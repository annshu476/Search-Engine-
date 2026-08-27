package com.searchengine.urlfrontier.model.kafka;

import java.time.Instant;

/**
 * Kafka event model representing a discovered URL received from Content Processor.
 */
public record DiscoveredUrl(
        String url,
        String sourceUrl,
        Instant discoveredAt
) {}
