# Indexer Service

The **Indexer Service** is Service 3 in the Search Engine pipeline. Its primary role is to consume processed document content from Kafka (`search-document-topic`), index the documents into Elasticsearch using explicit mappings, and expose a REST Search API for querying indexed content with field-weighted relevance scoring, fuzzy typo tolerance, search result highlighting, pagination, and sorting support.

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
SearchDocumentIndexer
    ↓
Elasticsearch (search-documents)
    ↓
SearchService
    ↓
SearchController (GET /api/search?q={query}&page={page}&size={size}&sort={sort})
```

---

## Features Implemented

### Feature 1 — Indexer Foundation
- Spring Boot 3.5.0 application foundation running on Java 21 on port `8083`.
- Official Elasticsearch Java API Client configuration (`co.elastic.clients:elasticsearch-java`).
- Externalized configuration via `application.yml` supporting environment variable overrides.
- Actuator health check integration (`/actuator/health`) with Elasticsearch connectivity health indicator (`ElasticsearchHealthIndicator`).
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
  - **`title^4.0`**: Highest importance (matches in title strongly boost document rank).
  - **`headings^3.0`**: High importance (matches in headings rank second).
  - **`metaDescription^2.0`**: Medium importance (matches in meta description rank third).
  - **`bodyText^1.0`**: Baseline importance (matches in body text).
- Configures `type = best_fields` (uses the score of the single best field for each document hit).
- Configures `minimum_should_match = "1"` to filter out documents with zero relevant field matches.
- Extracted query builder into `ElasticsearchSearchQueryBuilder`.
- Centralized relevance weights under `indexer.search.relevance.*` with fast-fail startup validation (`titleBoost > 0`, etc.).
- Normalizes search queries by trimming leading and trailing whitespace before execution while preserving internal spacing.

### Feature 7 — Fuzzy Search / Typo Tolerance
- Extends the multi-match query with configurable Elasticsearch `fuzziness` (default `AUTO`).
- Allows minor typos in user queries (e.g. `"sprng boot"` matches `"Spring Boot"`).
- Externalized configuration under `indexer.search.fuzzy.*`:
  - `enabled`: `true` (default).
  - `fuzziness`: `AUTO` (default).
- Fully compatible with Feature 6 field weighting (`title^4.0`, `headings^3.0`, `metaDescription^2.0`, `bodyText^1.0`) and sorting options.

### Feature 8 — Search Result Highlighting
- Adds Elasticsearch highlighting configuration to search requests for searchable fields (`title`, `headings`, `metaDescription`, `bodyText`).
- Encloses matched terms in configurable HTML tags (default `<em>` and `</em>`).
- Limits returned body text fragments to `fragment-size: 150` characters and `number-of-fragments: 2` to control payload size.
- Maps highlight fragments directly into `SearchResult.highlights` as `Map<String, List<String>>`.
- Returns `"highlights": {}` when no fragments are returned or when highlighting is disabled.
- Presentation metadata only: Highlighting does NOT alter Elasticsearch BM25 relevance scoring or result ordering.

> [!NOTE]
> **Intentionally NOT implemented yet**: Autocomplete, spell correction, synonyms, query suggestions, or semantic/vector search.

---

## Package Responsibilities

```
service/indexer/src/main/java/com/searchengine/indexer/
├── IndexerApplication.java                      # Spring Boot main application entry point
├── config/
│   ├── ElasticsearchConfig.java                # Bean definitions for RestClient, ElasticsearchTransport, and ElasticsearchClient
│   ├── ElasticsearchProperties.java            # Connection configuration properties prefixed with 'elasticsearch'
│   ├── IndexerElasticsearchProperties.java     # Index configuration properties prefixed with 'indexer.elasticsearch'
│   └── SearchProperties.java                   # Search API configuration properties (max-results, max-page-size, max-query-length, relevance, fuzzy, highlight)
├── consumer/
│   └── SearchDocumentConsumer.java             # Kafka listener for search-document-topic using manual ACK
├── controller/
│   └── SearchController.java                   # REST controller exposing GET /api/search?q={query}&page={page}&size={size}&sort={sort}
├── exception/
│   ├── ElasticsearchConfigurationException.java # Custom exception for invalid configuration/URLs
│   ├── ElasticsearchIndexingException.java     # Custom exception for Elasticsearch indexing failures
│   ├── SearchDocumentValidationException.java  # Exception thrown on SearchDocument contract validation failure
│   └── SearchQueryException.java               # Custom exception for search query execution failures
├── health/
│   └── ElasticsearchHealthIndicator.java        # Custom Spring Boot Actuator HealthIndicator for Elasticsearch ping
├── indexer/
│   └── SearchDocumentIndexer.java             # Indexes SearchDocument records into Elasticsearch using urlHash as _id
├── initializer/
│   └── SearchDocumentIndexInitializer.java     # Checks and creates search-documents index with explicit mapping on startup
├── mapper/
│   └── SearchDocumentMapper.java               # Maps SearchDocument records to Elasticsearch document Map
├── model/
│   ├── dto/
│   │   ├── SearchResponse.java                 # Search API response wrapper (query, totalHits, page, size, totalPages, sort, results)
│   │   └── SearchResult.java                   # Individual search hit DTO (excludes bodyText, includes highlights map)
│   └── kafka/
│       └── SearchDocument.java                 # Exact V1 SearchDocument record contract
├── search/
│   └── ElasticsearchSearchQueryBuilder.java   # Builds weighted best_fields multi-match queries with fuzziness and highlighting
├── service/
│   ├── IndexerService.java                     # Domain service orchestrating document indexing
│   └── SearchService.java                      # Service executing search queries with field weighting, fuzzy matching, highlighting, pagination, and sorting
└── validator/
    └── SearchDocumentValidator.java            # Validates SearchDocument required fields and bounds
```

---

## Server Port & Configuration

Default Port: `8083`

### Configuration Properties

| Property Key | Default Value | Environment Variable | Description |
|---|---|---|---|
| `server.port` | `8083` | `SERVER_PORT` | Service HTTP port |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | `KAFKA_BOOTSTRAP_SERVERS` | Kafka cluster bootstrap servers |
| `spring.kafka.consumer.group-id` | `indexer` | `KAFKA_CONSUMER_GROUP` | Consumer group ID |
| `indexer.kafka.search-document-topic` | `search-document-topic` | `SEARCH_DOCUMENT_TOPIC` | Topic name for incoming search documents |
| `indexer.elasticsearch.index-name` | `search-documents` | `ELASTICSEARCH_INDEX_NAME` | Elasticsearch index name |
| `indexer.search.max-results` | `10` | `SEARCH_MAX_RESULTS` | Default number of search results returned |
| `indexer.search.max-page-size` | `50` | `SEARCH_MAX_PAGE_SIZE` | Maximum allowed page size |
| `indexer.search.max-query-length` | `200` | `SEARCH_MAX_QUERY_LENGTH` | Maximum allowed characters in search query |
| `indexer.search.relevance.title-boost` | `4.0` | `SEARCH_TITLE_BOOST` | Field relevance boost for document `title` |
| `indexer.search.relevance.headings-boost` | `3.0` | `SEARCH_HEADINGS_BOOST` | Field relevance boost for document `headings` |
| `indexer.search.relevance.meta-description-boost` | `2.0` | `SEARCH_META_DESCRIPTION_BOOST` | Field relevance boost for `metaDescription` |
| `indexer.search.relevance.body-boost` | `1.0` | `SEARCH_BODY_BOOST` | Field relevance boost for document `bodyText` |
| `indexer.search.fuzzy.enabled` | `true` | `SEARCH_FUZZY_ENABLED` | Enables fuzzy matching for typo tolerance |
| `indexer.search.fuzzy.fuzziness` | `AUTO` | `SEARCH_FUZZINESS` | Elasticsearch fuzziness setting |
| `indexer.search.highlight.enabled` | `true` | `SEARCH_HIGHLIGHT_ENABLED` | Enables search result highlighting |
| `indexer.search.highlight.fragment-size` | `150` | `SEARCH_HIGHLIGHT_FRAGMENT_SIZE` | Maximum characters per highlight fragment |
| `indexer.search.highlight.number-of-fragments` | `2` | `SEARCH_HIGHLIGHT_NUMBER_OF_FRAGMENTS` | Maximum highlight fragments per field |
| `indexer.search.highlight.pre-tag` | `<em>` | `SEARCH_HIGHLIGHT_PRE_TAG` | Opening HTML highlight tag |
| `indexer.search.highlight.post-tag` | `</em>` | `SEARCH_HIGHLIGHT_POST_TAG` | Closing HTML highlight tag |
| `elasticsearch.url` | `http://localhost:9200` | `ELASTICSEARCH_URL` | Target Elasticsearch endpoint URL |

---

## Search API Specification

### Request Endpoint
```http
GET /api/search?q={query}&page={page}&size={size}&sort={sort}
```

### Example Request
```http
GET /api/search?q=spring%20boot&page=0&size=10&sort=relevance
```

### Example JSON Response
```json
{
  "query": "spring boot",
  "totalHits": 1,
  "page": 0,
  "size": 10,
  "totalPages": 1,
  "sort": "relevance",
  "results": [
    {
      "url": "https://spring.io",
      "canonicalUrl": "https://spring.io",
      "urlHash": "abc123hash",
      "title": "Spring Boot Framework",
      "metaDescription": "Build standalone Spring applications",
      "language": "en",
      "wordCount": 450,
      "statusCode": 200,
      "highlights": {
        "title": [
          "<em>Spring Boot</em> Framework"
        ],
        "bodyText": [
          "Build production-ready applications with <em>Spring Boot</em>."
        ]
      }
    }
  ]
}
```

---

## Testing

Run the standard deterministic test suite:
```powershell
.\mvnw.cmd clean test
```

Run real Elasticsearch integration tests:
```powershell
.\mvnw.cmd test -Pelasticsearch-integration
```

---

## Planned Next Features

- **Feature 9**: Kafka Retry Policy & Dead Letter Topic (DLT).
- **Feature 10**: End-to-End Search Pipeline Verification.
