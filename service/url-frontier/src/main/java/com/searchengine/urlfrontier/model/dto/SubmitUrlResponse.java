package com.searchengine.urlfrontier.model.dto;

import java.time.Instant;

/** Response returned after a URL is accepted, normalized, and hashed. */
public record SubmitUrlResponse(
        boolean accepted,
        String originalUrl,
        String normalizedUrl,
        String urlHash,
        String message,
        Instant timestamp
) {
}
