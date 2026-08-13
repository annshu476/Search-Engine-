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

## Feature 1 Scope

This release establishes the **Indexer Service Foundation**:

- Spring Boot 3.5.0 application foundation running on Java 21.
- Official Elasticsearch Java API Client configuration (`co.elastic.clients:elasticsearch-java`).
- Externalized configuration via `application.yml` supporting environment variable overrides.
- Actuator health check integration (`/actuator/health`) with Elasticsearch connectivity health indicator (`ElasticsearchHealthIndicator`).
- Prometheus metrics foundation (`/actuator/prometheus`).
- Clean package structure adhering to single-responsibility naming conventions.
- Fully deterministic unit and Spring context tests requiring no live Elasticsearch instance or Docker container.

---

## Package Responsibilities

```
service/indexer/src/main/java/com/searchengine/indexer/
├── IndexerApplication.java                      # Spring Boot main application entry point
├── config/
│   ├── ElasticsearchConfig.java                # Bean definitions for RestClient, ElasticsearchTransport, and ElasticsearchClient
│   └── ElasticsearchProperties.java            # Externalized configuration properties prefixed with 'elasticsearch'
├── exception/
│   └── ElasticsearchConfigurationException.java # Custom exception for invalid configuration/URLs
└── health/
    └── ElasticsearchHealthIndicator.java        # Custom Spring Boot Actuator HealthIndicator for Elasticsearch ping
```

---

## Server Port

Default Port: `8083`

- **8080**: URL Frontier
- **8081**: Crawler
- **8082**: Content Processor
- **8083**: Indexer

Can be overridden using `SERVER_PORT`:
```bash
SERVER_PORT=8083
```

---

## Configuration & Environment Variables

Configuration is defined in `src/main/resources/application.yml`.

| Property Key | Default Value | Environment Variable | Description |
|---|---|---|---|
| `server.port` | `8083` | `SERVER_PORT` | Service HTTP port |
| `elasticsearch.url` | `http://localhost:9200` | `ELASTICSEARCH_URL` | Target Elasticsearch endpoint URL |
| `elasticsearch.username` | `""` | `ELASTICSEARCH_USERNAME` | Elasticsearch basic auth username (optional) |
| `elasticsearch.password` | `""` | `ELASTICSEARCH_PASSWORD` | Elasticsearch basic auth password (optional) |
| `elasticsearch.connect-timeout-ms` | `5000` | `ELASTICSEARCH_CONNECT_TIMEOUT_MS` | Http connection timeout in ms |
| `elasticsearch.socket-timeout-ms` | `30000` | `ELASTICSEARCH_SOCKET_TIMEOUT_MS` | Http socket timeout in ms |

> [!NOTE]
> Secrets and passwords must be provided through environment variables in production and never committed to source control.

---

## Actuator & Monitoring Endpoints

- **Health Endpoint**: `GET /actuator/health`
- **Info Endpoint**: `GET /actuator/info`
- **Prometheus Metrics**: `GET /actuator/prometheus`

### Application Health vs. Elasticsearch Connectivity

- **Application Health**: Indicates whether the Spring Boot process is running and healthy.
- **Elasticsearch Connectivity**: Managed by `ElasticsearchHealthIndicator` which executes a light `ping()` query against Elasticsearch. If Elasticsearch is unreachable, the indicator reports `DOWN` status with detail `elasticsearch: Unavailable`, while leaving normal isolated application unit testing unaffected.

---

## How to Run Locally

### 1. Run standard Maven build
```powershell
.\mvnw.cmd clean package
```

### 2. Start the service
```powershell
.\mvnw.cmd spring-boot:run
```

Or run the built JAR file:
```powershell
java -jar target/indexer-0.0.1-SNAPSHOT.jar
```

---

## How to Test

Run the full deterministic test suite:
```powershell
.\mvnw.cmd clean test
```

> [!IMPORTANT]
> The test suite uses mocks and isolation to run 100% deterministically without needing Elasticsearch or Docker containers running.

---

## Local Elasticsearch Setup (Docker)

To run a local Elasticsearch container for manual testing with Indexer:

```bash
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  docker.elastic.co/elasticsearch/elasticsearch:8.15.0
```

Once running, verify connectivity:
```bash
curl http://localhost:9200
```

Then start the Indexer application and check `/actuator/health`:
```bash
curl http://localhost:8083/actuator/health
```

---

## Contract & Future Elasticsearch Index

The upstream Content Processor emits `SearchDocument`:

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

In future features, documents will be indexed into Elasticsearch using `urlHash` as the document ID for idempotent indexing.

---

## Intentionally NOT Implemented in Feature 1

1. Kafka Consumer logic (`search-document-topic` consumption).
2. Elasticsearch Index creation, mappings, analyzers, or field definitions.
3. SearchDocument indexing or bulk operations.
4. Retry mechanisms / Dead Letter Topic (DLT).
5. Search REST API endpoints or query execution.

---

## Planned Next Features

- **Feature 2**: Elasticsearch Index Schema & Mapping Configuration (`search-document` index management).
- **Feature 3**: Kafka Consumer & SearchDocument Indexing Service.
- **Feature 4**: Error Handling, Retry Policy & Dead Letter Topic (DLT).
- **Feature 5**: Integration Testing & End-to-End Pipeline Verification.
