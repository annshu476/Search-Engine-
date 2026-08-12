# URL Frontier

## Purpose

`url-frontier` is the entry point for crawl frontier submissions in the distributed search engine. It accepts URL submissions, validates them, normalizes them, hashes them, deduplicates them in Redis, assigns an initial backend-managed crawl priority, and publishes newly accepted crawl tasks to Kafka.

## Responsibilities

- Provide service health and operational metrics.
- Accept valid HTTP and HTTPS URL submissions through a REST API.
- Normalize submitted URLs without network access.
- Generate a deterministic SHA-256 hash from each normalized URL.
- Deduplicate normalized URLs in Redis for seven days.
- Assign an initial crawl priority to newly accepted URLs.
- Publish accepted URL crawl tasks to Kafka.
- Return the original URL, normalized URL, URL hash, assigned priority when applicable, and a UTC timestamp.
- Convert malformed or invalid HTTP requests into standard error responses.

## Technologies Used

- Java 21 and Spring Boot 3.5
- Maven and the Maven Wrapper
- Spring Web and Bean Validation
- Spring Data Redis
- Spring for Apache Kafka
- Spring Boot Actuator, Micrometer, and Prometheus registry
- Lombok

## Package Structure

```text
com.searchengine.urlfrontier
|- config/       application configuration, topic creation, and typed properties
|- constant/     API path constants
|- controller/   HTTP endpoints
|- exception/    common HTTP error handling
|- hasher/       SHA-256 URL hash generation
|- model/        DTO, Kafka, and Redis models
|- normalizer/   URL normalization
|- priority/     URL priority assignment
|- producer/     Kafka publishing
|- repository/   Redis-backed URL deduplication
|- service/      application use cases
`- validator/    request validation
```

Future-facing placeholder packages remain for Kafka consumers, metrics, utilities, and transport models that are not implemented yet.

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

Kafka or Redis publishing/storage failure response: `503 Service Unavailable`

## Completed Features

- Feature 1: Project setup
- Feature 2: URL ingestion API
- Feature 3: URL normalization
- Feature 4: SHA-256 URL hash generation
- Feature 5: Redis URL deduplication
- Feature 6: URL priority assignment
- Feature 7: Kafka publishing

## URL Frontier Flow

```text
Client
  -> UrlController
  -> UrlFrontierService
  -> UrlNormalizer
  -> Sha256UrlHasher
  -> VisitedUrlRepository
  -> UrlPriorityAssigner
  -> UrlTask
  -> UrlTaskProducer
  -> Kafka
  -> url-topic
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

V1 uses a fixed priority because the service does not yet have enough information to rank URLs meaningfully. Future versions may use signals such as seed URLs, page importance, freshness, domain policies, link signals, and crawl frequency.

## Feature 7: Kafka Publishing

Accepted URLs are published to Kafka topic `url-topic` only after Redis confirms the URL is new.

### UrlTask message

```json
{
  "schemaVersion": 1,
  "url": "https://spring.io",
  "urlHash": "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28",
  "priority": 5,
  "discoveredAt": "2026-08-11T18:30:00Z"
}
```

Message rules:

- `schemaVersion` starts at `1` for future schema evolution.
- `url` is the normalized URL.
- `urlHash` is the Kafka message key.
- `priority` is the backend-assigned crawl priority.
- `discoveredAt` is the UTC timestamp already used in the request flow.

### Topic configuration

Topic creation is infrastructure configuration, not business logic.

- Topic: `url-topic`
- Partitions: `1`
- Replication factor: `1`
- Creation approach: Spring `NewTopic` bean during application startup

### Producer acknowledgement

`UrlTaskProducer` does not treat `kafkaTemplate.send(...)` as immediate success. It waits for the broker acknowledgement with a bounded timeout before the request is considered successfully queued.

- Kafka key: `urlHash`
- Publish timeout: `3s`
- Success log: `KAFKA_PUBLISH_SUCCESS`
- Failure log: `KAFKA_PUBLISH_FAILURE`

### Retry behavior

Retries are delegated to the Kafka producer configuration instead of a custom retry loop.

- Retries: `3`
- Retry backoff: `200ms`
- Request timeout: `2000ms`
- Delivery timeout: `5000ms`
- Max block: `3000ms`

This keeps retries bounded and prevents broken Kafka connectivity from blocking the HTTP request indefinitely.

### Failure behavior

- Redis failure: return `503`, do not attempt Kafka publishing.
- Duplicate URL: return `409`, do not assign priority, do not create `UrlTask`, do not publish to Kafka.
- Kafka publish failure after Redis acceptance: return `503`.
- Redis key is not deleted when Kafka publish fails.

That last rule is an intentional V1 limitation. Redis remains the deduplication authority even if Kafka publish fails. A future hardening phase may introduce an Outbox pattern.

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

Start the shared local infrastructure from `infrastructure/docker`.

Kafka and ZooKeeper for Feature 7:

```bash
docker compose up -d zookeeper kafka
docker ps
```

Redis remains required for Feature 5:

```bash
docker compose up -d redis
docker ps
```

Start all three if needed:

```bash
docker compose up -d zookeeper kafka redis
```

## Topic Verification

Describe the topic from `infrastructure/docker`:

```bash
docker exec kafka kafka-topics --bootstrap-server kafka:29092 --describe --topic url-topic
```

Consume queued messages and print keys:

```bash
docker exec kafka kafka-console-consumer --bootstrap-server kafka:29092 --topic url-topic --from-beginning --property print.key=true --property key.separator=" | "
```

## Environment Variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP port for the service. |
| `REDIS_HOST` | `localhost` | Redis hostname. |
| `REDIS_PORT` | `6379` | Redis port. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers for local IDE execution. |
| `URL_TASK_TOPIC` | `url-topic` | Kafka topic used for accepted URL tasks. |
| `URL_TASK_PUBLISH_TIMEOUT` | `3s` | Maximum time to wait for Kafka acknowledgement. |
| `KAFKA_PRODUCER_RETRIES` | `3` | Bounded Kafka producer retry count. |
| `KAFKA_PRODUCER_RETRY_BACKOFF_MS` | `200` | Delay between producer retries. |
| `KAFKA_PRODUCER_REQUEST_TIMEOUT_MS` | `2000` | Per-request broker timeout. |
| `KAFKA_PRODUCER_DELIVERY_TIMEOUT_MS` | `5000` | Overall producer delivery timeout. |
| `KAFKA_PRODUCER_MAX_BLOCK_MS` | `3000` | Maximum block when producer metadata is unavailable. |
| `LOGGING_LEVEL_ROOT` | `INFO` | Root logging level. |
| `LOGGING_LEVEL_APPLICATION` | `INFO` | Logging level for application classes. |

## Testing

- Unit tests mock Redis and Kafka collaborators.
- HTTP/application tests use mocked infrastructure for deterministic responses.
- Redis integration tests are explicitly tagged as `integration`.
- No Kafka integration test or Testcontainers setup is included in the default suite.

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
2. Kafka consumer-side crawl coordination.
3. Richer priority strategies based on crawl signals.
4. Scheduling, politeness controls, and stronger delivery guarantees.
