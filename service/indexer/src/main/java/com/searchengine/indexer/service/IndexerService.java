package com.searchengine.indexer.service;

import com.searchengine.indexer.indexer.SearchDocumentIndexer;
import com.searchengine.indexer.model.kafka.SearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexerService {

    private final SearchDocumentIndexer searchDocumentIndexer;

    public void process(SearchDocument document) {
        if (document == null) {
            return;
        }

        log.info("Processing search document: urlHash={} url={}", document.urlHash(), document.url());
        searchDocumentIndexer.index(document);
    }
}
