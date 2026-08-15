package com.searchengine.indexer.model.analytics;

import java.util.List;

public record SearchAnalyticsSnapshot(
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        long validationErrors,
        long zeroResultSearches,
        long resultfulSearches,
        long cacheHits,
        long cacheMisses,
        double averageLatencyMs,
        long maxLatencyMs,
        long trackedQueries,
        List<QueryStats> topQueries
) {}
