# Indexer Service

The **Indexer Service** is Service 3 in the Search Engine pipeline. Its primary role is to consume processed document content from Kafka (`search-document-topic`), index the documents into Elasticsearch using explicit mappings, and expose a REST Search API for querying indexed content.

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
SearchController (GET /api/search?q={query})
```

---

## Features Implemented

### Feature 1 — Indexer Foundation
- Spring Boot 3.5.0 application foundation running on Java 21 on port `8083`.
- Official Elasticsearch Java API Client configuration (`co.elastic.clients:elasticsearch-java`).
- Externalized configuration via `application.yml` supporting environment variable overrides.
- Actuator health check integration (`/actuator/health`) with Elasticsearch connectivity health indicator (`ElasticsearchHealthIndicator`).
- Prometheus metrics foundation (`/actuator/prometheus`).
- Clean package structure adhering to single-responsibility naming conventions.

### Feature 2 — Kafka Consumer Foundation
- Kafka Consumer configuration targeting topic `search-document-topic` with consumer group `indexer`.
- Deserialization of incoming JSON payloads into `com.searchengine.indexer.model.kafka.SearchDocument` with `spring.json.use.type.headers=false`.
- `SearchDocumentValidator` enforcing contract rules and HTTP status ranges (100–599).
- `SearchDocumentConsumer` listening on Kafka with manual immediate acknowledgement (`ACK`).
- Non-retryable validation error classification via `SearchDocumentValidationException` (unacknowledged and logged without processing).
- Embedded Kafka integration test (`SearchDocumentConsumerIntegrationTest`).

### Feature 3 — Elasticsearch Index & Document Indexing
- Explicit Elasticsearch mapping for `search-documents` index (keyword for identities, text for searchable content, integer/date for metrics and timestamps).
- Document ID strategy using `SearchDocument.urlHash()` as `_id` for idempotent indexing.
- Startup index initialization via `SearchDocumentIndexInitializer`.
- Document mapping via `SearchDocumentMapper`.
- Elasticsearch document indexing via `SearchDocumentIndexer`.
- At-least-once Kafka processing semantics: Kafka messages are acknowledged **only after** Elasticsearch indexing succeeds.

### Feature 4 — Basic Search API
- Exposes `GET /api/search?q={query}` REST endpoint.
- Executes full-text multi-match queries across `title`, `metaDescription`, `headings`, and `bodyText`.
- Returns top matching results (at most 10 results, configured via `indexer.search.max-results=10`).
- Validates query length (`indexer.search.max-query-length=200`) and rejects null/blank queries with HTTP 400.
- Handles Elasticsearch failures gracefully with HTTP 503 without exposing internal details.
- Returns clean `SearchResponse` containing `query`, `totalHits`, and `List<SearchResult>` (excluding heavy `bodyText`).

> [!NOTE]
> **Intentionally NOT implemented yet in Feature 4**: Advanced ranking, fuzzy search, autocomplete, BM25 tuning, highlighting, pagination, or sorting customization.

---

## Package Responsibilities

```
service/indexer/src/main/java/com/searchengine/indexer/
├── IndexerApplication.java                      # Spring Boot main application entry point
├── config/
│   ├── ElasticsearchConfig.java                # Bean definitions for RestClient, ElasticsearchTransport, and ElasticsearchClient
│   ├── ElasticsearchProperties.java            # Connection configuration properties prefixed with 'elasticsearch'
│   ├── IndexerElasticsearchProperties.java     # Index configuration properties prefixed with 'indexer.elasticsearch'
│   └── SearchProperties.java                   # Search API configuration properties prefixed with 'indexer.search'
├── consumer/
│   └── SearchDocumentConsumer.java             # Kafka listener for search-document-topic using manual ACK
├── controller/
│   └── SearchController.java                   # REST controller exposing GET /api/search?q={query}
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
│   │   ├── SearchResponse.java                 # Search API response wrapper (query, totalHits, results)
│   │   └── SearchResult.java                   # Individual search hit DTO (excludes bodyText)
│   └── kafka/
│       └── SearchDocument.java                 # Exact V1 SearchDocument record contract
├── service/
│   ├── IndexerService.java                     # Domain service orchestrating document indexing
│   └── SearchService.java                      # Service executing multi-match queries against Elasticsearch
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
| `indexer.search.max-results` | `10` | `SEARCH_MAX_RESULTS` | Maximum number of search results returned |
| `indexer.search.max-query-length` | `200` | `SEARCH_MAX_QUERY_LENGTH` | Maximum allowed characters in search query |
| `elasticsearch.url` | `http://localhost:9200` | `ELASTICSEARCH_URL` | Target Elasticsearch endpoint URL |

---

## Search API Specification

### Request Endpoint
```http
GET /api/search?q={query}
```

### Search Fields
The query executes a multi-match search over:
- `title`
- `metaDescription`
- `headings`
- `bodyText`

### Query Constraints
- **Required**: `q` must not be null, blank, or whitespace-only.
- **Max Length**: `q` length must be <= 200 characters.

### Response Formats

#### 1. Valid Query Response (HTTP 200 OK)
```json
{
  "query": "spring",
  "totalHits": 1,
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

#### 2. Zero Results Response (HTTP 200 OK)
```json
{
  "query": "somethingthatdoesnotexist",
  "totalHits": 0,
  "results": []
}
```

#### 3. Validation Error (HTTP 400 Bad Request)
```json
{
  "error": "Search query must not be blank"
}
```

#### 4. Oversized Query Error (HTTP 400 Bad Request)
```json
{
  "error": "Search query exceeds maximum allowed length"
}
```

#### 5. Search Service Unavailable Error (HTTP 503 Service Unavailable)
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

### Test Coverage Summary

- **Context & Actuator Tests** (`IndexerApplicationTests`): Validates Spring context boot, `/actuator/health`, and `/actuator/prometheus`.
- **Kafka Consumer Tests** (`SearchDocumentConsumerTest`, `SearchDocumentConsumerIntegrationTest`): Validates manual ACK flow, payload validation, and `@EmbeddedKafka` integration.
- **Indexing & Initializer Tests** (`SearchDocumentIndexerTest`, `SearchDocumentIndexInitializerTest`, `SearchDocumentMapperTest`): Validates index creation, field mapping, `_id` idempotency, and indexing exception handling.
- **Search Service & Controller Tests** (`SearchServiceTest`, `SearchControllerTest`): Validates search query execution, zero-hit handling, max result limit, input validation, HTTP 400 responses, and HTTP 503 exception mapping.

---

## Planned Next Features

- **Feature 5**: Advanced Ranking, Highlighting, Pagination & Filtering.
- **Feature 6**: Kafka Retry Policy & Dead Letter Topic (DLT).
- **Feature 7**: End-to-End Pipeline Integration Verification.
