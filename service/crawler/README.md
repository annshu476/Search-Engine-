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

Feature 6 implements polite per-origin request rate limiting (`DomainRateLimiter`), enforcing minimum request intervals and a maximum of 1 active request per origin.

### Included in Feature 6
- **Origin Identity**: Canonical `scheme://host[:port]` key. Standard ports (`http:80`, `https:443`) are stripped for consistency (`https://example.com:443` -> `https://example.com`). `http` and `https` operate independently.
- **Delay Calculation**:
  - `robots Crawl-delay` (if valid >= 0) overrides default.
  - Fallback to `crawler.rate-limit.default-delay` (`2s`).
  - Clamped between `0s` and `60s` (`crawler.rate-limit.max-delay`).
- **Concurrency & Isolation**:
  - Max active requests per origin = `1` (`max-concurrent-per-origin`).
  - Managed per-origin via Caffeine cache (`1000` max origins, `30m` idle expiration).
  - Requests to distinct origins execute concurrently without mutual blocking.
- **Permit Safety**:
  - `CrawlerService` acquires rate-limit permit and guarantees permit release in `finally` block for success, errors, timeouts, and exceptions.

---

## Architecture Pipeline

```text
url-topic
    ↓
UrlTaskConsumer (Feature 2)
    ↓
CrawlerService
    ↓
RobotsChecker (Feature 5)
    ├── ALLOWED → DomainRateLimiter (Feature 6) → PageFetcher (Feature 3 & 4) → HTTP Server
    ├── DISALLOWED → Skip page fetch & ACK task
    └── UNAVAILABLE → Throw exception & NO ACK task
```

---

## Service Architecture Responsibilities

### Crawler Owns:
- Consuming `UrlTask` messages from `url-topic`
- Non-blocking HTTP page fetching using Spring WebClient
- HTTP response classification, content-type filtering & size validation
- `robots.txt` fetching, rule parsing, origin caching, and compliance checks
- Polite per-origin rate limiting & concurrency control
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
Runs deterministically using `MockWebServer` and simulated clocks without calling live external websites:

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
