package com.searchengine.indexer.model.dto;

import java.util.List;

public record SearchResponse(
    String query,
    long totalHits,
    List<SearchResult> results
) {}
