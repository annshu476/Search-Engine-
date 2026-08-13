package com.searchengine.indexer.service;

import com.searchengine.indexer.model.kafka.SearchDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class IndexerServiceTest {

    private IndexerService indexerService;

    @BeforeEach
    void setUp() {
        indexerService = new IndexerService();
    }

    @Test
    void process_validSearchDocument_acceptedWithoutModification() {
        Instant now = Instant.now();
        SearchDocument originalDoc = new SearchDocument(
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

        assertDoesNotThrow(() -> indexerService.process(originalDoc));

        assertThat(originalDoc.url()).isEqualTo("https://example.com");
        assertThat(originalDoc.urlHash()).isEqualTo("hash123");
        assertThat(originalDoc.wordCount()).isEqualTo(3);
        assertThat(originalDoc.statusCode()).isEqualTo(200);
    }

    @Test
    void process_nullDocument_handlesGracefully() {
        assertDoesNotThrow(() -> indexerService.process(null));
    }
}
