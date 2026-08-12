package com.searchengine.crawler.exception;

/**
 * Exception thrown when robots.txt is unavailable due to infrastructure/network failure.
 */
public class RobotsUnavailableException extends RuntimeException {

    private final String url;
    private final String reason;

    public RobotsUnavailableException(String url, String reason) {
        super("Robots policy unavailable for URL: " + url + " - Reason: " + reason);
        this.url = url;
        this.reason = reason;
    }

    public String getUrl() {
        return url;
    }

    public String getReason() {
        return reason;
    }
}
