# Indexer Service

The **Indexer Service** is Service 3 in the Search Engine pipeline. Its primary role is to consume processed document content from Kafka (`search-document-topic`), index the documents into Elasticsearch using explicit mappings, and expose REST Search APIs for querying indexed content with field-weighted relevance scoring, phrase matching, advanced query syntax & operators, production-hardened resilience, response optimization, in-memory caching, search analytics & query tracking, spell correction & synonym-aware search, search quality relevance testing, search analytics & query insights, fuzzy typo tolerance, search result highlighting, search suggestions / autocomplete, advanced search filters, pagination, and sorting support.

---

## Architecture Position

```
URL Frontier
    ↓
url-topic
    ↓
Crawler
    ↓
raw-html-topic
    ↓
Content Processor / Parser
    ↓
search-document-topic
    ↓
SearchDocumentConsumer
    ↓
IndexerService
    ↓
SearchDocumentIndexer (Invalidates Search Cache on Indexing Success)
    ↓
Elasticsearch (search-documents)
    ├── SearchService (Source Filtering + Caffeine Cache + SearchAnalyticsService + Spell Correction) → GET /api/search?q={query}&language={lang}&contentType={type}&statusCode={status}&fromDate={from}&toDate={to}&page={page}&size={size}&sort={sort}
    ├── SearchSuggestionService                                                                        → GET /api/search/suggest?q={prefix}
    ├── SearchEvaluationController                                                                     → POST /api/search/evaluation/run
    └── SearchAnalyticsController                                                                      → GET /api/search/analytics/summary
                                                                                                         GET /api/search/analytics/top-queries
                                                                                                         GET /api/search/analytics/zero-results
                                                                                                         POST /api/search/analytics/reset
```

---

## Features Implemented

### Feature 1 — Indexer Foundation
- Spring Boot 3.5.0 application foundation running on Java 21 on port `8083`.
- Official Elasticsearch Java API Client configuration (`co.elastic.clients:elasticsearch-java`).
- Externalized configuration via `application.yml` supporting environment variable overrides.
- Actuator health check integration (`/actuator/health`) with Elasticsearch connectivity health indicator (`ElasticsearchHealthIndicator`).
- Prometheus metrics foundation (`/actuator/prometheus`).

### Feature 2 — Kafka Consumer Foundation
- Kafka Consumer configuration targeting topic `search-document-topic` with consumer group `indexer`.
- Deserialization of incoming JSON payloads into `SearchDocument` with `spring.json.use.type.headers=false`.
- `SearchDocumentValidator` enforcing contract rules and HTTP status ranges (100–599).
- `SearchDocumentConsumer` listening on Kafka with manual immediate acknowledgement (`ACK`).
- Non-retryable validation error classification via `SearchDocumentValidationException`.
- Embedded Kafka integration test (`SearchDocumentConsumerIntegrationTest`).

### Feature 3 — Elasticsearch Index & Document Indexing
- Explicit Elasticsearch mapping for `search-documents` index (keyword for identities, text for searchable content, integer/date for metrics and timestamps).
- Document ID strategy using `SearchDocument.urlHash()` as `_id` for idempotent indexing.
- Startup index initialization via `SearchDocumentIndexInitializer`.
- Document mapping via `SearchDocumentMapper`.
- Elasticsearch document indexing via `SearchDocumentIndexer`.

### Feature 4 — Basic Search API
- Exposes `GET /api/search?q={query}` REST endpoint.
- Executes full-text multi-match queries across `title`, `metaDescription`, `headings`, and `bodyText`.
- Returns clean `SearchResponse` containing `query`, `totalHits`, and `List<SearchResult>` (excluding heavy `bodyText`).

### Feature 5 — Search API Pagination and Sorting
- Adds zero-based pagination via `page` (default 0, >= 0) and `size` (default 10, between 1 and 50).
- Calculates `totalPages` via `ceil(totalHits / size)`.
- Adds controlled sorting options:
  - `relevance` (default): Elasticsearch relevance score descending (`_score` desc).
  - `newest`: Sort by `indexedAt` descending with secondary deterministic `urlHash` (`_id`) ascending tie-breaker.

### Feature 6 — Search Relevance Improvements
- Replaces equal-weight multi-match query with a field-weighted strategy:
  - **`title^4.0`**: Highest importance (matches in title strongly boost document rank).
  - **`headings^3.0`**: High importance (matches in headings rank second).
  - **`metaDescription^2.0`**: Medium importance (matches in meta description rank third).
  - **`bodyText^1.0`**: Baseline importance (matches in body text).
- Configures `type = best_fields` (uses the score of the single best field for each document hit).
- Configures `minimum_should_match = "1"` to filter out documents with zero relevant field matches.
- Extracted query builder into `ElasticsearchSearchQueryBuilder`.
- Centralized relevance weights under `indexer.search.relevance.*` with fast-fail startup validation (`titleBoost > 0`, etc.).
- Normalizes search queries by trimming leading and trailing whitespace before execution while preserving internal spacing.

### Feature 7 — Fuzzy Search / Typo Tolerance
- Extends the multi-match query with configurable Elasticsearch `fuzziness` (default `AUTO`).
- Allows minor typos in user queries (e.g. `"sprng boot"` matches `"Spring Boot"`).
- Externalized configuration under `indexer.search.fuzzy.*`:
  - `enabled`: `true` (default).
  - `fuzziness`: `AUTO` (default).
- Fully compatible with Feature 6 field weighting (`title^4.0`, `headings^3.0`, `metaDescription^2.0`, `bodyText^1.0`) and sorting options.

### Feature 8 — Search Result Highlighting
- Adds Elasticsearch highlighting configuration to search requests for searchable fields (`title`, `headings`, `metaDescription`, `bodyText`).
- Encloses matched terms in configurable HTML tags (default `<em>` and `</em>`).
- Limits returned body text fragments to `fragment-size: 150` characters and `number-of-fragments: 2` to control payload size.
- Maps highlight fragments directly into `SearchResult.highlights` as `Map<String, List<String>>`.
- Returns `"highlights": {}` when no fragments are returned or when highlighting is disabled.
- Presentation metadata only: Highlighting does NOT alter Elasticsearch BM25 relevance scoring or result ordering.

### Feature 9 — Search Suggestions / Autocomplete
- Exposes `GET /api/search/suggest?q={prefix}` endpoint.
- Returns clean `SearchSuggestionResponse` DTO containing `query` and `suggestions` list (`List<String>`).
- Prefix queries search `title.suggest` and `headings.suggest` using Elasticsearch prefix queries.
- Bounded safety: Prefix length must be $\ge 2$ characters; results bounded to maximum 8 suggestions.

### Feature 10 — Advanced Search Filtering
- Adds search filters: `language`, `contentType`, `statusCode`, `fromDate`, `toDate`.
- Evaluates filters inside `bool.filter` non-scoring context.
- Fast-fail parameter validations on blank parameters, invalid status codes (100–599), or inverted date ranges (`fromDate > toDate`).

### Feature 11 — Phrase Matching & Advanced Relevance Weighting
- Introduces phrase matching boost (`phraseBoost = 2.0`) and title phrase boost (`titlePhraseBoost = 4.0`).
- Multi-term queries evaluate phrase matches alongside token multi-match queries to boost documents with consecutive term occurrences.

### Feature 12 — Advanced Search Query Syntax & Operators
- Adds controlled, safe query parsing via `SearchQueryParser`:
  - **Normal term**: `spring boot`
  - **Exact phrase**: `"spring boot"`
  - **Required term**: `+spring`
  - **Excluded term**: `-xml`
- Strict safety limits: Maximum 20 query terms, maximum 10 query phrases per request.

### Feature 13 — Search Production Hardening & Resilience
- Circuit breaker / query timeout controls (`indexer.search.timeout = 3s`).
- Deep pagination safety bounds (`indexer.search.max-page-depth = 10000`).
- Exception handling returning HTTP 400 Bad Request for validation errors and HTTP 500 for backend failures.

### Feature 14 — Search Performance, Caching & Response Optimization
- In-memory Caffeine caching (`SearchCacheService`) for identical search parameter requests.
- Cache invalidation on new document indexing (`SearchDocumentIndexer`).
- Source filtering (`_source.includes`) returning only required display fields.

### Feature 15 — Search Analytics & Query Tracking
- Thread-safe query statistics tracking (`SearchAnalyticsService`).
- Aggregates request count, successful/failed count, zero-result queries, and average latency.

### Feature 16 — Search Spell Correction & Synonym-Aware Search
- Query-time synonym expansion (`SearchQueryEnhancer`) for positive terms.
- Zero-result fallback spell correction (`SearchSpellCorrectionService`) using Elasticsearch Term Suggester API.

### Feature 17 — Search Quality, Ranking Evaluation & Relevance Testing
- Repeatable relevance quality measurement framework (`SearchEvaluationService`).
- Calculates Precision@K, Recall@K, Mean Reciprocal Rank (MRR), Hit@K, Zero-Result Rate, and Average Result Count.
- Exposes `POST /api/search/evaluation/run` internal endpoint.

### Feature 18 — Search Analytics & Query Insights
- Observability-only search analytics layer (`SearchAnalyticsService`) tracking search metrics, cache hit rates, zero-result rates, synonym expansions, spell corrections, filter usage, and advanced syntax usage.
- Strict Privacy & Security: Computes SHA-256 hashes (`queryHash`) for query aggregation. Query text storage is configurable (`normalized-query-storage: false` by default). Low-cardinality Micrometer metrics with no PII tags.
- Bounded Caffeine Storage: Queries bounded by `maximum-query-entries` (5,000) and `retention` (24h TTL).
- Failure Isolation: Analytics failure is caught and logged as `SEARCH_ANALYTICS_RECORDING_FAILED` without affecting search execution or response.
- REST Endpoints:
  - `GET /api/search/analytics/summary`
  - `GET /api/search/analytics/top-queries`
  - `GET /api/search/analytics/zero-results`

---

## Configuration Properties (`application.yml`)

```yaml
indexer:
  search:
    max-results: 10
    max-page-size: 50
    max-query-length: 200
    timeout: 3s
    max-page-depth: 10000
    max-query-terms: 20
    max-query-phrases: 10
    slow-query-threshold-ms: 1000
    cache:
      enabled: true
      maximum-size: 1000
      ttl: 60s
    analytics:
      enabled: true
      maximum-query-entries: 5000
      retention: 24h
      top-query-limit: 20
      normalized-query-storage: false
    synonyms:
      enabled: true
      maximum-synonyms-per-term: 5
      rules:
        - "java, jdk"
        - "js, javascript"
        - "spring boot, springboot"
    spell-correction:
      enabled: true
      maximum-suggestions: 3
      minimum-term-length: 3
    evaluation:
      enabled: true
      minimum-mrr: 0.80
      minimum-hit-at-1: 0.70
      minimum-hit-at-3: 0.90
      minimum-recall-at-10: 0.95
      maximum-zero-result-rate: 0.10
```

---

## API Documentation

### 1. Search Endpoint
```http
GET /api/search?q=spring%20boot&language=en&page=0&size=10&sort=relevance
```

### 2. Search Analytics Summary Endpoint
```http
GET /api/search/analytics/summary
```

### 3. Top Queries Endpoint
```http
GET /api/search/analytics/top-queries
```

### 4. Zero-Result Queries Endpoint
```http
GET /api/search/analytics/zero-results
```

### 5. Reset Analytics Endpoint
```http
POST /api/search/analytics/reset
```
*Response*: `HTTP 204 No Content`.

### 6. Relevance Evaluation Endpoint
```http
POST /api/search/evaluation/run
```

---

## Testing

Run the standard deterministic test suite across all services:
```powershell
# Crawler Service
cd service/crawler
.\mvnw.cmd clean test

# Content Processor Service
cd service/content-processor
.\mvnw.cmd clean test

# Indexer Service
cd service/indexer
.\mvnw.cmd clean test
```

Run real Elasticsearch integration tests:
```powershell
cd service/indexer
.\mvnw.cmd test -Pelasticsearch-integration
```
