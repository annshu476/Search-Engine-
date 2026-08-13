package com.searchengine.indexer.indexer;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.exception.ElasticsearchIndexingException;
import com.searchengine.indexer.mapper.SearchDocumentMapper;
import com.searchengine.indexer.model.kafka.SearchDocument;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SearchDocumentIndexerTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    private SearchDocumentMapper searchDocumentMapper;
    private IndexerElasticsearchProperties properties;
    private SearchDocumentIndexer searchDocumentIndexer;

    @BeforeEach
    void setUp() {
        searchDocumentMapper = new SearchDocumentMapper();
        properties = new IndexerElasticsearchProperties();
        properties.setIndexName("custom-search-index");
        searchDocumentIndexer = new SearchDocumentIndexer(elasticsearchClient, searchDocumentMapper, properties);
    }

    private SearchDocument createTestDocument() {
        Instant now = Instant.now();
        return new SearchDocument(
                "https://example.com/test",
                "https://example.com/test",
                "hashUrl123",
                "Title",
                "Description",
                List.of("H1"),
                "Body content",
                "en",
                10,
                200,
                "text/html",
                now,
                now
        );
    }

    @Test
    void index_nullDocument_doesNothing() {
        assertDoesNotThrow(() -> searchDocumentIndexer.index(null));
    }

    @Test
    void index_validDocument_indexesWithCorrectIndexNameAndUrlHashAsId() throws IOException {
        SearchDocument doc = createTestDocument();
        IndexResponse mockResponse = mock(IndexResponse.class);
        given(mockResponse.result()).willReturn(Result.Created);
        given(elasticsearchClient.index(any(Function.class))).willReturn(mockResponse);

        assertDoesNotThrow(() -> searchDocumentIndexer.index(doc));
    }

    @Test
    void index_elasticsearchException_throwsElasticsearchIndexingException() throws IOException {
        SearchDocument doc = createTestDocument();
        given(elasticsearchClient.index(any(Function.class))).willThrow(new RuntimeException("ES Connection Error"));

        assertThatThrownBy(() -> searchDocumentIndexer.index(doc))
                .isInstanceOf(ElasticsearchIndexingException.class)
                .hasMessageContaining("Failed to index SearchDocument with urlHash: hashUrl123");
    }
}
