package com.searchengine.urlfrontier.model.kafka;

import java.time.Instant;

/** Kafka message for a newly accepted URL crawl task. */
public record UrlTask(
        int schemaVersion,
        String url,
        String urlHash,
        int priority,
        Instant discoveredAt
) {
    public static final int SCHEMA_VERSION = 1;
}
