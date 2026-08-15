package com.searchengine.indexer.model.analytics;

public record SearchQueryStats(
        String queryHash,
        String query,
        long searchCount,
        long successfulCount,
        long zeroResultCount,
        double averageDurationMs,
        long minDurationMs,
        long maxDurationMs,
        double averageResultCount,
        long totalResultCount,
        long cacheHitCount,
        long correctionAttemptCount,
        long correctionAppliedCount,
        long synonymExpansionCount
) {}
