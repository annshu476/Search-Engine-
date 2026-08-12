package com.searchengine.crawler.model.kafka;

import java.time.Instant;

/**
 * Kafka message model representing a URL crawl task published by URL Frontier.
 */
public record UrlTask(
        int schemaVersion,
        String url,
        String urlHash,
        int priority,
        Instant discoveredAt
) {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
}
