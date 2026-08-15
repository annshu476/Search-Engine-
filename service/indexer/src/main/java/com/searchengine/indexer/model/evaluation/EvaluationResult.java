package com.searchengine.indexer.model.evaluation;

public record EvaluationResult(
        String query,
        String description,
        boolean hit,
        double reciprocalRank,
        long returnedHits,
        String topResultUrlHash
) {}
