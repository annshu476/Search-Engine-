package com.searchengine.indexer.model.search;

import java.time.Instant;

public record SearchCacheKey(
        String normalizedQuery,
        int page,
        int size,
        String sort,
        String language,
        String contentType,
        Integer statusCode,
        Instant fromDate,
        Instant toDate,
        String configVersion
) {}
