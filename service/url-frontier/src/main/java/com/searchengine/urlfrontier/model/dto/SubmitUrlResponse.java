package com.searchengine.urlfrontier.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Response returned after a URL is accepted, normalized, and hashed. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmitUrlResponse(
        boolean accepted,
        String originalUrl,
        String normalizedUrl,
        String urlHash,
        Integer priority,
        String message,
        Instant timestamp
) {
}
