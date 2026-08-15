package com.searchengine.indexer.model.analytics;

import java.time.Instant;

public record SearchAnalyticsEvent(
        Instant timestamp,
        String queryHash,
        String normalizedQuery,
        boolean successful,
        boolean zeroResults,
        int resultCount,
        long totalHits,
        long durationMs,
        int page,
        int size,
        String sort,
        boolean cacheHit,
        boolean fuzzyEnabled,
        boolean synonymExpansionUsed,
        boolean spellCorrectionAttempted,
        boolean spellCorrectionApplied,
        String correctedQueryHash,
        boolean filterUsage,
        boolean advancedSyntaxUsed
) {}
