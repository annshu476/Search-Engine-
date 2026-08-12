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

Feature 5 implements `robots.txt` fetching, rule parsing (`crawler-commons`), origin-based Caffeine caching, and compliance checks before page fetching.

### Included in Feature 5
- **Robots Parser Library**: Uses `com.github.crawler-commons:crawler-commons:1.4` for standard `robots.txt` parsing (User-Agent rules, Allow/Disallow, wildcards, Crawl-delay).
- **Origin Caching**: Thread-safe Caffeine cache (`crawler.robots.cache-ttl: 1h`, `crawler.robots.cache-max-size: 1000` origins). Origins isolate scheme and host (`http://example.com` vs `https://example.com`).
- **HTTP Status & Fail-Closed Rules**:
  - `404 Not Found` -> No Robots Policy -> **ALLOW** crawl.
  - `2xx` -> Parse rules for `SearchEngineBot/1.0` -> Evaluate **ALLOW** / **DISALLOW**.
  - `401` / `403 Forbidden` -> **DISALLOW** crawl.
  - `5xx` / Timeout / Network Error -> **ROBOTS UNAVAILABLE** -> Fail closed.
- **Kafka ACK Integration**:
  - **Allowed**: Proceeds to `PageFetcher`. If fetch succeeds -> ACK.
  - **Disallowed**: Valid crawl decision to skip -> Return normally -> **ACK**.
  - **Robots Infrastructure Unavailable**: Throws `RobotsUnavailableException` -> **NO ACK**.
- **No Robots Recursion**: `RobotsFetcher` fetches `https://<origin>/robots.txt` directly without calling `RobotsChecker` or `PageFetcher`.

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
    ├── ALLOWED → PageFetcher (Feature 3 & 4) → HTTP Server
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
Runs deterministically using `MockWebServer` without calling live external websites:

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
