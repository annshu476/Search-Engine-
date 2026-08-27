package com.searchengine.contentprocessor.service;

import com.searchengine.contentprocessor.model.kafka.DiscoveredUrl;
import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import com.searchengine.contentprocessor.model.kafka.SearchDocument;
import com.searchengine.contentprocessor.parser.HtmlDocumentParser;
import com.searchengine.contentprocessor.producer.DiscoveredUrlProducer;
import com.searchengine.contentprocessor.producer.SearchDocumentProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for orchestrating raw HTML document parsing, search document publishing, and discovered URL extraction.
 */
@Service
public class ContentProcessorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentProcessorService.class);

    private final HtmlDocumentParser htmlDocumentParser;
    private final SearchDocumentProducer searchDocumentProducer;
    private final DiscoveredUrlProducer discoveredUrlProducer;

    public ContentProcessorService(
            HtmlDocumentParser htmlDocumentParser,
            SearchDocumentProducer searchDocumentProducer,
            DiscoveredUrlProducer discoveredUrlProducer
    ) {
        this.htmlDocumentParser = htmlDocumentParser;
        this.searchDocumentProducer = searchDocumentProducer;
        this.discoveredUrlProducer = discoveredUrlProducer;
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

        // Link extraction for URL Frontier discovery loop
        try {
            List<DiscoveredUrl> discoveredUrls = htmlDocumentParser.extractDiscoveredLinks(document);
            if (discoveredUrls != null && !discoveredUrls.isEmpty()) {
                LOGGER.info("CRAWL_LINK_EXTRACTION_COMPLETED urlHash={} count={}", document.urlHash(), discoveredUrls.size());
                for (DiscoveredUrl discoveredUrl : discoveredUrls) {
                    discoveredUrlProducer.send(discoveredUrl);
                }
            }
        } catch (Exception e) {
            LOGGER.error("CRAWL_LINK_EXTRACTION_FAILED urlHash={} error=\"{}\"", document.urlHash(), e.getMessage());
        }

        return searchDocument;
    }
}
