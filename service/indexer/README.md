# Indexer Service

The **Indexer Service** is Service 3 in the Search Engine pipeline. Its primary role is to consume processed document content from Kafka (`search-document-topic`) and index the documents into Elasticsearch to enable full-text search capability.

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
Indexer (WE ARE HERE)
    ↓
Elasticsearch
```

---

## Features Implemented

### Feature 1 — Indexer Foundation
- Spring Boot 3.5.0 application foundation running on Java 21.
- Official Elasticsearch Java API Client configuration (`co.elastic.clients:elasticsearch-java`).
- Externalized configuration via `application.yml` supporting environment variable overrides.
- Actuator health check integration (`/actuator/health`) with Elasticsearch connectivity health indicator (`ElasticsearchHealthIndicator`).
- Prometheus metrics foundation (`/actuator/prometheus`).
- Clean package structure adhering to single-responsibility naming conventions.
- Fully deterministic unit and Spring context tests requiring no live Elasticsearch instance or Docker container.

### Feature 2 — Kafka Consumer Foundation
- Kafka Consumer configuration targeting topic `search-document-topic` with consumer group `indexer`.
- Deserialization of incoming JSON payloads into `com.searchengine.indexer.model.kafka.SearchDocument` with `spring.json.use.type.headers=false`.
- `SearchDocumentValidator` enforcing contract rules and HTTP status ranges (100–599).
- `SearchDocumentConsumer` listening on Kafka with manual immediate acknowledgement (`ACK`).
- Acknowledges only after successful domain processing by `IndexerService`.
- Non-retryable validation error classification via `SearchDocumentValidationException` (unacknowledged and logged without processing).
- Structured logging adhering to `SEARCH_DOCUMENT_RECEIVED`, `SEARCH_DOCUMENT_PROCESSED`, and `SEARCH_DOCUMENT_INVALID` formats (never logging large HTML/bodyText content).
- Full suite of unit tests and an `@EmbeddedKafka` integration test (`SearchDocumentConsumerIntegrationTest`).

> [!IMPORTANT]
> **Elasticsearch indexing is intentionally NOT implemented in Feature 2.** The service delegates validated records to `IndexerService` domain boundary. Actual Elasticsearch document indexing will be added in Feature 3.

---

## Package Responsibilities

```
service/indexer/src/main/java/com/searchengine/indexer/
├── IndexerApplication.java                      # Spring Boot main application entry point
├── config/
│   ├── ElasticsearchConfig.java                # Bean definitions for RestClient, ElasticsearchTransport, and ElasticsearchClient
│   └── ElasticsearchProperties.java            # Externalized configuration properties prefixed with 'elasticsearch'
├── consumer/
│   └── SearchDocumentConsumer.java             # Kafka listener for search-document-topic using manual ACK
├── exception/
│   ├── ElasticsearchConfigurationException.java # Custom exception for invalid configuration/URLs
│   └── SearchDocumentValidationException.java  # Exception thrown on SearchDocument contract validation failure
├── health/
│   └── ElasticsearchHealthIndicator.java        # Custom Spring Boot Actuator HealthIndicator for Elasticsearch ping
├── model/
│   └── kafka/
│       └── SearchDocument.java                 # Exact V1 SearchDocument record contract
├── service/
│   └── IndexerService.java                     # Domain service boundary receiving validated documents
└── validator/
    └── SearchDocumentValidator.java            # Validates SearchDocument required fields and bounds
```

---

## Server Port & Kafka Settings

Default Port: `8083`

- **8080**: URL Frontier
- **8081**: Crawler
- **8082**: Content Processor
- **8083**: Indexer

### Configuration & Environment Variables

| Property Key | Default Value | Environment Variable | Description |
|---|---|---|---|
| `server.port` | `8083` | `SERVER_PORT` | Service HTTP port |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | `KAFKA_BOOTSTRAP_SERVERS` | Kafka cluster bootstrap servers |
| `spring.kafka.consumer.group-id` | `indexer` | `KAFKA_CONSUMER_GROUP` | Consumer group ID |
| `indexer.kafka.search-document-topic` | `search-document-topic` | `SEARCH_DOCUMENT_TOPIC` | Topic name for incoming search documents |
| `elasticsearch.url` | `http://localhost:9200` | `ELASTICSEARCH_URL` | Target Elasticsearch endpoint URL |
| `elasticsearch.username` | `""` | `ELASTICSEARCH_USERNAME` | Elasticsearch basic auth username (optional) |
| `elasticsearch.password` | `""` | `ELASTICSEARCH_PASSWORD` | Elasticsearch basic auth password (optional) |

---

## SearchDocument Contract & Validation

```java
public record SearchDocument(
    String url,
    String canonicalUrl,
    String urlHash,
    String title,
    String metaDescription,
    List<String> headings,
    String bodyText,
    String language,
    Integer wordCount,
    Integer statusCode,
    String contentType,
    Instant fetchedAt,
    Instant indexedAt
) {}
```

### Validation Rules

- **Required Fields**: `url` (non-blank), `canonicalUrl` (non-blank), `urlHash` (non-blank), `headings` (non-null), `bodyText` (non-null), `wordCount` (non-null, >= 0), `statusCode` (non-null, 100–599), `contentType` (non-blank), `fetchedAt` (non-null), `indexedAt` (non-null).
- **Optional Fields**: `title`, `metaDescription`, `language` (may be null).

---

## Manual Acknowledgement & Error Handling

- **Manual ACK Mode**: `spring.kafka.listener.ack-mode=manual_immediate`
- **Successful Flow**: Kafka record received → Deserialized → Validated → `IndexerService.process(doc)` succeeds → `ack.acknowledge()`.
- **Validation Failure**: Throws `SearchDocumentValidationException`, logs `SEARCH_DOCUMENT_INVALID`, does **NOT** invoke `IndexerService`, does **NOT** call `ack.acknowledge()`.
- **Processing Failure**: Uncaught exception from `IndexerService` logs `SEARCH_DOCUMENT_PROCESSING_FAILED`, does **NOT** call `ack.acknowledge()`, and propagates exception to Kafka container handler.

---

## Logging Behavior

Structured, minimal log output is enforced:

- **Received**: `SEARCH_DOCUMENT_RECEIVED urlHash={} url={} topic={} partition={} offset={}`
- **Processed**: `SEARCH_DOCUMENT_PROCESSED urlHash={} topic={} partition={} offset={}`
- **Invalid**: `SEARCH_DOCUMENT_INVALID reason={} urlHash={} topic={} partition={} offset={}`

> [!NOTE]
> Large HTML/text payloads (`bodyText`, full `SearchDocument`) are never printed in logs.

---

## Testing

Run the complete test suite:
```powershell
.\mvnw.cmd clean test
```

### Test Coverage

1. **Spring Application Context Test** (`IndexerApplicationTests`): Context loads, `/actuator/health` and `/actuator/prometheus` endpoints return 200 OK.
2. **Unit Tests**:
   - `ElasticsearchConfigTest`: Client creation, timeouts, custom properties, invalid URL exception handling.
   - `ElasticsearchPropertiesTest`: Configuration property binding.
   - `ElasticsearchHealthIndicatorTest`: Ping success (`UP`), ping failure (`DOWN`), exception handling (`DOWN`).
   - `IndexerServiceTest`: Valid document accepted at domain boundary.
   - `SearchDocumentConsumerTest`: Valid document consumption & ACK, null payload handling, validation error non-ACK, processing failure non-ACK, optional/required field validation, wordCount & statusCode boundary validation.
3. **Embedded Kafka Integration Test** (`SearchDocumentConsumerIntegrationTest`):
   - Uses `@EmbeddedKafka` for end-to-end integration testing.
   - Verifies JSON deserialization, Kafka topic listener trigger, `IndexerService` invocation, and record acknowledgement without requiring an external Docker container.

---

## Local Verification & Docker

### 1. Docker Kafka Verification
Check that `search-document-topic` exists:
```bash
docker exec kafka kafka-topics --bootstrap-server kafka:29092 --list
```

### 2. Local Elasticsearch Docker Setup
```bash
docker run -d --name elasticsearch -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  docker.elastic.co/elasticsearch/elasticsearch:8.15.0
```

---

## Planned Next Features

- **Feature 3**: Elasticsearch Index Mapping & SearchDocument Indexing Service (actual document indexing into Elasticsearch).
- **Feature 4**: Error Handling, Retry Policy & Dead Letter Topic (DLT).
- **Feature 5**: Integration Testing & End-to-End Pipeline Verification.
