package com.searchengine.indexer.indexer;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.exception.ElasticsearchIndexingException;
import com.searchengine.indexer.mapper.SearchDocumentMapper;
import com.searchengine.indexer.model.kafka.SearchDocument;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchDocumentIndexer {

    private final ElasticsearchClient elasticsearchClient;
    private final SearchDocumentMapper searchDocumentMapper;
    private final IndexerElasticsearchProperties indexerElasticsearchProperties;

    public void index(SearchDocument document) {
        if (document == null) {
            return;
        }

        String indexName = indexerElasticsearchProperties.getIndexName();
        Map<String, Object> docMap = searchDocumentMapper.toMap(document);

        try {
            IndexResponse response = elasticsearchClient.index(i -> i
                    .index(indexName)
                    .id(document.urlHash())
                    .document(docMap)
            );

            log.info("SEARCH_DOCUMENT_INDEXED urlHash={} index={} result={}",
                    document.urlHash(), indexName, response.result().jsonValue());
        } catch (Exception e) {
            log.error("SEARCH_DOCUMENT_INDEX_FAILED urlHash={} index={}", document.urlHash(), indexName, e);
            throw new ElasticsearchIndexingException("Failed to index SearchDocument with urlHash: " + document.urlHash(), e);
        }
    }
}
