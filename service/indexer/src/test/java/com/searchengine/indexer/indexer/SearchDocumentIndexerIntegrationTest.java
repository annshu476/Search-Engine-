package com.searchengine.indexer.indexer;

import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import com.searchengine.indexer.model.kafka.SearchDocument;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
class SearchDocumentIndexerIntegrationTest {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private SearchDocumentIndexer searchDocumentIndexer;

    @Autowired
    private SearchDocumentIndexInitializer indexInitializer;

    @Test
    void endToEndElasticsearchIndexingAndIdempotency() throws Exception {
        // 1. Verify index created by initializer
        indexInitializer.initializeIndex();
        boolean indexExists = elasticsearchClient.indices().exists(e -> e.index("search-documents")).value();
        assertThat(indexExists).isTrue();

        // 2. Index initial document (Document A)
        Instant now = Instant.now();
        SearchDocument docA = new SearchDocument(
                "https://searchengine.org/idempotent-test",
                "https://searchengine.org/idempotent-test",
                "same-hash-123",
                "Old Title",
                "Old Meta Description",
                List.of("Heading A", "Heading B"),
                "Old body text content",
                "en",
                100,
                200,
                "text/html",
                now,
                now
        );

        searchDocumentIndexer.index(docA);

        // 3. Retrieve document by urlHash (_id) and verify all fields
        GetResponse<Map> responseA = elasticsearchClient.get(g -> g
                .index("search-documents")
                .id("same-hash-123"), Map.class);

        assertThat(responseA.found()).isTrue();
        Map<String, Object> sourceA = responseA.source();
        assertThat(sourceA).isNotNull();
        assertThat(sourceA.get("url")).isEqualTo("https://searchengine.org/idempotent-test");
        assertThat(sourceA.get("canonicalUrl")).isEqualTo("https://searchengine.org/idempotent-test");
        assertThat(sourceA.get("urlHash")).isEqualTo("same-hash-123");
        assertThat(sourceA.get("title")).isEqualTo("Old Title");
        assertThat(sourceA.get("metaDescription")).isEqualTo("Old Meta Description");
        assertThat(sourceA.get("bodyText")).isEqualTo("Old body text content");
        assertThat(sourceA.get("language")).isEqualTo("en");
        assertThat(sourceA.get("wordCount")).isEqualTo(100);
        assertThat(sourceA.get("statusCode")).isEqualTo(200);
        assertThat(sourceA.get("contentType")).isEqualTo("text/html");

        // 4. Index updated document with SAME urlHash (Document B)
        SearchDocument docB = new SearchDocument(
                "https://searchengine.org/idempotent-test",
                "https://searchengine.org/idempotent-test",
                "same-hash-123",
                "New Title",
                "New Meta Description",
                List.of("Updated Heading"),
                "New updated body text content",
                "en",
                250,
                200,
                "text/html",
                now,
                now
        );

        searchDocumentIndexer.index(docB);

        // 5. Retrieve by urlHash (_id) and verify update idempotency
        GetResponse<Map> responseB = elasticsearchClient.get(g -> g
                .index("search-documents")
                .id("same-hash-123"), Map.class);

        assertThat(responseB.found()).isTrue();
        Map<String, Object> sourceB = responseB.source();
        assertThat(sourceB).isNotNull();
        assertThat(sourceB.get("title")).isEqualTo("New Title");
        assertThat(sourceB.get("metaDescription")).isEqualTo("New Meta Description");
        assertThat(sourceB.get("bodyText")).isEqualTo("New updated body text content");
        assertThat(sourceB.get("wordCount")).isEqualTo(250);
    }
}
