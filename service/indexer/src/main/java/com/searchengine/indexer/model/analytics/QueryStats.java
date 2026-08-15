package com.searchengine.indexer.model.analytics;

import java.time.Instant;

public record QueryStats(
        String query,
        long count,
        long zeroResultCount,
        long totalHits,
        Instant lastSeenAt
) {}
