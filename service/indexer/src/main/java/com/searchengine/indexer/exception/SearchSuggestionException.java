package com.searchengine.indexer.exception;

public class SearchSuggestionException extends RuntimeException {

    public SearchSuggestionException(String message) {
        super(message);
    }

    public SearchSuggestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
