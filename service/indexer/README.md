# Indexer Service

The **Indexer Service** is Service 3 in the Search Engine pipeline. Its primary role is to consume processed document content from Kafka (`search-document-topic`), index the documents into Elasticsearch using explicit mappings, and expose REST Search APIs for querying indexed content with field-weighted relevance scoring, phrase matching, advanced query syntax & operators, production-hardened resilience, response optimization, in-memory caching, search analytics & query tracking, fuzzy typo tolerance, search result highlighting, search suggestions / autocomplete, advanced search filters, pagination, and sorting support.

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
    ├── SearchService (Source Filtering + Caffeine Cache + SearchAnalyticsService) → GET /api/search?q={query}&language={lang}&contentType={type}&statusCode={status}&fromDate={from}&toDate={to}&page={page}&size={size}&sort={sort}
    ├── SearchSuggestionService                                                   → GET /api/search/suggest?q={prefix}
    └── SearchAnalyticsController                                                 → GET /api/search/analytics
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
- Exposes a dedicated `GET /api/search/suggest?q={prefix}` REST endpoint for prefix suggestions.
- Queries `search-documents` index using Elasticsearch `phrase_prefix` multi-match across `title^4.0`, `headings^3.0`, and `metaDescription^2.0` (excluding raw `bodyText`).
- Enforces a minimum prefix length of 2 characters (`min-prefix-length: 2`).
- Returns clean `SearchSuggestionResponse` DTO containing `query` and `suggestions` list (`List<String>`).
- Normalizes and deduplicates candidates deterministically while respecting `max-results: 8`.
- Suggestions endpoints are intentionally NOT cached and NOT recorded in search analytics.

### Feature 10 — Advanced Search Filters
- Extends `GET /api/search` with optional query filters:
  - **`language`**: Exact keyword filter on `language` field (max 20 chars, non-blank).
  - **`contentType`**: Exact keyword filter on `contentType` field (max 100 chars, non-blank).
  - **`statusCode`**: Exact numeric filter on `statusCode` field (range 100–599).
  - **`fromDate`**: Range filter `fetchedAt >= fromDate` (ISO-8601 timestamp).
  - **`toDate`**: Range filter `fetchedAt <= toDate` (ISO-8601 timestamp).
- Enforces validation: returns HTTP 400 if `fromDate > toDate` with `{"error": "fromDate must not be after toDate"}`.
- Wraps full-text search and filters in an Elasticsearch `bool` query filter context.

### Feature 11 — Advanced Query Relevance & Phrase Matching
- Ranks exact phrase matches and exact title phrase matches significantly higher than scattered token matches.
- Introduces `phraseBoost` (default `2.0`) and `titlePhraseBoost` (default `4.0`) configuration properties under `indexer.search.relevance.*`.
- Composes multi-word relevance queries with `should` clauses:
  1. Baseline weighted multi-match query (`best_fields`, `fuzziness = AUTO`).
  2. Multi-field phrase query (`title^8.0`, `headings^6.0`, `metaDescription^4.0`, `bodyText^2.0`).
  3. Title exact phrase boost (`match_phrase` on `title`, `boost = 4.0`).
- Ranking hierarchy: `Exact title phrase > Exact phrase in other fields > Token matches > Fuzzy matches`.

### Feature 12 — Advanced Search Query Syntax & Operators
- Adds custom application-level query parser `SearchQueryParser` producing `ParsedSearchQuery`.
- Supported syntax operators:
  - **Exact Phrase**: `"spring boot"` (matches as exact phrase).
  - **Required Term / Phrase**: `+java` or `+"spring boot"` (term/phrase MUST appear in document).
  - **Excluded Term / Phrase**: `-xml` or `-"spring boot"` (documents containing term/phrase are eliminated via `must_not` non-scoring clause).
  - **Combined Query**: `"spring boot" +java -xml` (matches exact phrase `"spring boot"` and required term `java`, excluding `xml`).
- Error Handling: Invalid syntax returns HTTP 400 with `{"error": "Invalid search query syntax"}` via `SearchQuerySyntaxException`.

### Feature 13 — Search Production Hardening
- **Search Request Timeout**: Configurable search-level timeout (`timeout: 3s`). Returns `HTTP 503 Service Unavailable` on timeout.
- **Deep Pagination Protection**: Enforces a configurable maximum offset limit (`max-page-depth: 10000`). Returns `HTTP 400 Bad Request` if `page * size > maxPageDepth` or integer overflow occurs.
- **Query Complexity Limits**: Enforces query term limits (`max-query-terms: 20`) and phrase limits (`max-query-phrases: 10`).
- **Observability**: Tracks search metrics (`search.requests`, `search.success`, `search.errors`, `search.validation.errors`, `search.timeouts`, `search.duration`).

### Feature 14 — Search Performance, Caching & Response Optimization
- **Elasticsearch Source Filtering**: Configures Elasticsearch search requests to return only fields required by `SearchResult`: `url`, `canonicalUrl`, `urlHash`, `title`, `metaDescription`, `language`, `wordCount`, `statusCode`. Large unused fields (`bodyText`, `headings`, `contentType`, `fetchedAt`, `indexedAt`) are excluded from payload transfer while highlighting for `title`, `headings`, `metaDescription`, and `bodyText` remains fully functional.
- **Application-Level Search Cache**: Bounded in-memory Caffeine cache (`SearchCacheService`) storing successful `GET /api/search` responses. Configured under `indexer.search.cache` (`enabled: true`, `maximum-size: 1000`, `ttl: 60s`).
- **Deterministic Cache Key**: Immutable `SearchCacheKey` incorporating trimmed query string, `page`, `size`, `sort`, all search filters (`language`, `contentType`, `statusCode`, `fromDate`, `toDate`), and configuration version namespace to prevent stale responses across config changes.
- **Cache Invalidation on Indexing**: Automatically clears/invalidates cached search responses when `SearchDocumentIndexer.index(document)` succeeds. Indexing failures leave cache untouched.

### Feature 15 — Search Analytics & Query Tracking
- **Privacy-Safe Search Analytics Layer**: `SearchAnalyticsService` tracks search request volume, successes, failures, validation errors, zero-result searches, resultful searches, execution latency (total/max), cache hits/misses, and query frequency statistics without storing IP addresses, user IDs, cookies, session IDs, or full result URLs.
- **Bounded In-Memory Query Tracking**: Utilizes Caffeine cache for query frequency tracking (`maximum-query-entries: 5000`, `query-retention: 1h`). Query evictions do NOT reduce global counters (`totalRequests`, `successfulRequests`, etc.).
- **Internal REST Analytics Endpoints**:
  - `GET /api/search/analytics`: Returns aggregate snapshot containing `totalRequests`, `successfulRequests`, `failedRequests`, `validationErrors`, `zeroResultSearches`, `resultfulSearches`, `cacheHits`, `cacheMisses`, `averageLatencyMs`, `maxLatencyMs`, `trackedQueries`, and `topQueries` list (bounded by `top-query-limit: 20`).
  - `GET /api/search/analytics/zero-results`: Returns most frequent zero-result queries sorted by zero-result count descending.
  - `POST /api/search/analytics/reset`: Clears in-memory analytics counters and query frequency statistics without calling Elasticsearch or modifying search cache/indexes. Returns HTTP 204 No Content.
- **Micrometer Observability**: Emits Micrometer metrics (`search.analytics.requests`, `search.analytics.success`, `search.analytics.errors`, `search.analytics.validation_errors`, `search.analytics.zero_results`, `search.analytics.resultful`, `search.analytics.duration`) with safe low-cardinality label `operation="search"`.

> [!WARNING]
> **Operational Security Note**: The analytics endpoints (`GET /api/search/analytics`, `GET /api/search/analytics/zero-results`, `POST /api/search/analytics/reset`) expose operational search metrics and reset capabilities. In production deployments, these endpoints MUST be protected behind authentication/authorization or restricted to an internal network boundary.

> [!NOTE]
> **In-Memory Volatility**: Analytics statistics are held purely in memory and reset upon service restart.

---

## Package Responsibilities

```
service/indexer/src/main/java/com/searchengine/indexer/
├── IndexerApplication.java                      # Spring Boot main application entry point
├── analytics/
│   ├── SearchAnalyticsController.java          # REST controller for /api/search/analytics endpoints
│   └── SearchAnalyticsService.java             # In-memory bounded search analytics manager
├── config/
│   ├── ElasticsearchConfig.java                # Bean definitions for RestClient, ElasticsearchTransport, and ElasticsearchClient
│   ├── ElasticsearchProperties.java            # Connection configuration properties prefixed with 'elasticsearch'
│   ├── IndexerElasticsearchProperties.java     # Index configuration properties prefixed with 'indexer.elasticsearch'
│   └── SearchProperties.java                   # Search API configuration properties (cache, hardening, analytics)
├── consumer/
│   └── SearchDocumentConsumer.java             # Kafka listener for search-document-topic using manual ACK
├── controller/
│   └── SearchController.java                   # REST controller exposing GET /api/search and GET /api/search/suggest
├── exception/
│   ├── ElasticsearchConfigurationException.java # Custom exception for invalid configuration/URLs
│   ├── ElasticsearchIndexingException.java     # Custom exception for Elasticsearch indexing failures
│   ├── SearchDocumentValidationException.java  # Exception thrown on SearchDocument contract validation failure
│   ├── SearchQueryException.java               # Custom exception for search query execution failures
│   ├── SearchQuerySyntaxException.java         # Exception thrown on malformed advanced search query syntax
│   └── SearchSuggestionException.java         # Custom exception for search suggestion execution failures
├── health/
│   └── ElasticsearchHealthIndicator.java        # Custom Spring Boot Actuator HealthIndicator for Elasticsearch ping
├── indexer/
│   └── SearchDocumentIndexer.java             # Indexes SearchDocument records into Elasticsearch and invalidates search cache
├── initializer/
│   └── SearchDocumentIndexInitializer.java     # Checks and creates search-documents index with explicit mapping on startup
├── mapper/
│   └── SearchDocumentMapper.java               # Maps SearchDocument records to Elasticsearch document Map
├── model/
│   ├── analytics/
│   │   ├── QueryStats.java                     # Query statistics DTO record
│   │   ├── SearchAnalyticsSnapshot.java        # Analytics snapshot DTO record
│   │   └── ZeroResultsResponse.java            # Zero-results response DTO record
│   ├── dto/
│   │   ├── SearchFilter.java                   # Filter parameters record DTO
│   │   ├── SearchResponse.java                 # Search API response wrapper
│   │   ├── SearchResult.java                   # Individual search hit DTO
│   │   └── SearchSuggestionResponse.java       # Suggestion API response DTO
│   ├── kafka/
│   │   └── SearchDocument.java                 # Exact V1 SearchDocument record contract
│   └── search/
│       ├── ParsedSearchQuery.java              # Advanced query syntax parser DTO
│       └── SearchCacheKey.java                 # Deterministic search cache key record
├── search/
│   ├── ElasticsearchSearchQueryBuilder.java   # Builds bool search queries combining multi-match, phrase boosts, operators, filters, highlighting
│   ├── ElasticsearchSuggestionQueryBuilder.java # Builds phrase_prefix multi-match queries for suggestions
│   └── SearchQueryParser.java                  # Parses raw query string into ParsedSearchQuery model
├── service/
│   ├── IndexerService.java                     # Domain service orchestrating document indexing
│   ├── SearchCacheService.java                 # In-memory Caffeine search cache manager with metrics and logging
│   ├── SearchService.java                      # Service executing source-filtered, cached, analytics-tracked search queries
│   └── SearchSuggestionService.java            # Service executing prefix suggestion queries
└── validator/
    └── SearchDocumentValidator.java            # Validates SearchDocument required fields and bounds
```

---

## Server Port & Configuration

Default Port: `8083`

### Configuration Properties

| Property Key | Default Value | Environment Variable | Description |
|---|---|---|---|
| `server.port` | `8083` | `SERVER_PORT` | Service HTTP port |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | `KAFKA_BOOTSTRAP_SERVERS` | Kafka cluster bootstrap servers |
| `spring.kafka.consumer.group-id` | `indexer` | `KAFKA_CONSUMER_GROUP` | Consumer group ID |
| `indexer.kafka.search-document-topic` | `search-document-topic` | `SEARCH_DOCUMENT_TOPIC` | Topic name for incoming search documents |
| `indexer.elasticsearch.index-name` | `search-documents` | `ELASTICSEARCH_INDEX_NAME` | Elasticsearch index name |
| `indexer.search.max-results` | `10` | `SEARCH_MAX_RESULTS` | Default number of search results returned |
| `indexer.search.max-page-size` | `50` | `SEARCH_MAX_PAGE_SIZE` | Maximum allowed page size |
| `indexer.search.max-query-length` | `200` | `SEARCH_MAX_QUERY_LENGTH` | Maximum allowed characters in search query |
| `indexer.search.timeout` | `3s` | `SEARCH_TIMEOUT` | Elasticsearch search request execution timeout |
| `indexer.search.max-page-depth` | `10000` | `SEARCH_MAX_PAGE_DEPTH` | Maximum allowed Elasticsearch result offset (`page * size`) |
| `indexer.search.max-query-terms` | `20` | `SEARCH_MAX_QUERY_TERMS` | Maximum allowed meaningful terms per query |
| `indexer.search.max-query-phrases` | `10` | `SEARCH_MAX_QUERY_PHRASES` | Maximum allowed exact phrases per query |
| `indexer.search.slow-query-threshold-ms` | `1000` | `SEARCH_SLOW_QUERY_THRESHOLD_MS` | Threshold in ms to log slow query warnings |
| `indexer.search.cache.enabled` | `true` | `SEARCH_CACHE_ENABLED` | Enables in-memory Caffeine search response caching |
| `indexer.search.cache.maximum-size` | `1000` | `SEARCH_CACHE_MAXIMUM_SIZE` | Maximum allowed entries in Caffeine cache |
| `indexer.search.cache.ttl` | `60s` | `SEARCH_CACHE_TTL` | Cache entry time-to-live after write |
| `indexer.search.analytics.enabled` | `true` | `SEARCH_ANALYTICS_ENABLED` | Enables in-memory search analytics collection |
| `indexer.search.analytics.maximum-query-entries` | `5000` | `SEARCH_ANALYTICS_MAXIMUM_QUERY_ENTRIES` | Maximum tracked query entries in analytics cache |
| `indexer.search.analytics.top-query-limit` | `20` | `SEARCH_ANALYTICS_TOP_QUERY_LIMIT` | Maximum top queries returned in analytics snapshot |
| `indexer.search.analytics.query-retention` | `1h` | `SEARCH_ANALYTICS_QUERY_RETENTION` | Time-to-live retention for tracked query stats |
| `indexer.search.relevance.title-boost` | `4.0` | `SEARCH_TITLE_BOOST` | Field relevance boost for document `title` |
| `indexer.search.relevance.headings-boost` | `3.0` | `SEARCH_HEADINGS_BOOST` | Field relevance boost for document `headings` |
| `indexer.search.relevance.meta-description-boost` | `2.0` | `SEARCH_META_DESCRIPTION_BOOST` | Field relevance boost for `metaDescription` |
| `indexer.search.relevance.body-boost` | `1.0` | `SEARCH_BODY_BOOST` | Field relevance boost for document `bodyText` |
| `indexer.search.relevance.phrase-boost` | `2.0` | `SEARCH_RELEVANCE_PHRASE_BOOST` | Multi-field phrase relevance boost multiplier |
| `indexer.search.relevance.title-phrase-boost` | `4.0` | `SEARCH_RELEVANCE_TITLE_PHRASE_BOOST` | Exact title phrase match boost multiplier |
| `indexer.search.fuzzy.enabled` | `true` | `SEARCH_FUZZY_ENABLED` | Enables fuzzy matching for typo tolerance |
| `indexer.search.fuzzy.fuzziness` | `AUTO` | `SEARCH_FUZZINESS` | Elasticsearch fuzziness setting |
| `indexer.search.highlight.enabled` | `true` | `SEARCH_HIGHLIGHT_ENABLED` | Enables search result highlighting |
| `indexer.search.suggestions.enabled` | `true` | `SEARCH_SUGGESTIONS_ENABLED` | Enables search suggestions / autocomplete |
| `elasticsearch.url` | `http://localhost:9200` | `ELASTICSEARCH_URL` | Target Elasticsearch endpoint URL |

---

## API Specification

### 1. Search Endpoint
```http
GET /api/search?q={query}&language={lang}&contentType={type}&statusCode={status}&fromDate={from}&toDate={to}&page={page}&size={size}&sort={sort}
```

### 2. Search Analytics Endpoint
```http
GET /api/search/analytics
```

#### Example Response (HTTP 200 OK):
```json
{
  "totalRequests": 1200,
  "successfulRequests": 1160,
  "failedRequests": 20,
  "validationErrors": 20,
  "zeroResultSearches": 145,
  "resultfulSearches": 1015,
  "cacheHits": 430,
  "cacheMisses": 730,
  "averageLatencyMs": 42.8,
  "maxLatencyMs": 820,
  "trackedQueries": 312,
  "topQueries": [
    {
      "query": "spring boot",
      "count": 82,
      "zeroResultCount": 2,
      "totalHits": 1240,
      "lastSeenAt": "2026-08-15T21:00:00Z"
    }
  ]
}
```

### 3. Zero-Result Queries Endpoint
```http
GET /api/search/analytics/zero-results
```

#### Example Response (HTTP 200 OK):
```json
{
  "queries": [
    {
      "query": "sprng boot",
      "count": 12
    },
    {
      "query": "java microservice",
      "count": 8
    }
  ]
}
```

### 4. Reset Analytics Endpoint
```http
POST /api/search/analytics/reset
```
*Response*: `HTTP 204 No Content` (clears in-memory search analytics state).

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
