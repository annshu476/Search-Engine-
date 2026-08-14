package com.searchengine.indexer.model.dto;

import java.util.List;
import java.util.Map;

public record SearchResult(
    String url,
    String canonicalUrl,
    String urlHash,
    String title,
    String metaDescription,
    String language,
    Integer wordCount,
    Integer statusCode,
    Map<String, List<String>> highlights
) {}
