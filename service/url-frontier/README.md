# URL Frontier

## Purpose

`url-frontier` manages the entry point for the crawl frontier in the distributed search engine. It currently validates, normalizes, and hashes URL submissions locally; it does not store, deduplicate, prioritize, or publish them.

## Responsibilities

- Provide service health and operational metrics.
- Accept valid HTTP and HTTPS URL submissions through a REST API.
- Normalize submitted URLs without network access.
- Generate a deterministic SHA-256 hash from each normalized URL.
- Return the original URL, normalized URL, URL hash, and a UTC timestamp.
- Convert common malformed or invalid HTTP requests into standard error responses.

## Technologies Used

- Java 21 and Spring Boot 3.5
- Maven and the Maven Wrapper
- Spring Web and Bean Validation
- Spring for Apache Kafka and Spring Data Redis (dependencies only at this stage)
- Spring Boot Actuator, Micrometer, and Prometheus registry
- Lombok

## Package Structure

```
com.searchengine.urlfrontier
├── config/       application configuration and typed properties
├── controller/   HTTP endpoints
├── hasher/       SHA-256 URL hash generation
├── normalizer/   URL normalization
├── service/      application use cases
├── validator/    request validation
├── exception/    common HTTP error handling
├── repository/   future Redis adapters
├── producer/     future Kafka publishers
├── consumer/     future Kafka listeners
├── metrics/      future custom Micrometer metrics
├── util/         small shared utilities
└── model/
    ├── dto/      HTTP models
    ├── kafka/    Kafka event models
    └── redis/    Redis persistence models
```

The `constant`, `controller`, `service`, `normalizer`, `hasher`, `validator`, `exception`, and `model/dto` packages contain the implemented URL submission flow. The remaining packages are intentionally empty placeholders for later features.

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
  "message": "URL processed successfully",
  "timestamp": "2026-07-31T10:00:00Z"
}
```

## Completed Features

- Feature 3: URL normalization
- Feature 4: SHA-256 URL hash generation

## Feature 4: URL Hash Generation

The request flow is:

```text
Client -> UrlController -> UrlFrontierService -> UrlNormalizer -> Sha256UrlHasher -> Response
```

`UrlNormalizer` and `Sha256UrlHasher` are pure local components. They do not make DNS lookups, contact the internet, resolve redirects, or interact with Redis or Kafka.

Normalization rules:

- Trim leading and trailing whitespace.
- Convert the scheme and host to lowercase.
- Remove `/` only when it is the root path.
- Preserve deeper paths, query parameters, and fragments.

`Sha256UrlHasher` accepts the normalized URL, encodes it as UTF-8, and returns its lowercase hexadecimal SHA-256 digest. Hashing is performed after normalization so equivalent input URLs, such as `HTTPS://SPRING.IO/` and `https://spring.io`, receive the same hash.

Hashing comes before Redis because Redis should eventually use this stable value as its deduplication key. Generating the key first keeps storage implementation-independent and allows a later Redis adapter to perform an atomic check-and-store without redefining URL identity. This feature only generates and returns the hash; it does not compare, store, or deduplicate hashes.

Validation failures return `400 Bad Request` using Spring Problem Details. The `url` field is required, cannot be blank, and must be a valid HTTP or HTTPS URL.

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

No Docker dependency is required for this feature. Kafka and Redis client libraries are present, but their health indicators are disabled until the corresponding integration is implemented.

Build and run the service image:

```bash
docker build -t url-frontier:local .
docker run --rm -p 8080:8080 --name url-frontier url-frontier:local
```

Stop the container with `docker stop url-frontier`.

## Environment Variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP port for the service. |
| `LOGGING_LEVEL_ROOT` | `INFO` | Root logging level. |
| `LOGGING_LEVEL_APPLICATION` | `INFO` | Logging level for application classes. |

## Roadmap

1. Redis-backed deduplication and frontier storage using the existing URL hash as the key.
2. Frontier-specific Micrometer counters and timers.
3. Kafka events for crawler coordination after accepted URLs can be persisted atomically.
4. Priority scheduling and crawl politeness controls.
