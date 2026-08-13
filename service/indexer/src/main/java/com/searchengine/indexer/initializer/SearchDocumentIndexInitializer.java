package com.searchengine.indexer.initializer;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchDocumentIndexInitializer {

    private final ElasticsearchClient elasticsearchClient;
    private final IndexerElasticsearchProperties indexerElasticsearchProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndex() {
        String indexName = indexerElasticsearchProperties.getIndexName();
        try {
            boolean exists = elasticsearchClient.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
                log.info("Creating Elasticsearch index with explicit mapping: index={}", indexName);
                elasticsearchClient.indices().create(c -> c
                        .index(indexName)
                        .mappings(m -> m
                                .properties("url", p -> p.keyword(k -> k))
                                .properties("canonicalUrl", p -> p.keyword(k -> k))
                                .properties("urlHash", p -> p.keyword(k -> k))
                                .properties("title", p -> p.text(t -> t))
                                .properties("metaDescription", p -> p.text(t -> t))
                                .properties("headings", p -> p.text(t -> t))
                                .properties("bodyText", p -> p.text(t -> t))
                                .properties("language", p -> p.keyword(k -> k))
                                .properties("wordCount", p -> p.integer(i -> i))
                                .properties("statusCode", p -> p.integer(i -> i))
                                .properties("contentType", p -> p.keyword(k -> k))
                                .properties("fetchedAt", p -> p.date(d -> d))
                                .properties("indexedAt", p -> p.date(d -> d))
                        )
                );
                log.info("SEARCH_DOCUMENT_INDEX_READY index={}", indexName);
            } else {
                log.info("Elasticsearch index already exists: index={}", indexName);
            }
        } catch (Exception e) {
            log.error("SEARCH_DOCUMENT_INDEX_INITIALIZATION_FAILED index={}", indexName, e);
            throw new IllegalStateException("Failed to initialize Elasticsearch index: " + indexName, e);
        }
    }
}
