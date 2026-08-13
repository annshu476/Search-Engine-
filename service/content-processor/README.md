# Content Processor Service

The **Content Processor** service transforms raw HTML documents fetched by the Crawler into structured search documents.

## Current Scope (Feature 1: Service Foundation)

Feature 1 establishes the service foundation, Kafka infrastructure, message contracts, and application configuration.

> [!NOTE]
> **Important Scope Notes:**
> - HTML parsing (e.g. Jsoup) is **NOT** implemented in Feature 1.
> - SearchDocument extraction logic is **NOT** implemented in Feature 1.
> - Elasticsearch connection and search indexing/queries are **NOT** implemented in Feature 1.
> - SearchDocument publishing is **NOT** active yet (only topic infrastructure is declared).

---

## Service Specifications

| Specification | Value |
|---|---|
| Service Name | `content-processor` |
| Default HTTP Port | `8082` (`${SERVER_PORT:8082}`) |
| Input Topic | `raw-html-topic` (`${RAW_HTML_TOPIC:raw-html-topic}`) |
| Output Topic | `search-document-topic` (`${SEARCH_DOCUMENT_TOPIC:search-document-topic}`) |
| Consumer Group | `content-processor` (`${KAFKA_CONSUMER_GROUP:content-processor}`) |
| Acknowledgment | `manual_immediate` (ACK on success; no ACK on processing failure) |

---

## Message Contracts

### Input Contract: `RawHtmlDocument` (`raw-html-topic`)

```java
public record RawHtmlDocument(
    int schemaVersion,
    String url,
    String finalUrl,
    String urlHash,
    int statusCode,
    String contentType,
    String html,
    Instant fetchedAt
) {}
```

### Output Contract: `SearchDocument` (`search-document-topic` - V1 Locked)

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

---

## Actuator & Monitoring

- Health check: `http://localhost:8082/actuator/health`
- Info: `http://localhost:8082/actuator/info`
- Prometheus metrics: `http://localhost:8082/actuator/prometheus`

---

## Build & Test Commands

### Run Unit and Integration Tests

```bash
./mvnw test
```

### Run Service Locally

```bash
./mvnw spring-boot:run
```

---

## Docker & Kafka Verification

### Start Infrastructure Services

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d zookeeper kafka redis
```

### Verify Kafka Topics

```bash
docker exec kafka kafka-topics --bootstrap-server kafka:29092 --list
```

Expected output includes:
- `raw-html-topic`
- `search-document-topic`
- `url-topic`

---

## Folder Structure

```
service/content-processor/
├── pom.xml
├── README.md
├── .gitignore
├── .dockerignore
├── mvnw
├── mvnw.cmd
├── .mvn/
│   └── wrapper/
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/
    │   │       └── searchengine/
    │   │           └── contentprocessor/
    │   │               ├── ContentProcessorApplication.java
    │   │               ├── config/
    │   │               │   ├── KafkaConsumerConfig.java
    │   │               │   └── SearchDocumentTopicConfig.java
    │   │               ├── consumer/
    │   │               │   └── RawHtmlConsumer.java
    │   │               ├── service/
    │   │               │   └── ContentProcessorService.java
    │   │               ├── model/
    │   │               │   └── kafka/
    │   │               │       ├── RawHtmlDocument.java
    │   │               │       └── SearchDocument.java
    │   │               └── exception/
    │   │                   └── GlobalExceptionHandler.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/
            └── com/
                └── searchengine/
                    └── contentprocessor/
                        ├── ContentProcessorApplicationTests.java
                        ├── consumer/
                        │   ├── RawHtmlConsumerTest.java
                        │   └── RawHtmlConsumerIntegrationTest.java
                        └── service/
                            └── ContentProcessorServiceTest.java
```
