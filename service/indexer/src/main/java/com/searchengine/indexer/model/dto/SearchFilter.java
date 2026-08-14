package com.searchengine.indexer.model.dto;

import java.time.Instant;

public record SearchFilter(
        String language,
        String contentType,
        Integer statusCode,
        Instant fromDate,
        Instant toDate
) {
    public boolean hasFilters() {
        return language != null || contentType != null || statusCode != null || fromDate != null || toDate != null;
    }
}
