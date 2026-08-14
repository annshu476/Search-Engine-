# Indexer Service

The **Indexer Service** is Service 3 in the Search Engine pipeline. Its primary role is to consume processed document content from Kafka (`search-document-topic`), index the documents into Elasticsearch using explicit mappings, and expose a REST Search API for querying indexed content with pagination and sorting support.

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
- Deserialization of incoming JSON payloads into `com.searchengine.indexer.model.kafka.SearchDocument` with `spring.json.use.type.headers=false`.
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
- Calculates `totalPages` via `ceil(totalHits / size)` (e.g., 0 hits -> 0 pages, 11 hits / size 10 -> 2 pages).
- Adds controlled sorting options:
  - `relevance` (default): Elasticsearch relevance score descending (`_score` desc).
  - `newest`: Sort by `indexedAt` descending with secondary deterministic `urlHash` (`_id`) ascending tie-breaker.
- Enforces strict input validation returning HTTP 400 for invalid page numbers, invalid sizes, or unsupported sort fields.

> [!NOTE]
> **Intentionally NOT implemented yet**: Arbitrary client-controlled sort fields, fuzzy search, autocomplete, highlighting, advanced ranking, or search suggestions.

---

## Package Responsibilities

```
service/indexer/src/main/java/com/searchengine/indexer/
├── IndexerApplication.java                      # Spring Boot main application entry point
├── config/
│   ├── ElasticsearchConfig.java                # Bean definitions for RestClient, ElasticsearchTransport, and ElasticsearchClient
│   ├── ElasticsearchProperties.java            # Connection configuration properties prefixed with 'elasticsearch'
│   ├── IndexerElasticsearchProperties.java     # Index configuration properties prefixed with 'indexer.elasticsearch'
│   └── SearchProperties.java                   # Search API configuration properties (max-results, max-page-size, max-query-length)
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
├── service/
│   ├── IndexerService.java                     # Domain service orchestrating document indexing
│   └── SearchService.java                      # Service executing multi-match queries with pagination and sorting against Elasticsearch
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
| `q` | `String` | *(Required)* | Non-blank, <= 200 chars | Search query string across title, metaDescription, headings, bodyText |
| `page` | `Integer` | `0` | >= 0 | Zero-based page index |
| `size` | `Integer` | `10` | 1 to 50 | Page size |
| `sort` | `String` | `relevance` | `relevance` or `newest` | Ordering strategy |

### Supported Sort Options
- **`relevance`**: Orders results by Elasticsearch relevance score descending.
- **`newest`**: Orders results by `indexedAt` descending with secondary deterministic `urlHash` (`_id`) ascending tie-breaker.
- *Note*: Arbitrary client-controlled field sorting is explicitly forbidden and returns HTTP 400.

---

## Response Formats & Error Codes

### 1. Valid Query Response (HTTP 200 OK)
```json
{
  "query": "spring",
  "totalHits": 125,
  "page": 0,
  "size": 10,
  "totalPages": 13,
  "sort": "relevance",
  "results": [
    {
      "url": "https://spring.io",
      "canonicalUrl": "https://spring.io",
      "urlHash": "abc123hash",
      "title": "Spring Framework",
      "metaDescription": "Spring makes Java development easier",
      "language": "en",
      "wordCount": 450,
      "statusCode": 200
    }
  ]
}
```

### 2. Zero Results Response (HTTP 200 OK)
```json
{
  "query": "somethingthatdoesnotexist",
  "totalHits": 0,
  "page": 0,
  "size": 10,
  "totalPages": 0,
  "sort": "relevance",
  "results": []
}
```

### 3. Page Validation Error (HTTP 400 Bad Request)
```json
{
  "error": "Page must be greater than or equal to 0"
}
```

### 4. Size Validation Error (HTTP 400 Bad Request)
```json
{
  "error": "Page size must be between 1 and 50"
}
```

### 5. Unsupported Sort Error (HTTP 400 Bad Request)
```json
{
  "error": "Unsupported sort option: oldest"
}
```

### 6. Search Service Unavailable Error (HTTP 503 Service Unavailable)
```json
{
  "error": "Search service temporarily unavailable"
}
```

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

- **Feature 6**: Kafka Retry Policy & Dead Letter Topic (DLT).
- **Feature 7**: Highlighting, Fuzzy Search & Autocomplete.
- **Feature 8**: End-to-End Search Pipeline Verification.
