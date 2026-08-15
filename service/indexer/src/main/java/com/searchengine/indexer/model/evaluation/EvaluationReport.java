package com.searchengine.indexer.model.evaluation;

public record EvaluationReport(
        int totalQueries,
        int successfulQueries,
        int failedQueries,
        double precisionAt1,
        double precisionAt3,
        double precisionAt5,
        double precisionAt10,
        double recallAt1,
        double recallAt3,
        double recallAt5,
        double recallAt10,
        double mrr,
        double hitAt1,
        double hitAt3,
        double hitAt5,
        double hitAt10,
        double zeroResultRate,
        double averageResultCount,
        boolean passedThresholds,
        String evaluatedAt
) {}
