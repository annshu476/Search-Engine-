# Distributed Search Engine Microservice Platform

A production-oriented, fault-tolerant, event-driven web crawler, content processor, and search engine platform built with **Java 21**, **Spring Boot 3.5.0**, **Apache Kafka**, **Elasticsearch 8.11**, **Redis 7**, **Caffeine**, and **Prometheus**.

---

## 1. System Architecture

```
                               [ External Web Pages ]
                                         ▲
                                         │ (HTTP / Robots.txt)
                                         ▼
                              ┌─────────────────────┐
                              │   Crawler Service   │  (Port 8081)
                              └──────────┬──────────┘
                                         │
                                         │ Produces RawHtmlDocument
                                         ▼
                              ┌─────────────────────┐
                              │   raw-html-topic    │  (Kafka)
                              └──────────┬──────────┘
                                         │
                                         │ Consumes RawHtmlDocument
                                         ▼
                              ┌─────────────────────┐
                              │ Content Processor   │  (Port 8082)
                              └──────────┬──────────┘
                                         │
                                         │ Produces SearchDocument
                                         ▼
                              ┌─────────────────────┐
                              │search-document-topic│  (Kafka)
                              └──────────┬──────────┘
                                         │
                                         │ Consumes SearchDocument
                                         ▼
                              ┌─────────────────────┐
       ┌─────────────────────►│   Indexer Service   │  (Port 8083)
       │                      └────┬────────────┬───┘
       │                           │            │
       │ Invalidation Pub/Sub      │ Reads/     │ Reads/
       │ & L2 Distributed Cache    │ Writes     │ Queries
       ▼                           ▼            ▼
 ┌──────────┐              ┌──────────────┐   ┌──────────────────────────────┐
 │  Redis   │  (Port 6379) │Elasticsearch │   │   Client / Search Consumers   │
 └──────────┘              │(Port 9200)   │   │ GET /api/search?q={query}    │
                           └──────────────┘   └──────────────────────────────┘
```

---

## 2. Architectural Rationale & Design Decisions

### Why Separate 3 Microservices?
- **Crawler Service**: Network I/O bound. Manages HTTP connections, domain rate limits, and `robots.txt` compliance without blocking document parsing.
- **Content Processor Service**: CPU bound. Executes HTML DOM cleaning via JSoup, canonical URL resolution, structural metadata extraction (title, headings, meta descriptions), and word count computation.
- **Indexer Service**: Storage and query bound. Handles Elasticsearch document indexing, multi-tier L1/L2 caching, distributed rate limiting, and public Search REST APIs.

### Why Retain BM25 Scoring in Indexer (No Separate Ranker)?
Elasticsearch is built on Apache Lucene with SIMD acceleration and native inverted index term statistics ($tf-idf$, document length normalization). Co-locating field weighting (`title^4.0`, `headings^3.0`), phrase boosting, and BM25 scoring inside the Indexer eliminates 20–50ms of network RPC latency per query.

### Data Guarantees & Idempotency
- **At-Least-Once Delivery**: All Kafka consumers use `ack-mode: manual_immediate`. Offsets are committed only after successful processing.
- **Idempotent Storage**: `urlHash = SHA256(canonicalUrl)` is assigned as the Elasticsearch document `_id`. Duplicate or replayed Kafka events update existing records in place without producing duplicate documents.

---

## 3. Technology Stack

- **Language & Framework**: Java 21 (JDK), Spring Boot 3.5.0, Spring Kafka, Spring Data Redis
- **Messaging & Event Streaming**: Apache Kafka 7.5, Zookeeper
- **Search Engine & Inverted Index**: Elasticsearch 8.11 (Java API Client)
- **Caching**: L1 Caffeine (In-Memory), L2 Redis 7 (Distributed JSON Cache & Pub/Sub)
- **HTML Parsing**: JSoup 1.18
- **Metrics & Observability**: Micrometer, Prometheus, Spring Boot Actuator
- **Container Orchestration**: Docker, Docker Compose

---

## 4. Infrastructure & Service Ports

| Container / Service | Image / Port(s) | Role & Health Check Endpoint |
| :--- | :--- | :--- |
| **Zookeeper** | `confluentinc/cp-zookeeper:7.5.0` (2181) | Kafka coordination (`nc -z localhost 2181`) |
| **Kafka** | `confluentinc/cp-kafka:7.5.0` (9092) | Event broker (`nc -z localhost 9092`) |
| **Redis** | `redis:7-alpine` (6379) | Distributed L2 cache & Rate Limiting (`redis-cli ping`) |
| **Elasticsearch** | `docker.elastic.co/elasticsearch/elasticsearch:8.11.0` (9200) | Search storage & BM25 engine (`_cluster/health`) |
| **Prometheus** | `prom/prometheus:latest` (9090) | Metrics collector (`http://localhost:9090`) |
| **Crawler** | `service/crawler` (8081) | Web page ingestion (`http://localhost:8081/actuator/health`) |
| **Content Processor**| `service/content-processor` (8082) | Text parsing (`http://localhost:8082/actuator/health`) |
| **Indexer** | `service/indexer` (8083) | Search API & indexing (`http://localhost:8083/actuator/health`) |

---

## 5. Search Capabilities

- **BM25 Relevance & Field Weighting**: Multi-match field weighting (`title^4.0`, `headings^3.0`, `metaDescription^2.0`, `bodyText^1.0`).
- **Phrase Matching & Boosting**: Exact phrase matching and title phrase boosting (`titlePhraseBoost = 4.0`).
- **Fuzzy Search & Typo Tolerance**: Configurable typo tolerance (`AUTO` fuzziness).
- **HTML Highlighting**: Dynamic snippet highlighting (`<em>...</em>`).
- **Autocomplete & Suggestions**: Real-time prefix suggestions via `title.suggest` and `headings.suggest`.
- **Search Enhancements**: Synonym expansion (`java` $\rightarrow$ `jdk`) and zero-result spell correction fallback.
- **Search Security & Abuse Protection**: Token bucket rate limiting, query length limits (max 200 chars), cost evaluation, and admin token authorization (`X-Admin-Token`).
- **Search Evaluation**: Built-in relevance evaluation framework computing MRR, Precision@K, Recall@K, and Hit@K.

---

## 6. REST API Reference

### Public Search APIs
- `GET /api/search?q={query}&language={lang}&contentType={type}&statusCode={status}&fromDate={from}&toDate={to}&page={page}&size={size}&sort={sort}`
  - *Returns*: `SearchResponse` JSON with hits, highlights, and pagination metadata.
- `GET /api/search/suggest?q={prefix}`
  - *Returns*: Autocomplete suggestion list.

### Analytics & Insights APIs
- `GET /api/search/analytics/summary`: Aggregate search counts, latency, and cache hit rates.
- `GET /api/search/analytics/top-queries`: Top search queries.
- `GET /api/search/analytics/zero-results`: Top zero-result queries.

### Protected Admin Operations
- `POST /api/search/analytics/reset` (`Header: X-Admin-Token: test-admin-secret-token`)
- `POST /api/search/evaluation/run` (`Header: X-Admin-Token: test-admin-secret-token`)

---

## 7. Local Setup & Execution

### Prerequisites
- Java 21 JDK (`java -version`)
- Docker & Docker Compose (`docker compose version`)

### 1. Build & Launch Microservices Stack
```powershell
cd infrastructure/docker
docker compose up -d --build
```

### 2. Verify Health Status
```powershell
curl http://localhost:8081/actuator/health  # Crawler
curl http://localhost:8082/actuator/health  # Content Processor
curl http://localhost:8083/actuator/health  # Indexer
```

### 3. Stop System & Clean Volumes
```powershell
cd infrastructure/docker
docker compose down -v
```

---

## 8. Running Test Suites

```powershell
# Crawler Unit Tests (96 tests)
cd service/crawler
.\mvnw.cmd clean test

# Content Processor Unit Tests (43 tests)
cd service/content-processor
.\mvnw.cmd clean test

# Indexer Unit Tests (158 tests)
cd service/indexer
.\mvnw.cmd clean test

# Elasticsearch, Redis & Pipeline Integration Tests
cd service/indexer
.\mvnw.cmd test -Pelasticsearch-integration
```

---

## 9. Future Extensions

- API Gateway & Authentication Service (OAuth2 / JWT).
- Cloud Deployment Automation (Kubernetes / Helm Charts / Terraform).
- Machine Learning Re-Ranking (PyTorch / Cross-Encoder LTR model sidecar).
