# Crawler Service

The Crawler Service is a microservice responsible for consuming URLs from the URL Frontier, fetching web pages according to `robots.txt` policies and domain rate limits, validating HTML content, and producing raw HTML documents to the downstream pipeline topic (`raw-html-topic`).

---

## Feature 1 Scope: Crawler Foundation

Feature 1 established the Spring Boot 3.5.0 and Java 21 foundation, package architecture, Actuator endpoints, and WebClient configuration.

---

## Feature 2 Scope: Kafka Consumer

Feature 2 implemented Kafka consumer support for consuming `UrlTask` messages from `url-topic` under consumer group `crawler-service`.

---

## Feature 3 Scope: HTTP Page Fetching

Feature 3 implemented non-blocking HTTP web page fetching using Spring WebClient (`PageFetcher`).

---

## Feature 4 Scope: HTTP Response Handling

Feature 4 implemented explicit HTTP response classification, content-type verification, empty-body checks, and redirect limit enforcement.

---

## Feature 5 Scope: Robots.txt Compliance

Feature 5 implemented `robots.txt` fetching, rule parsing (`crawler-commons`), origin-based Caffeine caching, and compliance checks before page fetching.

---

## Feature 6 Scope: Domain Rate Limiting

Feature 6 implemented polite per-origin request rate limiting (`DomainRateLimiter`), enforcing minimum request intervals and a maximum of 1 active request per origin.

---

## Feature 7 Scope: Retry & Failure Handling

Feature 7 implements Spring Kafka native non-blocking retry topics (`@RetryableTopic`) and dead-letter topic (`url-topic-dlt`) with strict exception classification into transient vs permanent failures.

---

## Feature 8 Scope: Raw HTML Publishing

Feature 8 implements publishing fetched HTML pages to the downstream Kafka topic `raw-html-topic`.

### Key Components of Feature 8
- **Kafka Contract (`RawHtmlDocument`)**:
  - `schemaVersion` (int) - Currently version 1.
  - `url` (String) - Original requested URL.
  - `finalUrl` (String) - Final URL after HTTP redirects.
  - `urlHash` (String) - SHA-256 canonical hash used as the Kafka message key.
  - `statusCode` (int) - HTTP status code (e.g. 200).
  - `contentType` (String) - Verified MIME type (e.g. `text/html`).
  - `html` (String) - Complete HTML body payload.
  - `fetchedAt` (Instant) - Timestamp when fetch completed.
- **Topic Configuration (`raw-html-topic`)**:
  - Partition count: 1 partition for V1.
  - Replication factor: 1 for local development.
  - Defined cleanly via `RawHtmlTopicConfig.java` and externalized via `application.yml` (`crawler.raw-html.topic`).
- **Producer Component (`RawHtmlProducer`)**:
  - Publishes `RawHtmlDocument` using `urlHash` as the message key.
  - Bounded timeout (`crawler.raw-html.publish-timeout: 3s`) waiting for broker ACK.
  - Throws `RawHtmlPublishException` on timeout or broker failure.
- **Publish Failure Integration**:
  - `RawHtmlPublishException` extends `RetryableCrawlerException`.
  - When raw HTML publishing fails, the exception is caught by Feature 7's non-blocking retry mechanism, retrying the task across retry topics (`url-topic-retry-2000`, `url-topic-retry-5000`, `url-topic-retry-15000`) before routing to `url-topic-dlt`.
  - Rate-limit permits are guaranteed to be released in `finally` blocks prior to publishing.
- **At-Least-Once Delivery Semantics & Limitations**:
  - Deduplication is not performed on `raw-html-topic`.
  - If a task is retried after HTML was successfully fetched but Kafka publishing failed, the same `RawHtmlDocument` may be published more than once.
  - Downstream services (e.g., HTML parser) must handle duplicate documents statelessly or idempotently.
- **Payload Size**:
  - Page fetch response size remains capped at 10MB (`PageFetcher` response size limit).
  - `RawHtmlDocument` carries the complete raw HTML string. Compression or external blob storage can be evaluated in subsequent pipeline iterations.

---

## Architecture Pipeline

```text
url-topic
    ↓
UrlTaskConsumer (Feature 2 & 7)
    ↓
CrawlerService
    ↓
RobotsChecker (Feature 5)
    ├── ALLOWED → DomainRateLimiter (Feature 6) → PageFetcher (Feature 3 & 4)
    │                                                      ↓ (SUCCESS)
    │                                              RawHtmlDocument (Feature 8)
    │                                                      ↓
    │                                              RawHtmlProducer
    │                                                      ↓
    │                                              raw-html-topic → ACK Task
    │                                                      ↓ (PUBLISH FAILURE)
    │                                              RawHtmlPublishException → Retry Topics (2s, 5s, 15s) → DLT
    ├── DISALLOWED → Skip page fetch & ACK task
    └── UNAVAILABLE → RetryableCrawlerException → Retry Topics (2s, 5s, 15s) → DLT
```

---

## Local Verification & Topic Inspection

### 1. Start Infrastructure (Docker)
From project root:

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d zookeeper kafka redis
```

### 2. Inspect Topics in Kafka Container
```bash
docker exec kafka kafka-topics --bootstrap-server kafka:29092 --list
```

Expected topic output:
```text
raw-html-topic
url-topic
url-topic-retry-2000
url-topic-retry-5000
url-topic-retry-15000
url-topic-dlt
```

### 3. Consume Raw HTML Messages
```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic raw-html-topic \
  --from-beginning \
  --property print.key=true \
  --property key.separator=" | "
```

### 4. Read DLT Messages
```bash
docker exec kafka kafka-console-consumer --bootstrap-server kafka:29092 --topic url-topic-dlt --from-beginning
```

---

## Verification & Testing

### Offline Unit & Integration Tests
Runs complete test suite using embedded Kafka brokers without requiring live external services:

```bash
.\mvnw.cmd clean test
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
