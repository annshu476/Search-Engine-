# Indexer Service

The **Indexer Service** is Service 3 in the Search Engine pipeline. Its primary role is to consume processed document content from Kafka (`search-document-topic`), index the documents into Elasticsearch using explicit mappings, and expose REST Search APIs for querying indexed content with field-weighted relevance scoring, phrase matching, advanced query syntax & operators, production-hardened resilience, response optimization, in-memory L1 Caffeine & L2 Redis distributed caching, Redis Pub/Sub cache invalidation coordination, distributed Redis rate limiting, search analytics & query tracking, spell correction & synonym-aware search, search quality relevance testing, search analytics & query insights, search API security & rate limiting, fuzzy typo tolerance, search result highlighting, search suggestions / autocomplete, advanced search filters, pagination, and sorting support.

---

## Architecture Position

```
URL Frontier
    ↓
url-topic
    ↓
Crawler
    ↓
raw-html-topic
    ↓
Content Processor / Parser
    ↓
search-document-topic
    ↓
SearchDocumentConsumer
    ↓
IndexerService
    ↓
SearchDocumentIndexer ──(Clear L1 & L2 Cache + Publish Redis Pub/Sub Event)──> Redis Pub/Sub (search-engine:cache-invalidation)
    ↓                                                                                       │
Elasticsearch (search-documents)                                                           ▼
    ├── RequestCorrelationFilter & SearchSecurityFilter (X-Request-Id, Distributed Redis Rate Limiting, Admin Token Auth, Query Cost Score)
    ├── SearchService (Source Filtering + L1 Caffeine / L2 Redis Cache + SearchAnalyticsService + Spell Correction) → GET /api/search?q={query}&language={lang}&contentType={type}&statusCode={status}&fromDate={from}&toDate={to}&page={page}&size={size}&sort={sort}
    ├── SearchSuggestionService                                                                                        → GET /api/search/suggest?q={prefix}
    ├── SearchEvaluationController                                                                                     → POST /api/search/evaluation/run (X-Admin-Token)
    └── SearchAnalyticsController                                                                                      → GET /api/search/analytics/summary
                                                                                                                         GET /api/search/analytics/top-queries
                                                                                                                         GET /api/search/analytics/zero-results
                                                                                                                         POST /api/search/analytics/reset (X-Admin-Token)
```

---

## Features Implemented

### Feature 1 — Indexer Foundation
- Spring Boot 3.5.0 application foundation running on Java 21 on port `8083`.
- Official Elasticsearch Java API Client configuration (`co.elastic.clients:elasticsearch-java`).
- Externalized configuration via `application.yml` supporting environment variable overrides.
- Actuator health check integration (`/actuator/health`) with Elasticsearch health indicator (`ElasticsearchHealthIndicator`).
- Prometheus metrics foundation (`/actuator/prometheus`).

### Feature 2 — Kafka Consumer Foundation
- Kafka Consumer configuration targeting topic `search-document-topic` with consumer group `indexer`.
- Deserialization of incoming JSON payloads into `SearchDocument` with `spring.json.use.type.headers=false`.
- `SearchDocumentValidator` enforcing contract rules and HTTP status ranges (100–599).
- `SearchDocumentConsumer` listening on Kafka with manual immediate acknowledgement (`ACK`).
- Non-retryable validation error classification via `SearchDocumentValidationException`.
- Embedded Kafka integration test (`SearchDocumentConsumerIntegrationTest`).

### Feature 3 — Elasticsearch Index & Document Indexing
- Explicit Elasticsearch mapping for `search-documents` index (keyword for identities, text for searchable content, integer/date for metrics and timestamps).
- Document ID strategy using `SearchDocument.urlHash()` as `_id` for idempotent indexing.
- Startup index initialization via `SearchDocumentIndexInitializer`.
- Document mapping via `SearchDocumentMapper`.
- Elasticsearch document indexing via `SearchDocumentIndexer`.

### Feature 4 — Basic Search API
- Exposes `GET /api/search?q={query}` REST endpoint.
- Executes full-text multi-match queries across `title`, `metaDescription`, `headings`, and `bodyText`.
- Returns clean `SearchResponse` containing `query`, `totalHits`, and `List<SearchResult>` (excluding heavy `bodyText`).

### Feature 5 — Search API Pagination and Sorting
- Adds zero-based pagination via `page` (default 0, >= 0) and `size` (default 10, between 1 and 50).
- Calculates `totalPages` via `ceil(totalHits / size)`.
- Adds controlled sorting options:
  - `relevance` (default): Elasticsearch relevance score descending (`_score` desc).
  - `newest`: Sort by `indexedAt` descending with secondary deterministic `urlHash` (`_id`) ascending tie-breaker.

### Feature 6 — Search Relevance Improvements
- Replaces equal-weight multi-match query with a field-weighted strategy:
  - **`title^4.0`**: Highest importance.
  - **`headings^3.0`**: High importance.
  - **`metaDescription^2.0`**: Medium importance.
  - **`bodyText^1.0`**: Baseline importance.
- Configures `type = best_fields` and `minimum_should_match = "1"`.
- Trims whitespace from user queries before execution while preserving internal term spacing.

### Feature 7 — Fuzzy Search / Typo Tolerance
- Extends multi-match query with configurable Elasticsearch `fuzziness` (default `AUTO`).
- Allows minor typos in user queries (e.g. `"sprng boot"` matches `"Spring Boot"`).

### Feature 8 — Search Result Highlighting
- Adds Elasticsearch highlighting configuration to search requests for searchable fields.
- Encloses matched terms in configurable HTML tags (default `<em>` and `</em>`).
- Maps highlight fragments directly into `SearchResult.highlights`.

### Feature 9 — Search Suggestions / Autocomplete
- Exposes `GET /api/search/suggest?q={prefix}` endpoint.
- Returns `SearchSuggestionResponse` DTO containing `query` and `suggestions` list (`List<String>`).

### Feature 10 — Advanced Search Filtering
- Adds search filters: `language`, `contentType`, `statusCode`, `fromDate`, `toDate`.
- Evaluates filters inside `bool.filter` non-scoring context.

### Feature 11 — Phrase Matching & Advanced Relevance Weighting
- Introduces phrase matching boost (`phraseBoost = 2.0`) and title phrase boost (`titlePhraseBoost = 4.0`).

### Feature 12 — Advanced Search Query Syntax & Operators
- Adds safe query parsing via `SearchQueryParser`: exact phrase (`"..."`), required (`+term`), excluded (`-term`).

### Feature 13 — Search Production Hardening & Resilience
- Circuit breaker / query timeout controls (`indexer.search.timeout = 3s`) and deep pagination bounds (`10000`).

### Feature 14 — Search Performance, Caching & Response Optimization
- In-memory Caffeine caching (`SearchCacheService`) for search queries.

### Feature 15 — Search Analytics & Query Tracking
- Thread-safe query statistics tracking (`SearchAnalyticsService`).

### Feature 16 — Search Spell Correction & Synonym-Aware Search
- Query-time synonym expansion (`SearchQueryEnhancer`) and zero-result fallback spell correction (`SearchSpellCorrectionService`).

### Feature 17 — Search Quality, Ranking Evaluation & Relevance Testing
- Repeatable relevance evaluation framework (`SearchEvaluationService`) exposing `POST /api/search/evaluation/run`.

### Feature 18 — Search Analytics & Query Insights
- Observability-only search analytics layer (`SearchAnalyticsService`) with SHA-256 hashed queries and PII protection.

### Feature 19 — Search API Security, Rate Limiting & Abuse Protection
- Endpoint-specific rate limits, `X-Request-Id` correlation, admin token security (`X-Admin-Token`), query cost protection (`SearchRequestCostEvaluator`), and security headers.

### Feature 20 — Distributed Redis Rate Limiting & Search Cache Coordination
- **Distributed Redis Rate Limiting** (`RedisSearchRateLimiter`): Atomic Redis counter operations (`INCR` + `EXPIRE`) sharing endpoint limits across multiple Indexer instances. SHA-256 client key hashing.
- **Fail-Open Resilience**: If Redis is unavailable and `fail-open: true`, logs `REDIS_CONNECTION_FAILED`, increments fallback metrics (`search.rate_limit.redis.fallback`), and seamlessly falls back to local Caffeine rate limiting. If `fail-open: false`, returns `HTTP 503 Service Unavailable`.
- **L1 Caffeine / L2 Redis Search Cache Architecture** (`SearchCacheService` & `RedisSearchCache`):
  - **L1 Cache**: Local Caffeine in-memory cache (ultra-fast).
  - **L2 Cache**: Redis distributed cache (`search-engine:search:<hash>`) using Jackson JSON serialization and SHA-256 `SearchCacheKey` hashes.
  - **Read Flow**: Request $\rightarrow$ L1 Caffeine hit (return) $\rightarrow$ L1 Caffeine miss $\rightarrow$ L2 Redis hit (return & populate L1) $\rightarrow$ L2 Redis miss $\rightarrow$ Elasticsearch execution $\rightarrow$ store in L2 Redis & L1 Caffeine.
- **Redis Pub/Sub Cache Invalidation Coordination** (`SearchCacheInvalidationPublisher` & `SearchCacheInvalidationSubscriber`):
  - Successful Elasticsearch indexing clears local L1 Caffeine cache, clears L2 Redis cache keys (`search-engine:search:*`), and publishes a Pub/Sub invalidation message to channel `search-engine:cache-invalidation`.
  - All Indexer instances listening on the channel invalidate their local L1 Caffeine cache instantly.
  - Pub/Sub or Redis failure clears local L1 cache and never breaks Elasticsearch document indexing.
- **Redis Health Indicator** (`RedisHealthIndicator`): Exposes Redis connection state on `/actuator/health`. When `fail-open: true`, Redis downtime reports `DEGRADED` detail without bringing down application health.

*Scope Clarification*: Feature 20 does NOT introduce Redis Streams, distributed locks, API Gateway, OAuth/JWT, user authentication, Kubernetes deployment, service mesh, or Feature 21 functionality.

---

## Configuration Properties (`application.yml`)

```yaml
indexer:
  search:
    redis:
      enabled: ${SEARCH_REDIS_ENABLED:true}
      host: ${SEARCH_REDIS_HOST:localhost}
      port: ${SEARCH_REDIS_PORT:6379}
      password: ${SEARCH_REDIS_PASSWORD:}
      timeout: ${SEARCH_REDIS_TIMEOUT:500ms}
      key-prefix: ${SEARCH_REDIS_KEY_PREFIX:search-engine:}
      fail-open: ${SEARCH_REDIS_FAIL_OPEN:true}
    rate-limit:
      enabled: ${SEARCH_RATE_LIMIT_ENABLED:true}
      trust-forwarded-headers: false
      maximum-client-entries: 10000
      window: 1m
      max-cost-score: 100
      search:
        requests-per-minute: 60
      suggest:
        requests-per-minute: 120
      analytics:
        requests-per-minute: 10
      evaluation:
        requests-per-minute: 2
      analytics-reset:
        requests-per-minute: 1
    cache:
      enabled: true
      maximum-size: 1000
      ttl: 60s
    admin:
      enabled: true
      token: test-admin-secret-token
```

---

## Testing

Run the standard deterministic test suite across all services:
```powershell
# Crawler Service
cd service/crawler
.\mvnw.cmd clean test

# Content Processor Service
cd service/content-processor
.\mvnw.cmd clean test

# Indexer Service
cd service/indexer
.\mvnw.cmd clean test
```

Run Elasticsearch & Redis integration tests:
```powershell
cd service/indexer
.\mvnw.cmd test -Pelasticsearch-integration
```
