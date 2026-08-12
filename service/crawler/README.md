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

### Included in Feature 7
- **Non-Blocking Retry Architecture**: Uses Spring Kafka `@RetryableTopic` with 4 total attempts (1 initial attempt + 3 retries).
  - Retry Delays: 2s -> 5s -> 15s.
  - Automatically creates retry topics (`url-topic-retry-2000`, `url-topic-retry-5000`, `url-topic-retry-15000`) and dead-letter topic (`url-topic-dlt`).
  - Failed messages are forwarded to retry topics without blocking main consumer threads.
- **Exception Classification**:
  - **Retryable (Transient)** -> `RetryableCrawlerException` & `RobotsUnavailableException`:
    - HTTP `500, 502, 503, 504, 429`.
    - Connection timeouts, response timeouts, DNS failures, network glitches.
    - `Robots.txt` 5xx, timeouts, network failures.
  - **Non-Retryable (Permanent)** -> `NonRetryableCrawlerException`:
    - HTTP `400, 401, 403, 404, 410`.
    - Unsupported Content-Type (`application/pdf`, `image/png`).
    - Empty HTML body or oversized response (> 10MB).
    - SSRF blocked / unsafe destination.
    - Unsupported `schemaVersion` != 1 or malformed JSON payloads.
- **Robots Disallowed Handling**:
  - Disallowed URLs are a valid crawling decision -> Logged & skipped -> **ACK** (not retried).
- **DLT Handler**:
  - `handleDlt` method logs `CRAWL_DLT` metadata (`urlHash`, `url`, `originalTopic`, `partition`, `offset`, `failureReason`).

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
    ├── ALLOWED → DomainRateLimiter (Feature 6) → PageFetcher (Feature 3 & 4) → HTTP Server
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
docker exec -it kafka kafka-topics --bootstrap-server kafka:29092 --list
```

Expected topic output:
```text
url-topic
url-topic-retry-2000
url-topic-retry-5000
url-topic-retry-15000
url-topic-dlt
```

### 3. Read DLT Messages
```bash
docker exec -it kafka kafka-console-consumer --bootstrap-server kafka:29092 --topic url-topic-dlt --from-beginning
```

---

## Verification & Testing

### Offline Unit & Context Tests (Default)
Runs deterministically without requiring a live Kafka broker:

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
