package com.searchengine.indexer.model.analytics;

import java.util.List;

public record ZeroResultsResponse(
        List<QueryZeroResultItem> queries
) {
    public record QueryZeroResultItem(
            String query,
            long count
    ) {}
}
