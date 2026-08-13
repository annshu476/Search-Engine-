package com.searchengine.contentprocessor.model.kafka;

import java.time.Instant;
import java.util.List;

/**
 * Locked V1 Kafka contract for structured search documents destined for search-document-topic.
 */
public record SearchDocument(
        String url,
        String canonicalUrl,
        String urlHash,
        String title,
        String metaDescription,
        List<String> headings,
        String bodyText,
        String language,
        Integer wordCount,
        Integer statusCode,
        String contentType,
        Instant fetchedAt,
        Instant indexedAt
) {}
