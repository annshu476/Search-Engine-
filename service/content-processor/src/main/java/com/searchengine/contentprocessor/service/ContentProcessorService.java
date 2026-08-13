package com.searchengine.contentprocessor.service;

import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import com.searchengine.contentprocessor.model.kafka.SearchDocument;
import com.searchengine.contentprocessor.parser.HtmlDocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for orchestrating raw HTML document processing.
 * For Feature 2, delegates raw HTML document parsing to HtmlDocumentParser and returns the SearchDocument.
 */
@Service
public class ContentProcessorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentProcessorService.class);

    private final HtmlDocumentParser htmlDocumentParser;

    public ContentProcessorService(HtmlDocumentParser htmlDocumentParser) {
        this.htmlDocumentParser = htmlDocumentParser;
    }

    public SearchDocument process(RawHtmlDocument document) {
        if (document == null) {
            LOGGER.warn("RAW_HTML_DOCUMENT_NULL skipped null input document");
            return null;
        }

        LOGGER.info("RAW_HTML_DOCUMENT_PROCESSING urlHash={} url=\"{}\"", document.urlHash(), document.url());

        SearchDocument searchDocument = htmlDocumentParser.parse(document);

        if (searchDocument != null) {
            LOGGER.info("SEARCH_DOCUMENT_CREATED urlHash={} title=\"{}\" wordCount={} canonicalUrl=\"{}\"",
                    searchDocument.urlHash(), searchDocument.title(), searchDocument.wordCount(), searchDocument.canonicalUrl());
        }

        return searchDocument;
    }
}
