package com.searchengine.crawler.exception;

/**
 * Exception thrown for permanent failures that route directly to DLT.
 */
public class NonRetryableCrawlerException extends CrawlerException {

    public NonRetryableCrawlerException(String url, String failureReason, int statusCode) {
        super("Non-retryable failure for URL: " + url + " - StatusCode: " + statusCode + " - Reason: " + failureReason,
                url, failureReason, statusCode);
    }
}
