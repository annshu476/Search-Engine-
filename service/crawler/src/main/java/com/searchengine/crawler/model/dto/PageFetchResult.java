package com.searchengine.crawler.model.dto;

import java.time.Instant;

/**
 * Immutable result of an HTTP page fetch attempt.
 */
public record PageFetchResult(
        String requestedUrl,
        String finalUrl,
        int statusCode,
        String contentType,
        String body,
        Instant fetchedAt,
        boolean success,
        String failureReason
) {
    public static PageFetchResult success(String requestedUrl, String finalUrl, int statusCode, String contentType, String body, Instant fetchedAt) {
        return new PageFetchResult(requestedUrl, finalUrl, statusCode, contentType, body, fetchedAt, true, null);
    }

    public static PageFetchResult failure(String requestedUrl, String finalUrl, int statusCode, String contentType, String failureReason, Instant fetchedAt) {
        return new PageFetchResult(requestedUrl, finalUrl != null ? finalUrl : requestedUrl, statusCode, contentType, null, fetchedAt, false, failureReason);
    }
}
