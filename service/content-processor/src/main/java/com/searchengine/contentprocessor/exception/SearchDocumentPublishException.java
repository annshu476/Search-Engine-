package com.searchengine.contentprocessor.exception;

/**
 * Runtime exception representing a failure or timeout when publishing a SearchDocument to Kafka.
 * This exception is retryable, indicating that message acknowledgment should be withheld.
 */
public class SearchDocumentPublishException extends RuntimeException {

    private final String urlHash;
    private final String topic;

    public SearchDocumentPublishException(String urlHash, String topic, String message, Throwable cause) {
        super(message, cause);
        this.urlHash = urlHash;
        this.topic = topic;
    }

    public SearchDocumentPublishException(String urlHash, String topic, String message) {
        super(message);
        this.urlHash = urlHash;
        this.topic = topic;
    }

    public String getUrlHash() {
        return urlHash;
    }

    public String getTopic() {
        return topic;
    }
}
