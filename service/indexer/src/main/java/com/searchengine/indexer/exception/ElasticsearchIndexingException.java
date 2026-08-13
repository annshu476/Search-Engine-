package com.searchengine.indexer.exception;

public class ElasticsearchIndexingException extends RuntimeException {

    public ElasticsearchIndexingException(String message) {
        super(message);
    }

    public ElasticsearchIndexingException(String message, Throwable cause) {
        super(message, cause);
    }
}
