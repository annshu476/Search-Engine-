# Distributed Search Engine

A distributed web search engine built with Java, Spring Boot, Kafka, Redis, and Elasticsearch. It can crawl web pages, extract searchable content, discover new links automatically, index documents, and provide a search interface through a Next.js frontend.

---

## Demo

![Search Interface Screenshot](frontend/public/next.svg)

> *Submit a seed URL to begin crawling or search indexed pages directly.*

---

## What It Does

- **Url Submission**: Accepts web URLs to start crawling.
- **Deduplication**: Prevents crawling the same URL twice using Redis.
- **Web Crawling**: Fetches web pages while respecting `robots.txt` rules and domain rate limits.
- **Content Extraction**: Extracts page titles, headings, meta descriptions, and clean body text.
- **Link Discovery**: Automatically discovers hyperlinks on web pages and feeds them back into the crawl queue.
- **Asynchronous Pipeline**: Sends documents and discovered URLs through Kafka topics so services operate independently.
- **Full-Text Search**: Stores documents in Elasticsearch with BM25 relevance ranking, exact phrase matching, and fuzzy typo tolerance.
- **Autocomplete & Suggestions**: Provides real-time query suggestions as you type.
- **Filtering & Sorting**: Supports filtering by language, content type, date, and sorting by relevance or newest date.
- **Multi-Level Caching**: Uses local Caffeine cache and distributed Redis cache to speed up repeated queries.
- **Search Analytics**: Tracks search counts, top queries, and zero-result searches.
- **Search Interface**: Provides a web interface built with Next.js and Tailwind CSS.

---

## How It Works

### Main Crawl & Search Pipeline

```text
Seed URL (Frontend / REST API)
   ↓
URL Frontier (:8080)
   ↓
Kafka (url-topic)
   ↓
Crawler (:8081)
   ↓
Kafka (raw-html-topic)
   ↓
Content Processor (:8082)
   ├── SearchDocument ──► Kafka (search-document-topic) ──► Indexer (:8083) ──► Elasticsearch (:9200)
   │
   └── Discovered URLs
            ↓
      Kafka (discovered-urls-topic)
            ↓
       URL Frontier (:8080)  ──►  Redis SETNX  ──►  Kafka (url-topic)  ──►  Crawler (:8081)
```

### Search Flow

```text
Browser
   ↓
Next.js Frontend (:3000)
   ↓
Next.js API Proxy (/api/search)
   ↓
Indexer (:8083)
   ↓
Elasticsearch (:9200)
```

---

## Services

| Service | What it does | Port |
| :--- | :--- | :--- |
| **URL Frontier** | Manages seed URL submissions, normalizes URLs, deduplicates via Redis, and queues crawl tasks | `8080` |
| **Crawler** | Fetches web pages, obeys `robots.txt`, and enforces per-domain rate limits | `8081` |
| **Content Processor** | Cleans HTML, extracts structural metadata, and discovers new links | `8082` |
| **Indexer** | Stores documents in Elasticsearch, handles search queries, caching, analytics, and suggestions | `8083` |
| **Frontend** | Web search interface and seed submission UI | `3000` |

---

## Technology Stack

| Category | Technology |
| :--- | :--- |
| **Backend** | Java 21, Spring Boot 3.5.0, Spring Web, Spring Data Redis, Spring Kafka |
| **Messaging** | Apache Kafka 7.5, Zookeeper |
| **Search Engine** | Elasticsearch 8.15 |
| **Caching** | Redis 8, Caffeine |
| **Parsing** | Jsoup |
| **Frontend** | Next.js 15, React 19, TypeScript, Tailwind CSS |
| **Monitoring** | Prometheus, Spring Boot Actuator, Micrometer |
| **Testing** | JUnit 5, Spring Boot Test, Mockito, Vitest |
| **Containerization** | Docker, Docker Compose |

---

## Why These Technologies?

- **Kafka**: Connects the crawler, processor, frontier, and indexer so each service can work independently and handle traffic spikes asynchronously.
- **Elasticsearch**: Handles full-text search, relevance scoring (BM25), fuzzy typo matching, phrase boosting, and filtering.
- **Redis**: Provides fast O(1) URL deduplication (`SETNX`), rate limiting, and shared cache storage across instances.
- **Caffeine**: Provides a fast in-memory L1 cache before making network calls to Redis or Elasticsearch.
- **URL Frontier**: Separates URL scheduling and deduplication from the network-heavy crawler service.
- **Next.js**: Provides the user-facing search UI and proxies API requests to the backend with SSRF protection.

---

## Search Features

### Search Capabilities
- **Field-Weighted Relevance**: Searches across titles, headings, descriptions, and body text with title boosting.
- **Exact Phrase Matching**: Boosts results matching exact multi-word phrases.
- **Fuzzy Typo Tolerance**: Finds matching documents even with minor spelling mistakes.
- **Result Highlighting**: Wraps matching search terms in `<em>...</em>` tags.
- **Filters & Sorting**: Filter by language, status code, or date range, and sort by relevance or newest date.
- **Pagination**: Supports page navigation (`page` and `size` parameters).

### Advanced Query Syntax Examples
- `"spring boot"` — Exact phrase search.
- `spring +boot` — Must contain "boot".
- `spring -xml` — Must not contain "xml".
- `"spring boot" +java -xml` — Combined phrase, required term, and excluded term.

### Suggestions
As you type in the search bar, the suggestions endpoint (`/api/search/suggest`) returns prefix-matched autocomplete queries from indexed document titles and headings.

### Synonyms and Spell Correction
- **Synonyms**: Query term expansion (e.g. `jdk` $\rightarrow$ `java`).
- **Spell Correction**: Fallback query suggestions when a search yields zero hits (e.g. `sprng boot` $\rightarrow$ `spring boot`).

---

## Crawling Flow

1. User submits a seed URL (e.g. `https://spring.io`).
2. URL Frontier normalizes the URL and checks Redis (`SETNX`) to ensure it hasn't been seen before.
3. The frontier assigns a crawl task and publishes it to Kafka topic `url-topic`.
4. Crawler consumes the task, checks `robots.txt`, applies domain rate limiting, and fetches the HTML page.
5. Crawler publishes the raw HTML document to Kafka topic `raw-html-topic`.
6. Content Processor parses the HTML using Jsoup to extract title, headings, description, and body text.
7. Content Processor extracts `<a href="...">` links, resolves relative URLs, and sends discovered links to `discovered-urls-topic`.
8. URL Frontier consumes `discovered-urls-topic`, checks Redis deduplication, and queues new URLs back into `url-topic`.
9. Content Processor sends the cleaned `SearchDocument` to `search-document-topic`.
10. Indexer receives the document, indexes it into Elasticsearch, and the page becomes searchable.

---

## Idempotency and Duplicates

- **Document Identity**: The system computes `urlHash = SHA256(normalizedUrl)` for every document.
- **Elasticsearch Upsert**: Elasticsearch uses `urlHash` as the document `_id`. Re-indexing the same URL updates the existing document in place rather than creating a duplicate.
- **Redis Deduplication**: URL Frontier writes key `visited:<urlHash>` into Redis using `setIfAbsent` (`SETNX`) with a 7-day TTL. If the key exists, the URL is rejected with a `409 Conflict` (API) or dropped (Kafka).
- **Delivery Guarantee**: All Kafka consumers use manual offset acknowledgments (`ACK`), ensuring **at-least-once delivery with idempotent processing**.

---

## Caching

```text
User Request  ──►  L1 Caffeine Cache (In-Memory)  ──►  L2 Redis Cache (Distributed)  ──►  Elasticsearch
```

- **L1 Cache**: In-memory Caffeine cache on the Indexer service for sub-millisecond responses on repeated queries.
- **L2 Cache**: Redis cache shared across Indexer instances.
- **Cache Invalidation**: When a new document is indexed, Indexer broadcasts a pub/sub message to invalidate affected search cache entries.

---

## Reliability

- **Kafka Manual ACKs**: Offsets are committed only after successful message processing.
- **Retry Topics & DLT**: Crawler uses Spring Kafka non-blocking retry topics (`url-topic-retry-1000`, `url-topic-retry-2000`) and Dead Letter Topics (`url-topic-dlt`) for handling network failures.
- **Fail-Open Cache**: If Redis is temporarily down, search falls back directly to Elasticsearch.
- **Query Limits & Rate Limiting**: Search queries are limited to 200 characters and rate-limited to protect Elasticsearch from overload.
- **Health Checks**: Every service exposes Spring Boot Actuator health endpoints (`/actuator/health`).

---

## Security

- **SSRF Protection**: Next.js seed proxy (`/api/urls`) validates submitted URLs and blocks local loopback addresses (`localhost`, `127.0.0.1`, `::1`, `0.0.0.0`), cloud metadata IPs (`169.254.169.254`), and private IPv4 ranges (`10.*`, `172.16-31.*`, `192.168.*`).
- **Protected Admin Endpoints**: Sensitive operations (`POST /api/search/analytics/reset` and `POST /api/search/evaluation/run`) require the `X-Admin-Token` header.
- **Input Validation**: Request payloads are strictly validated using Spring Bean Validation (`@Valid`, `@NotBlank`, `@ValidUrl`).
- **Error Protection**: Stack traces are hidden in public API error responses.

---

## API Reference

### Seed Submission & Search APIs

| Method | Endpoint | Purpose | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/urls` | Submit a seed URL to URL Frontier (Direct) | Public |
| `POST` | `/api/urls` | Submit a seed URL via Next.js Proxy (SSRF Protected) | Public |
| `GET` | `/api/search` | Search indexed documents (`q`, `page`, `size`, `sort`, etc.) | Public |
| `GET` | `/api/search/suggest` | Get autocomplete query suggestions | Public |
| `GET` | `/api/search/analytics/summary` | View aggregate search statistics | Public |
| `GET` | `/api/search/analytics/top-queries` | View top search queries | Public |
| `GET` | `/api/search/analytics/zero-results` | View top zero-result queries | Public |
| `POST` | `/api/search/analytics/reset` | Reset analytics counters | Admin (`X-Admin-Token`) |
| `POST` | `/api/search/evaluation/run` | Run search relevance evaluation suite | Admin (`X-Admin-Token`) |

---

## Run Locally

### Prerequisites
- **Java 21 JDK** (`java -version`)
- **Node.js v18+ & npm** (`node -v`)
- **Docker & Docker Compose** (`docker compose version`)

---

### Recommended: Development Mode

Run infrastructure (Kafka, Zookeeper, Redis, Elasticsearch, Prometheus) in Docker, and run the Java microservices and frontend locally for fast testing and step-through debugging.

#### Step 1: Start Infrastructure in Docker
From the project root:
```powershell
cd infrastructure/docker
docker compose -f docker-compose.dev.yml up -d
```

#### Step 2: Start Application Services Locally
Open separate terminal windows for each service:

```powershell
# 1. URL Frontier (Port 8080)
cd service/url-frontier
.\mvnw.cmd spring-boot:run

# 2. Crawler (Port 8081)
cd service/crawler
.\mvnw.cmd spring-boot:run

# 3. Content Processor (Port 8082)
cd service/content-processor
.\mvnw.cmd spring-boot:run

# 4. Indexer (Port 8083)
cd service/indexer
.\mvnw.cmd spring-boot:run

# 5. Next.js Frontend (Port 3000)
cd frontend
npm run dev
```

Open `http://localhost:3000` in your browser.

---

### Alternative: Full Docker Mode

Compile and run the entire system inside Docker containers:
```powershell
cd infrastructure/docker
docker compose up -d --build
```

---

## Test Suites

Run the test suites across each microservice:

```powershell
# URL Frontier (34 tests)
cd service/url-frontier
.\mvnw.cmd test

# Crawler (96 tests)
cd service/crawler
.\mvnw.cmd test

# Content Processor (44 tests)
cd service/content-processor
.\mvnw.cmd test

# Indexer (158 tests)
cd service/indexer
.\mvnw.cmd test

# Next.js Frontend (5 tests)
cd frontend
npm test
```

---

## Known Limitations

- **Fixed Priority**: Crawl tasks currently use a fixed default priority (`5`). Dynamic page importance ranking can be added in future versions.
- **Deduplication Window**: Re-crawling visited URLs is disabled within the 7-day Redis deduplication window.
