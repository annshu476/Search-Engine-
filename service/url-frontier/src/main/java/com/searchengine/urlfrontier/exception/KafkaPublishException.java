package com.searchengine.urlfrontier.exception;

/** Raised when publishing an accepted URL task to Kafka fails. */
public class KafkaPublishException extends RuntimeException {

    public KafkaPublishException(Throwable cause) {
        super(cause);
    }
}
