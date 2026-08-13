package com.searchengine.indexer.exception;

public class ElasticsearchConfigurationException extends RuntimeException {

    public ElasticsearchConfigurationException(String message) {
        super(message);
    }

    public ElasticsearchConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
