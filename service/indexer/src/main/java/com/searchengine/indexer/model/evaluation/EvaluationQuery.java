package com.searchengine.indexer.model.evaluation;

import java.util.List;

public record EvaluationQuery(
        String query,
        List<String> expectedResultIds,
        List<String> preferredResultIds,
        List<String> excludedResultIds,
        int minimumExpectedHits,
        String description
) {}
