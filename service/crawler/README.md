# Crawler Service

The Crawler Service is a microservice responsible for consuming URLs from the URL Frontier, fetching web pages according to `robots.txt` policies and domain rate limits, validating HTML content, and producing raw HTML documents to the downstream pipeline topic (`raw-html-topic`).

## Feature 1 Scope: Crawler Foundation

Feature 1 establishes the clean Spring Boot service foundation for the Crawler microservice.

### Included in Feature 1
- Spring Boot 3.5.0 and Java 21 foundation setup
- Project package structure (`config`, `consumer`, `service`, `fetcher`, `robots`, `ratelimit`, `producer`, `model`, `exception`)
- Spring WebFlux / `WebClient` configuration infrastructure
- Spring Kafka dependency configuration
- Spring Boot Actuator and Prometheus metrics foundation
- Context load and Actuator health test suite

### Current Limitations (Intentionally Excluded from Feature 1)
- **No HTTP Crawling**: Actual WebClient HTTP requests, redirects, User-Agent header logic, response size limits, and timeout handlers are not yet implemented.
- **No Kafka Consumers**: `@KafkaListener` and `url-topic` consumer logic are not active.
- **No Rate Limiting / Robots.txt**: Per-domain rate limiting and `robots.txt` parsing belong to later features.

---

## Future Architecture Pipeline

```text
url-topic
    ↓
Crawler Kafka Consumer
    ↓
robots.txt Checker
    ↓
Domain Rate Limiter
    ↓
WebClient Fetcher
    ↓
HTML Validation
    ↓
RawHtmlDocument
    ↓
raw-html-topic
```

---

## Service Architecture Responsibilities

### Crawler Owns:
- Web page fetching
- `robots.txt` policy adherence
- Per-domain rate limiting
- HTTP error and retry handling
- Raw HTML document generation

### Crawler Does NOT Own:
- URL deduplication (owned by URL Frontier)
- URL priority calculation (owned by URL Frontier)
- Elasticsearch indexing / search queries

---

## Running the Service

### Prerequisites
- Java 21
- Maven (or `./mvnw` wrapper)

### Local Startup
From the `service/crawler` directory:

```bash
./mvnw spring-boot:run
```

By default, the service runs on port `8081`.

---

## Health Endpoint

Check the health status of the service:

```bash
curl http://localhost:8081/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

---

## Testing

Run unit and context tests:

```bash
./mvnw test
```
