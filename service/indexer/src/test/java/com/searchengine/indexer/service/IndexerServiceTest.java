package com.searchengine.indexer.service;

import com.searchengine.indexer.exception.ElasticsearchIndexingException;
import com.searchengine.indexer.indexer.SearchDocumentIndexer;
import com.searchengine.indexer.model.kafka.SearchDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IndexerServiceTest {

    @Mock
    private SearchDocumentIndexer searchDocumentIndexer;

    @InjectMocks
    private IndexerService indexerService;

    private SearchDocument createTestDocument() {
        Instant now = Instant.now();
        return new SearchDocument(
                "https://example.com",
                "https://example.com",
                "hash123",
                "Title",
                "Description",
                List.of("Heading 1"),
                "Body content text",
                "en",
                3,
                200,
                "text/html",
                now,
                now
        );
    }

    @Test
    void process_validSearchDocument_reachesSearchDocumentIndexer() {
        SearchDocument doc = createTestDocument();

        assertDoesNotThrow(() -> indexerService.process(doc));

        verify(searchDocumentIndexer, times(1)).index(doc);
    }

    @Test
    void process_nullDocument_doesNotCallIndexer() {
        assertDoesNotThrow(() -> indexerService.process(null));

        verify(searchDocumentIndexer, never()).index(null);
    }

    @Test
    void process_indexingFailure_propagatesException() {
        SearchDocument doc = createTestDocument();
        doThrow(new ElasticsearchIndexingException("Indexing failed", new RuntimeException("ES error")))
                .when(searchDocumentIndexer).index(doc);

        assertThatThrownBy(() -> indexerService.process(doc))
                .isInstanceOf(ElasticsearchIndexingException.class)
                .hasMessageContaining("Indexing failed");

        verify(searchDocumentIndexer, times(1)).index(doc);
    }
}
