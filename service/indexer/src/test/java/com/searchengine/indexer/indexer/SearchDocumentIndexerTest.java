package com.searchengine.indexer.indexer;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.exception.ElasticsearchIndexingException;
import com.searchengine.indexer.mapper.SearchDocumentMapper;
import com.searchengine.indexer.model.kafka.SearchDocument;
import com.searchengine.indexer.service.SearchCacheService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchDocumentIndexerTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private SearchDocumentMapper searchDocumentMapper;

    @Mock
    private SearchCacheService searchCacheService;

    private IndexerElasticsearchProperties indexerProperties;
    private SearchDocumentIndexer searchDocumentIndexer;

    @BeforeEach
    void setUp() {
        indexerProperties = new IndexerElasticsearchProperties();
        indexerProperties.setIndexName("test-search-documents");

        searchDocumentIndexer = new SearchDocumentIndexer(elasticsearchClient, searchDocumentMapper, indexerProperties, searchCacheService);
    }

    @Test
    void index_successfulIndexing_invalidatesSearchCache() throws IOException {
        SearchDocument doc = new SearchDocument(
                "https://example.com", "https://example.com", "hash123",
                "Title", "Meta", List.of("H1"), "Body", "en", 100, 200, "text/html",
                Instant.now(), Instant.now()
        );

        given(searchDocumentMapper.toMap(doc)).willReturn(Map.of("url", "https://example.com"));

        IndexResponse indexResponse = mock(IndexResponse.class);
        given(indexResponse.result()).willReturn(co.elastic.clients.elasticsearch._types.Result.Created);
        given(elasticsearchClient.index(any(Function.class))).willReturn(indexResponse);

        searchDocumentIndexer.index(doc);

        verify(searchCacheService).invalidateAll();
    }

    @Test
    void index_failedIndexing_doesNotInvalidateSearchCache() throws IOException {
        SearchDocument doc = new SearchDocument(
                "https://example.com", "https://example.com", "hash123",
                "Title", "Meta", List.of("H1"), "Body", "en", 100, 200, "text/html",
                Instant.now(), Instant.now()
        );

        given(searchDocumentMapper.toMap(doc)).willReturn(Map.of("url", "https://example.com"));
        given(elasticsearchClient.index(any(Function.class))).willThrow(new IOException("Elasticsearch down"));

        assertThatThrownBy(() -> searchDocumentIndexer.index(doc))
                .isInstanceOf(ElasticsearchIndexingException.class);

        verify(searchCacheService, never()).invalidateAll();
    }
}
