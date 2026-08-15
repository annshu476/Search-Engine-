# Indexer Service

The **Indexer Service** is Service 3 in the Search Engine pipeline. Its primary role is to consume processed document content from Kafka (`search-document-topic`), index the documents into Elasticsearch using explicit mappings, and expose REST Search APIs for querying indexed content with field-weighted relevance scoring, phrase matching, advanced query syntax & operators, fuzzy typo tolerance, search result highlighting, search suggestions / autocomplete, advanced search filters, pagination, and sorting support.

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
SearchDocumentIndexer
    ↓
Elasticsearch (search-documents)
    ├── SearchService           → GET /api/search?q={query}&language={lang}&contentType={type}&statusCode={status}&fromDate={from}&toDate={to}&page={page}&size={size}&sort={sort}
    └── SearchSuggestionService → GET /api/search/suggest?q={prefix}
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
- Error Handling: Invalid syntax (unclosed quotes, standalone operators `+`/`-`, operator with whitespace `+ spring`, empty phrases `""`, or queries with no positive terms like `-xml`) returns HTTP 400 with `{"error": "Invalid search query syntax"}` via `SearchQuerySyntaxException`.
- Explicitly unsupported syntax: User field targeting (`title:foo`), wildcards (`*`), regex (`.`), user fuzzy operators (`foo~1`), range operators (`>100`).

> [!NOTE]
> **Intentionally NOT implemented yet**: Semantic/vector search, spell correction, synonyms, query history, or personalized recommendations.

---

## Package Responsibilities

```
service/indexer/src/main/java/com/searchengine/indexer/
├── IndexerApplication.java                      # Spring Boot main application entry point
├── config/
│   ├── ElasticsearchConfig.java                # Bean definitions for RestClient, ElasticsearchTransport, and ElasticsearchClient
│   ├── ElasticsearchProperties.java            # Connection configuration properties prefixed with 'elasticsearch'
│   ├── IndexerElasticsearchProperties.java     # Index configuration properties prefixed with 'indexer.elasticsearch'
│   └── SearchProperties.java                   # Search API configuration properties
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
│   └── SearchDocumentIndexer.java             # Indexes SearchDocument records into Elasticsearch using urlHash as _id
├── initializer/
│   └── SearchDocumentIndexInitializer.java     # Checks and creates search-documents index with explicit mapping on startup
├── mapper/
│   └── SearchDocumentMapper.java               # Maps SearchDocument records to Elasticsearch document Map
├── model/
│   ├── dto/
│   │   ├── SearchFilter.java                   # Filter parameters record DTO (language, contentType, statusCode, fromDate, toDate)
│   │   ├── SearchResponse.java                 # Search API response wrapper
│   │   ├── SearchResult.java                   # Individual search hit DTO
│   │   └── SearchSuggestionResponse.java       # Suggestion API response DTO
│   ├── kafka/
│   │   └── SearchDocument.java                 # Exact V1 SearchDocument record contract
│   └── search/
│       └── ParsedSearchQuery.java              # Advanced query syntax parser DTO (normalTerms, exactPhrases, requiredTerms, excludedTerms)
├── search/
│   ├── ElasticsearchSearchQueryBuilder.java   # Builds bool search queries combining multi-match, phrase boosts, operators, filters, highlighting
│   ├── ElasticsearchSuggestionQueryBuilder.java # Builds phrase_prefix multi-match queries for suggestions
│   └── SearchQueryParser.java                  # Parses raw query string into ParsedSearchQuery model
├── service/
│   ├── IndexerService.java                     # Domain service orchestrating document indexing
│   ├── SearchService.java                      # Service executing filtered search queries with advanced query syntax
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

### Search Endpoint with Filters and Advanced Query Syntax
```http
GET /api/search?q={query}&language={lang}&contentType={type}&statusCode={status}&fromDate={from}&toDate={to}&page={page}&size={size}&sort={sort}
```

#### Example Advanced Query Requests

1. **Exact Phrase Match**:
   ```http
   GET /api/search?q=%22spring%20boot%22
   ```

2. **Required Term**:
   ```http
   GET /api/search?q=spring%20%2Bjava
   ```

3. **Excluded Term**:
   ```http
   GET /api/search?q=spring%20-xml
   ```

4. **Combined Advanced Operators**:
   ```http
   GET /api/search?q=%22spring%20boot%22%20%2Bjava%20-xml
   ```

5. **Advanced Query with Filters, Pagination, and Sorting**:
   ```http
   GET /api/search?q=%22spring%20boot%22%20%2Bjava%20-xml&language=en&contentType=text/html&statusCode=200&page=0&size=10&sort=relevance
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
