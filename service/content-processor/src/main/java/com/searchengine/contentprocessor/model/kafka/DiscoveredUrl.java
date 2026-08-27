package com.searchengine.contentprocessor.model.kafka;

import java.time.Instant;

/**
 * Kafka event representing a newly discovered hyperlink extracted from a crawled HTML document.
 */
public record DiscoveredUrl(
        String url,
        String sourceUrl,
        Instant discoveredAt
) {}
