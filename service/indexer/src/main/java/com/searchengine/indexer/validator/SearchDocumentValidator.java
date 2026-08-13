package com.searchengine.indexer.validator;

import com.searchengine.indexer.exception.SearchDocumentValidationException;
import com.searchengine.indexer.model.kafka.SearchDocument;
import org.springframework.stereotype.Component;

@Component
public class SearchDocumentValidator {

    public void validate(SearchDocument document) {
        if (document == null) {
            throw new SearchDocumentValidationException("SearchDocument must not be null");
        }

        if (document.url() == null || document.url().isBlank()) {
            throw new SearchDocumentValidationException("url must not be null or blank");
        }

        if (document.canonicalUrl() == null || document.canonicalUrl().isBlank()) {
            throw new SearchDocumentValidationException("canonicalUrl must not be null or blank");
        }

        if (document.urlHash() == null || document.urlHash().isBlank()) {
            throw new SearchDocumentValidationException("urlHash must not be null or blank");
        }

        if (document.headings() == null) {
            throw new SearchDocumentValidationException("headings list must not be null");
        }

        if (document.bodyText() == null) {
            throw new SearchDocumentValidationException("bodyText must not be null");
        }

        if (document.wordCount() == null) {
            throw new SearchDocumentValidationException("wordCount must not be null");
        }

        if (document.wordCount() < 0) {
            throw new SearchDocumentValidationException("wordCount must be greater than or equal to 0");
        }

        if (document.statusCode() == null) {
            throw new SearchDocumentValidationException("statusCode must not be null");
        }

        if (document.statusCode() < 100 || document.statusCode() > 599) {
            throw new SearchDocumentValidationException("statusCode must be a valid HTTP status code (100-599)");
        }

        if (document.contentType() == null || document.contentType().isBlank()) {
            throw new SearchDocumentValidationException("contentType must not be null or blank");
        }

        if (document.fetchedAt() == null) {
            throw new SearchDocumentValidationException("fetchedAt timestamp must not be null");
        }

        if (document.indexedAt() == null) {
            throw new SearchDocumentValidationException("indexedAt timestamp must not be null");
        }
    }
}
