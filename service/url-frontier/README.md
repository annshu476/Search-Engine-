# URL Frontier

## Purpose

`url-frontier` is the entry point for crawl frontier submissions in the distributed search engine. It currently accepts URL submissions, validates them, normalizes them, hashes them, deduplicates them in Redis, and assigns an initial backend-managed crawl priority.

## Responsibilities

- Provide service health and operational metrics.
- Accept valid HTTP and HTTPS URL submissions through a REST API.
- Normalize submitted URLs without network access.
- Generate a deterministic SHA-256 hash from each normalized URL.
- Deduplicate normalized URLs in Redis for seven days.
- Assign an initial crawl priority to newly accepted URLs.
- Return the original URL, normalized URL, URL hash, assigned priority when applicable, and a UTC timestamp.
- Convert malformed or invalid HTTP requests into standard error responses.

## Technologies Used

- Java 21 and Spring Boot 3.5
- Maven and the Maven Wrapper
- Spring Web and Bean Validation
- Spring Data Redis
- Spring for Apache Kafka dependencies only
- Spring Boot Actuator, Micrometer, and Prometheus registry
- Lombok

## Package Structure

```text
com.searchengine.urlfrontier
|- config/       application configuration and typed properties
|- constant/     API path constants
|- controller/   HTTP endpoints
|- exception/    common HTTP error handling
|- hasher/       SHA-256 URL hash generation
|- model/        DTO, Kafka, and Redis models
|- normalizer/   URL normalization
|- priority/     URL priority assignment
|- repository/   Redis-backed URL deduplication
|- service/      application use cases
`- validator/    request validation
```

Future-facing placeholder packages remain for Kafka consumers, producers, metrics, utilities, and transport models that are not implemented yet.

## API Documentation

### Submit a URL

`POST /urls`

Request body:

```json
{
  "url": " HTTPS://SPRING.IO/ "
}
```

Successful response: `202 Accepted`

```json
{
  "accepted": true,
  "originalUrl": " HTTPS://SPRING.IO/ ",
  "normalizedUrl": "https://spring.io",
  "urlHash": "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28",
  "priority": 5,
  "message": "URL accepted",
  "timestamp": "2026-07-31T10:00:00Z"
}
```

Duplicate response: `409 Conflict`

```json
{
  "accepted": false,
  "originalUrl": "https://spring.io",
  "normalizedUrl": "https://spring.io",
  "urlHash": "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28",
  "message": "URL has already been seen",
  "timestamp": "2026-07-31T10:00:00Z"
}
```

## Completed Features

- Feature 1: Project setup
- Feature 2: URL ingestion API
- Feature 3: URL normalization
- Feature 4: SHA-256 URL hash generation
- Feature 5: Redis URL deduplication
- Feature 6: URL priority assignment

## URL Frontier Flow

```text
Client
  -> UrlController
  -> UrlFrontierService
  -> UrlNormalizer
  -> Sha256UrlHasher
  -> VisitedUrlRepository
  -> UrlPriorityAssigner
  -> Response
```

## Feature 5: Redis URL Deduplication

Redis stores `visited:{urlHash}`, for example `visited:007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28`.

- Value: ISO-8601 UTC discovery timestamp
- TTL: 7 days
- Atomic write: `setIfAbsent(key, value, ttl)`
- Duplicate behavior: return `409 Conflict`, do not overwrite timestamp, do not refresh TTL

Redis does not store the URL body or the assigned priority.

## Feature 6: URL Priority Assignment

Priority assignment is intentionally simple in V1.

- Allowed range: `1` to `10`
- Default priority: `5`
- Meaning: `1` is lowest priority and `10` is highest priority

Only newly accepted URLs receive a priority. Duplicate URLs do not trigger a new priority assignment and keep the existing duplicate response behavior.

Clients cannot choose priority. The request contract stays:

```json
{
  "url": "https://spring.io"
}
```

V1 uses a fixed priority because the service does not yet have enough information to rank URLs meaningfully. Future versions may use signals such as seed URLs, page importance, freshness, domain policies, link signals, and crawl frequency. That logic belongs in later features, so the current implementation isolates assignment in `UrlPriorityAssigner` without introducing premature strategy abstractions.

## Validation Errors

Validation failures return `400 Bad Request` using Spring Problem Details.

```json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "detail": "Request validation failed",
  "errors": [
    "url: url must be a valid HTTP or HTTPS URL"
  ]
}
```

## Running the Service

From this directory:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The service listens on `http://localhost:8080` by default.

## Docker Dependencies

Start the shared Redis dependency from `infrastructure/docker`:

```bash
docker compose up -d redis
docker ps
docker logs redis
docker compose stop redis
```

Kafka dependencies are present in the build only for future features. Kafka publishing is not implemented yet.

## Environment Variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP port for the service. |
| `LOGGING_LEVEL_ROOT` | `INFO` | Root logging level. |
| `LOGGING_LEVEL_APPLICATION` | `INFO` | Logging level for application classes. |

## Testing

- Unit tests mock `VisitedUrlRepository`.
- HTTP/application tests use mocked infrastructure for deterministic responses.
- Redis integration tests are explicitly tagged as `integration`.
- No Testcontainers are used.

Run the default test suite:

```bash
./mvnw test
```

Run tagged Redis integration tests explicitly:

```bash
./mvnw -Predis-integration test
```

## Roadmap

1. Frontier-specific Micrometer counters and timers.
2. Kafka events for accepted crawl tasks after persistence remains atomic.
3. Richer priority strategies based on crawl signals.
4. Scheduling and crawl politeness controls.
