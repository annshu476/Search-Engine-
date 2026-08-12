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

Feature 4 implements explicit HTTP response classification, content-type verification, empty-body checks, and redirect limit enforcement.

### Included in Feature 4
- **HTTP Status Rules**:
  - `200 OK` / `201 Created` with valid non-blank HTML body -> `success = true`.
  - `204 No Content` -> `success = false` (`failureReason="No content (204)"`).
  - `3xx` redirects (301, 302, 303, 307, 308) -> Auto-followed up to `maxRedirects = 5`. Exceeding 5 redirects -> `success = false` (`failureReason="Redirect limit exceeded"`).
  - `4xx` (400, 401, 403, 404, 410) & `5xx` (500, 502, 503) -> `success = false`, status code preserved in `PageFetchResult`.
- **Content-Type Validation**:
  - Accepts `text/html` and `application/xhtml+xml` (including charset parameters e.g., `text/html; charset=UTF-8`).
  - Missing Content-Type or binary/media types (`application/pdf`, `image/png`, `video/mp4`, `application/zip`) -> `success = false`.
- **Body Validation**:
  - Null, empty (`""`), or whitespace-only (`"   "`) HTML bodies -> `success = false` (`failureReason="Empty HTML response"`).
- **Redirect Limit Enforcement**:
  - Netty `HttpClient` redirect predicate explicitly configured: `res.redirectedFrom().length < maxRedirects`.
  - `finalUrl` accurately captures destination URL after redirects.
- **Failure Representation & Kafka ACK**:
  - Structured `PageFetchResult` generated for expected HTTP failure outcomes.
  - `CrawlerService` logs `PAGE_FETCH_FAILURE` and propagates `PageFetchException` to prevent Kafka ACK for un-fetched URLs.
- **Deterministic Testing**: Comprehensive test coverage in `PageFetcherTest` using `MockWebServer` testing 2xx, 204, 3xx redirects, 4xx/5xx status codes, missing/unsupported Content-Types, empty bodies, charsets, and SSRF blocking offline.

---

## Architecture Pipeline

```text
url-topic
    ↓
UrlTaskConsumer (Feature 2)
    ↓
CrawlerService
    ↓
PageFetcher (Feature 3 & 4)
    ↓
WebClient (Feature 3 & 4)
    ↓
HTTP Response Classification (Feature 4)
    ↓
robots.txt Checker (Future)
    ↓
Domain Rate Limiter (Future)
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
- Non-blocking HTTP page fetching using Spring WebClient
- HTTP response classification, content-type filtering & size validation
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
