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

Feature 3 implements non-blocking HTTP web page fetching using Spring WebClient (`PageFetcher`).

### Included in Feature 3
- **PageFetcher Component**: Reactive fetcher component returning `Mono<PageFetchResult>`.
- **Fetch Result DTO**: `PageFetchResult` containing `requestedUrl`, `finalUrl`, `statusCode`, `contentType`, `body`, `fetchedAt`, `success`, and `failureReason`.
- **User-Agent Header**: Configurable `SearchEngineBot/1.0` (`crawler.http.user-agent`).
- **Timeouts**: Connect timeout `5s` (`crawler.http.connect-timeout`), Response timeout `10s` (`crawler.http.response-timeout`).
- **Redirects**: Auto-follows up to `5` HTTP redirects (`crawler.http.max-redirects`) and tracks `finalUrl`.
- **Max Response Size**: Configurable `10MB` codec limit (`crawler.http.max-response-size`) to prevent JVM memory exhaustion.
- **Content Type Filter**: Restricts processing to HTML (`text/html`, `application/xhtml+xml`). Unsupported binary/media formats (e.g. PDF, images) are rejected.
- **SSRF Protection**: Host/IP validation rejecting loopback (`127.0.0.1`, `::1`), link-local (`169.254.x.x`), and private IP ranges (`10.x.x.x`, `172.16-31.x.x`, `192.168.x.x`).
- **Kafka ACK Propagation**: HTTP fetch failures throw `PageFetchException` inside `CrawlerService`, preventing Kafka offset ACK for failed tasks.
- **Offline Deterministic Testing**: Test suite using `MockWebServer` to test HTTP status codes, redirects, timeouts, content-type filtering, size limits, and SSRF blocking offline.

---

## Architecture Pipeline

```text
url-topic
    ↓
UrlTaskConsumer (Feature 2)
    ↓
CrawlerService
    ↓
PageFetcher (Feature 3)
    ↓
WebClient (Feature 3)
    ↓
HTTP Server
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
