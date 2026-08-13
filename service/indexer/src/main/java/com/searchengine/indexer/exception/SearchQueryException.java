package com.searchengine.indexer.exception;

public class SearchQueryException extends RuntimeException {

    public SearchQueryException(String message) {
        super(message);
    }

    public SearchQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
