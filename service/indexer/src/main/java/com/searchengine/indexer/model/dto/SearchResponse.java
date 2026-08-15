package com.searchengine.indexer.model.dto;

import java.util.List;

public record SearchResponse(
    String query,
    String correctedQuery,
    long totalHits,
    int page,
    int size,
    int totalPages,
    String sort,
    List<SearchResult> results
) {
    public SearchResponse(String query, long totalHits, int page, int size, int totalPages, String sort, List<SearchResult> results) {
        this(query, null, totalHits, page, size, totalPages, sort, results);
    }
}
