package com.searchengine.indexer.model.analytics;

public record SearchAnalyticsSummary(
        long totalSearches,
        long successfulSearches,
        long failedSearches,
        long zeroResultSearches,
        double zeroResultRate,
        double averageDurationMs,
        double averageResultCount,
        double cacheHitRate,
        double fuzzySearchUsage,
        double synonymExpansionUsage,
        long spellCorrectionAttempts,
        long spellCorrectionApplied,
        long trackedQueries,
        String generatedAt
) {}
