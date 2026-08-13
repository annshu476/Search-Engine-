# Content Processor Service

The **Content Processor** service transforms raw HTML documents fetched by the Crawler into structured search documents and publishes them to Kafka.

---

## Service Specifications

| Specification | Value |
|---|---|
| Service Name | `content-processor` |
| Default HTTP Port | `8082` (`${SERVER_PORT:8082}`) |
| Input Topic | `raw-html-topic` (`${RAW_HTML_TOPIC:raw-html-topic}`) |
| Output Topic | `search-document-topic` (`${SEARCH_DOCUMENT_TOPIC:search-document-topic}`) |
| Consumer Group | `content-processor` (`${KAFKA_CONSUMER_GROUP:content-processor}`) |
| Message Key | `SearchDocument.urlHash` |
| Publish Timeout | `3s` (`${SEARCH_DOCUMENT_PUBLISH_TIMEOUT:3s}`) |
| Acknowledgment | `manual_immediate` (ACK on success; no ACK on parsing or publishing failure) |

---

## Architecture & Pipeline

```text
raw-html-topic
      ↓
RawHtmlConsumer
      ↓
ContentProcessorService
      ↓
HtmlDocumentParser (Jsoup DOM extraction)
      ↓
SearchDocument
      ↓
SearchDocumentProducer (acks=all, key=urlHash)
      ↓
search-document-topic
      ↓
[Kafka Broker ACK -> RawHtmlConsumer ACK]
```

---

## Feature 3: SearchDocument Kafka Publishing

Feature 3 completes the end-to-end Kafka-to-Kafka processing pipeline by publishing parsed `SearchDocument` records to `search-document-topic`.

### Producer & Reliability Configuration

- **Kafka Key**: `SearchDocument.urlHash()` ensures deterministic partitioning across topic partitions.
- **Serialization**: `JsonSerializer` serializes `SearchDocument` as JSON without Java class type headers.
- **Producer Guarantees**: `acks=all`, `enable.idempotence=true`.
- **Broker ACK Wait**: `SearchDocumentProducer` synchronously waits for broker acknowledgment up to `publish-timeout` (default 3s).
- **Publish Failure Handling**: If broker acknowledgment times out or fails, `SearchDocumentProducer` throws a retryable `SearchDocumentPublishException`. `RawHtmlConsumer` catches the exception and withholds message acknowledgment (`ack.acknowledge()`), allowing Kafka to redeliver the `RawHtmlDocument`.

### At-Least-Once Delivery & Idempotency Semantics

- **At-Least-Once Semantics**: If a `SearchDocument` is published to Kafka but the service crashes before acknowledging the incoming `RawHtmlDocument`, Kafka will redeliver the raw HTML message, potentially publishing a duplicate `SearchDocument`.
- **Idempotency**: The downstream Indexer service will handle duplicates idempotently by using `urlHash` as the Elasticsearch `_id`.

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
./mvnw clean test
```

Windows:
```cmd
.\mvnw.cmd clean test
```

---

## Docker & Kafka Verification

### 1. Start Infrastructure Services

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d zookeeper kafka redis
```

### 2. Verify Kafka Topics

```bash
docker exec kafka kafka-topics --bootstrap-server kafka:29092 --list
```

Expected output includes `raw-html-topic` and `search-document-topic`.

### 3. Consume Output Topic

```bash
docker exec kafka kafka-console-consumer --bootstrap-server kafka:29092 --topic search-document-topic --from-beginning --property print.key=true --property key.separator=" | "
```

### 4. Publish Test Message to Input Topic

```bash
docker exec -i kafka kafka-console-producer --bootstrap-server kafka:29092 --topic raw-html-topic --property parse.key=true --property key.separator=":"
```
Input line:
```text
feature-3-docker-test:{"schemaVersion":1,"url":"https://example.com","finalUrl":"https://example.com","urlHash":"feature-3-docker-test","statusCode":200,"contentType":"text/html","html":"<html lang=\"en\"><head><title>Example Domain</title><meta name=\"description\" content=\"Example description\"><link rel=\"canonical\" href=\"https://example.com\"></head><body><h1>Example Domain</h1><p>Hello search engine</p></body></html>","fetchedAt":"2026-08-13T11:30:00Z"}
```
