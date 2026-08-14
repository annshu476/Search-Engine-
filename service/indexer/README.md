# Indexer Service

The **Indexer Service** is Service 3 in the Search Engine pipeline. Its primary role is to consume processed document content from Kafka (`search-document-topic`), index the documents into Elasticsearch using explicit mappings, and expose a REST Search API for querying indexed content with field-weighted relevance scoring, pagination, and sorting support.

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
    ↓
SearchService
    ↓
SearchController (GET /api/search?q={query}&page={page}&size={size}&sort={sort})
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

> [!NOTE]
> **Intentionally NOT implemented yet**: Fuzzy matching, autocomplete, spell correction, synonyms, highlighting, query suggestions, or advanced learning-to-rank.

---

## Package Responsibilities

```
service/indexer/src/main/java/com/searchengine/indexer/
├── IndexerApplication.java                      # Spring Boot main application entry point
├── config/
│   ├── ElasticsearchConfig.java                # Bean definitions for RestClient, ElasticsearchTransport, and ElasticsearchClient
│   ├── ElasticsearchProperties.java            # Connection configuration properties prefixed with 'elasticsearch'
│   ├── IndexerElasticsearchProperties.java     # Index configuration properties prefixed with 'indexer.elasticsearch'
│   └── SearchProperties.java                   # Search API configuration properties (max-results, max-page-size, max-query-length, relevance boosts)
├── consumer/
│   └── SearchDocumentConsumer.java             # Kafka listener for search-document-topic using manual ACK
├── controller/
│   └── SearchController.java                   # REST controller exposing GET /api/search?q={query}&page={page}&size={size}&sort={sort}
├── exception/
│   ├── ElasticsearchConfigurationException.java # Custom exception for invalid configuration/URLs
│   ├── ElasticsearchIndexingException.java     # Custom exception for Elasticsearch indexing failures
│   ├── SearchDocumentValidationException.java  # Exception thrown on SearchDocument contract validation failure
│   └── SearchQueryException.java               # Custom exception for search query execution failures
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
│   │   ├── SearchResponse.java                 # Search API response wrapper (query, totalHits, page, size, totalPages, sort, results)
│   │   └── SearchResult.java                   # Individual search hit DTO (excludes bodyText)
│   └── kafka/
│       └── SearchDocument.java                 # Exact V1 SearchDocument record contract
├── search/
│   └── ElasticsearchSearchQueryBuilder.java   # Builds weighted best_fields multi-match queries with minimum_should_match=1
├── service/
│   ├── IndexerService.java                     # Domain service orchestrating document indexing
│   └── SearchService.java                      # Service executing multi-match queries with field weighting, pagination, and sorting
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
| `elasticsearch.url` | `http://localhost:9200` | `ELASTICSEARCH_URL` | Target Elasticsearch endpoint URL |

---

## Search API Specification

### Request Endpoint
```http
GET /api/search?q={query}&page={page}&size={size}&sort={sort}
```

### Request Parameters

| Parameter | Type | Default | Constraints | Description |
|---|---|---|---|---|
| `q` | `String` | *(Required)* | Non-blank, <= 200 chars | Search query string (automatically trimmed) |
| `page` | `Integer` | `0` | >= 0 | Zero-based page index |
| `size` | `Integer` | `10` | 1 to 50 | Page size |
| `sort` | `String` | `relevance` | `relevance` or `newest` | Ordering strategy |

---

## Field Weighting & Relevance Ranking

When `sort=relevance` (default) is requested:
1. `title` matches receive a `4.0` score multiplier.
2. `headings` matches receive a `3.0` score multiplier.
3. `metaDescription` matches receive a `2.0` score multiplier.
4. `bodyText` matches receive a `1.0` baseline score multiplier.
5. Query is executed using `best_fields` multi-match with `minimum_should_match = "1"`.
6. Documents are ordered by Elasticsearch `_score` descending.

---

## Testing

Run the standard deterministic test suite:
```powershell
.\mvnw.cmd clean test
```

Run real Elasticsearch integration tests:
```powershell
.\mvnw.cmd test -Pelasticsearch-integration
```

---

## Planned Next Features

- **Feature 7**: Kafka Retry Policy & Dead Letter Topic (DLT).
- **Feature 8**: Highlighting, Fuzzy Search & Autocomplete.
- **Feature 9**: End-to-End Search Pipeline Verification.
