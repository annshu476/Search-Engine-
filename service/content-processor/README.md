# Content Processor Service

The **Content Processor** service transforms raw HTML documents fetched by the Crawler into structured search documents.

---

## Service Specifications

| Specification | Value |
|---|---|
| Service Name | `content-processor` |
| Default HTTP Port | `8082` (`${SERVER_PORT:8082}`) |
| Input Topic | `raw-html-topic` (`${RAW_HTML_TOPIC:raw-html-topic}`) |
| Output Topic | `search-document-topic` (`${SEARCH_DOCUMENT_TOPIC:search-document-topic}`) |
| Consumer Group | `content-processor` (`${KAFKA_CONSUMER_GROUP:content-processor}`) |
| Acknowledgment | `manual_immediate` (ACK on success; no ACK on processing failure) |

---

## Feature 2: HTML Parsing & SearchDocument Creation

Feature 2 implements DOM parsing using **Jsoup** (`org.jsoup:jsoup:1.18.3`) to transform raw HTML into populated `SearchDocument` V1 instances.

> [!NOTE]
> **Current Limitations:**
> - `SearchDocument` records are created in memory; publishing to `search-document-topic` is **NOT** active yet (handled in Feature 3).
> - Elasticsearch connection and search indexing are **NOT** implemented yet.

### HTML Parsing & Extraction Rules

| Field | Source / Rule |
|---|---|
| `url` | Copied from `RawHtmlDocument.url` |
| `canonicalUrl` | Extracted from `<link rel="canonical" href="...">`. Relative URLs resolved against `finalUrl`. Absolute URLs preserved. If missing/blank, falls back to `RawHtmlDocument.finalUrl` |
| `urlHash` | Copied from `RawHtmlDocument.urlHash` |
| `title` | Text content of `<title>` tag (trimmed). Returns `null` if missing or empty |
| `metaDescription` | Content attribute of `<meta name="description" content="...">` (case-insensitive name check). Returns `null` if missing or empty |
| `headings` | Visible text of all `h1, h2, h3, h4, h5, h6` elements preserved in document order as `List<String>`. Empty headings ignored |
| `bodyText` | Text from `<body>` with `<script>`, `<style>`, `<noscript>`, and `<template>` elements stripped prior to extraction. Whitespace normalized |
| `wordCount` | Count of non-empty tokens obtained by splitting `bodyText.trim()` on `\\s+`. Returns `0` if bodyText is empty |
| `language` | Priority 1: `<html lang="...">` attribute. Priority 2: `<meta http-equiv="content-language" content="...">`. Returns `null` if unstated |
| `statusCode` | Copied from `RawHtmlDocument.statusCode` |
| `contentType` | Copied from `RawHtmlDocument.contentType` |
| `fetchedAt` | Copied from `RawHtmlDocument.fetchedAt` |
| `indexedAt` | Timestamp (`Instant.now()`) when Content Processor constructed the `SearchDocument` |

### Edge Case Handling

- **Malformed HTML**: Parsed gracefully by Jsoup without throwing parsing exceptions.
- **Empty / Null HTML**: Handled safely without NPE. Produces a `SearchDocument` with `title=null`, `metaDescription=null`, `headings=[]`, `bodyText=""`, `wordCount=0`, `canonicalUrl=finalUrl`, metadata copied, and current `indexedAt`.

---

## Message Contracts

### Input Contract: `RawHtmlDocument` (`raw-html-topic`)

```java
public record RawHtmlDocument(
    int schemaVersion,
    String url,
    String finalUrl,
    String urlHash,
    int statusCode,
    String contentType,
    String html,
    Instant fetchedAt
) {}
```

### Output Contract: `SearchDocument` (`search-document-topic` - V1 Locked)

```java
public record SearchDocument(
    String url,
    String canonicalUrl,
    String urlHash,
    String title,
    String metaDescription,
    List<String> headings,
    String bodyText,
    String language,
    Integer wordCount,
    Integer statusCode,
    String contentType,
    Instant fetchedAt,
    Instant indexedAt
) {}
```

---

## Actuator & Monitoring

- Health check: `http://localhost:8082/actuator/health`
- Info: `http://localhost:8082/actuator/info`
- Prometheus metrics: `http://localhost:8082/actuator/prometheus`

---

## Build & Test Commands

### Run Unit and Integration Tests

```bash
./mvnw clean test
```

Windows:
```cmd
.\mvnw.cmd clean test
```

### Run Service Locally

```bash
./mvnw spring-boot:run
```

---

## Docker & Kafka Verification

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d zookeeper kafka redis
docker exec kafka kafka-topics --bootstrap-server kafka:29092 --list
```
