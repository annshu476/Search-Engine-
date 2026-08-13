package com.searchengine.crawler.exception;

/**
 * Exception thrown when publishing a RawHtmlDocument to raw-html-topic fails.
 * Extends RetryableCrawlerException to trigger non-blocking retries via Feature 7 retry handling.
 */
public class RawHtmlPublishException extends RetryableCrawlerException {

    public RawHtmlPublishException(String url, String failureReason, Throwable cause) {
        super(url, failureReason, 0);
        if (cause != null) {
            initCause(cause);
        }
    }

    public RawHtmlPublishException(String url, String failureReason) {
        super(url, failureReason, 0);
    }
}
