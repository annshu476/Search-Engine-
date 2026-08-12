package com.searchengine.crawler.exception;

/**
 * Exception thrown for transient failures that should trigger non-blocking Kafka retries.
 */
public class RetryableCrawlerException extends CrawlerException {

    public RetryableCrawlerException(String url, String failureReason, int statusCode) {
        super("Retryable failure for URL: " + url + " - StatusCode: " + statusCode + " - Reason: " + failureReason,
                url, failureReason, statusCode);
    }
}
