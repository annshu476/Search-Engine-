package com.searchengine.crawler.model.kafka;

import java.time.Instant;

/**
 * Kafka message model representing a fetched raw HTML document published to raw-html-topic.
 */
public record RawHtmlDocument(
        int schemaVersion,
        String url,
        String finalUrl,
        String urlHash,
        int statusCode,
        String contentType,
        String html,
        Instant fetchedAt
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
