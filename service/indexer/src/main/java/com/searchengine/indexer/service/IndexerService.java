package com.searchengine.indexer.service;

import com.searchengine.indexer.model.kafka.SearchDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class IndexerService {

    public void process(SearchDocument document) {
        if (document == null) {
            return;
        }

        log.info("Domain boundary reached for SearchDocument: urlHash={} url={}", document.urlHash(), document.url());
        // Feature 2 establishes domain boundary. Elasticsearch indexing logic will be added in Feature 3.
    }
}
