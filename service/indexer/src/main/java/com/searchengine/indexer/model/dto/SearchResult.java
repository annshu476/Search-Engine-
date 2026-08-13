package com.searchengine.indexer.model.dto;

public record SearchResult(
    String url,
    String canonicalUrl,
    String urlHash,
    String title,
    String metaDescription,
    String language,
    Integer wordCount,
    Integer statusCode
) {}
