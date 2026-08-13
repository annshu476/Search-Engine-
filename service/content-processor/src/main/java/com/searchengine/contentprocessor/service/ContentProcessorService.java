package com.searchengine.contentprocessor.service;

import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for processing raw HTML documents.
 * For Feature 1 foundation, this logs document receipt without executing HTML parsing or search document creation.
 */
@Service
public class ContentProcessorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentProcessorService.class);

    public void process(RawHtmlDocument document) {
        if (document == null) {
            LOGGER.warn("RAW_HTML_DOCUMENT_NULL payload is null");
            return;
        }

        LOGGER.info("RAW_HTML_DOCUMENT_RECEIVED urlHash={} url=\"{}\" statusCode={} contentType={}",
                document.urlHash(), document.url(), document.statusCode(), document.contentType());
    }
}
