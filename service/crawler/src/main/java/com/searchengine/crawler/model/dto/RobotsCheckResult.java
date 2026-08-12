package com.searchengine.crawler.model.dto;

import java.time.Instant;

/**
 * Immutable outcome of evaluating robots.txt compliance for a target URL.
 */
public record RobotsCheckResult(
        String origin,
        boolean allowed,
        boolean robotsAvailable,
        long crawlDelayMs,
        String reason,
        Instant checkedAt
) {
    public static RobotsCheckResult allowed(String origin, long crawlDelayMs, String reason) {
        return new RobotsCheckResult(origin, true, true, crawlDelayMs, reason, Instant.now());
    }

    public static RobotsCheckResult disallowed(String origin, String reason) {
        return new RobotsCheckResult(origin, false, true, -1, reason, Instant.now());
    }

    public static RobotsCheckResult unavailable(String origin, String reason) {
        return new RobotsCheckResult(origin, false, false, -1, reason, Instant.now());
    }
}
