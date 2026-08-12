# Crawler Service

The Crawler Service is a microservice responsible for consuming URLs from the URL Frontier, fetching web pages according to `robots.txt` policies and domain rate limits, validating HTML content, and producing raw HTML documents to the downstream pipeline topic (`raw-html-topic`).

---

## Feature 1 Scope: Crawler Foundation

Feature 1 established the Spring Boot 3.5.0 and Java 21 foundation, package architecture, Actuator endpoints, and WebClient configuration.

---

## Feature 2 Scope: Kafka Consumer

Feature 2 implements Kafka consumer support for consuming `UrlTask` messages from `url-topic`.

### Included in Feature 2
- **Kafka Contract**: `UrlTask` record matching URL Frontier's message payload (`schemaVersion`, `url`, `urlHash`, `priority`, `discoveredAt`).
- **Consumer Group**: `crawler-service` with `concurrency = 1`.
- **Topic**: `url-topic`
- **Deserialization**: `ErrorHandlingDeserializer` wrapping `JsonDeserializer` configured for `com.searchengine.crawler.model.kafka.UrlTask` with trusted package restriction.
- **Validation**:
  - `schemaVersion == 1` (rejects unsupported schema versions with log `URL_TASK_UNSUPPORTED_SCHEMA`)
  - `url` required (non-null, non-blank)
  - `urlHash` required (non-null, non-blank)
  - `priority` required (1..10 inclusive)
  - `discoveredAt` required (non-null timestamp)
- **Manual Acknowledgement**: Ack mode set to `manual_immediate`. Acknowledges messages (`ack.acknowledge()`) ONLY after validation succeeds and `CrawlerService.processUrlTask(...)` returns normally.
- **Offline Testing**: Complete unit test suite running without requiring a live Kafka broker.

---

## Future Architecture Pipeline

```text
url-topic
    ↓
UrlTaskConsumer (Feature 2)
    ↓
CrawlerService
    ↓
robots.txt Checker (Future)
    ↓
Domain Rate Limiter (Future)
    ↓
WebClient Fetcher (Future)
    ↓
HTML Validation (Future)
    ↓
RawHtmlDocument (Future)
    ↓
raw-html-topic (Future)
```

---

## Service Architecture Responsibilities

### Crawler Owns:
- Consuming `UrlTask` messages from `url-topic`
- Web page fetching (future)
- `robots.txt` policy adherence (future)
- Per-domain rate limiting (future)
- HTTP error and retry handling (future)
- Raw HTML document generation & publishing (future)

### Crawler Does NOT Own:
- URL deduplication (owned by URL Frontier)
- URL priority calculation (owned by URL Frontier)
- Elasticsearch indexing / search queries

---

## Running Infrastructure & Service

### 1. Start Infrastructure (Docker)
From project root:

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d zookeeper kafka redis
```

### 2. Run Service
From `service/crawler` directory:

```bash
./mvnw spring-boot:run
```

By default, the service runs on port `8081`.

---

## Verification & Testing

### Offline Unit & Context Tests (Default)
Runs deterministically without Kafka:

```bash
./mvnw test
```

### Health Endpoint
Check service health:

```bash
curl http://localhost:8081/actuator/health
```

Expected response:
```json
{"status":"UP"}
```
