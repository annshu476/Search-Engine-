package com.searchengine.contentprocessor.service;

import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import com.searchengine.contentprocessor.model.kafka.SearchDocument;
import com.searchengine.contentprocessor.parser.HtmlDocumentParser;
import com.searchengine.contentprocessor.producer.SearchDocumentProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for orchestrating raw HTML document parsing and search document publishing.
 */
@Service
public class ContentProcessorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentProcessorService.class);

    private final HtmlDocumentParser htmlDocumentParser;
    private final SearchDocumentProducer searchDocumentProducer;

    public ContentProcessorService(HtmlDocumentParser htmlDocumentParser, SearchDocumentProducer searchDocumentProducer) {
        this.htmlDocumentParser = htmlDocumentParser;
        this.searchDocumentProducer = searchDocumentProducer;
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

            searchDocumentProducer.send(searchDocument);
        }

        return searchDocument;
    }
}
