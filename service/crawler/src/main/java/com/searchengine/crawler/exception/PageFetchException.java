package com.searchengine.crawler.exception;

import com.searchengine.crawler.model.dto.PageFetchResult;

/**
 * Exception thrown when an HTTP page fetch attempt fails.
 */
public class PageFetchException extends RuntimeException {

    private final PageFetchResult fetchResult;

    public PageFetchException(PageFetchResult fetchResult) {
        super("Page fetch failed for URL: " + fetchResult.requestedUrl() + " - Reason: " + fetchResult.failureReason());
        this.fetchResult = fetchResult;
    }

    public PageFetchResult getFetchResult() {
        return fetchResult;
    }
}
