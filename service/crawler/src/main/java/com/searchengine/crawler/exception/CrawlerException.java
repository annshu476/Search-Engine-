package com.searchengine.crawler.exception;

/**
 * Abstract base exception for crawler execution failures.
 */
public abstract class CrawlerException extends RuntimeException {

    private final String url;
    private final String failureReason;
    private final int statusCode;

    public CrawlerException(String message, String url, String failureReason, int statusCode) {
        super(message);
        this.url = url;
        this.failureReason = failureReason;
        this.statusCode = statusCode;
    }

    public String getUrl() {
        return url;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
