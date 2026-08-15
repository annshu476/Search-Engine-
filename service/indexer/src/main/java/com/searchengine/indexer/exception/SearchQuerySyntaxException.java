package com.searchengine.indexer.exception;

public class SearchQuerySyntaxException extends IllegalArgumentException {
    public SearchQuerySyntaxException(String message) {
        super(message);
    }
}
