package com.searchengine.crawler.exception;

/**
 * Exception thrown when robots.txt is unavailable due to infrastructure/network failure.
 */
public class RobotsUnavailableException extends RetryableCrawlerException {

    public RobotsUnavailableException(String url, String reason) {
        super(url, "Robots policy unavailable: " + reason, 0);
    }
}
