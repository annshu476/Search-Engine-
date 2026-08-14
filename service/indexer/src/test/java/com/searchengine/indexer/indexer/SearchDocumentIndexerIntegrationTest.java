package com.searchengine.indexer.indexer;

import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.kafka.SearchDocument;
import com.searchengine.indexer.service.SearchService;
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

    @Autowired
    private SearchService searchService;

    @Test
    void endToEndElasticsearchIndexingIdempotencyPaginationAndRelevance() throws Exception {
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

        // 6. Test Pagination and Sorting via SearchService
        Instant time1 = Instant.now().minusSeconds(60);
        Instant time2 = Instant.now();

        SearchDocument doc1 = new SearchDocument(
                "https://searchengine.org/page1", "https://searchengine.org/page1", "hash-page-1",
                "Pagination Test Page One", "Meta description for page 1", List.of("H1"),
                "Unique content for pagination testing", "en", 150, 200, "text/html", time1, time1
        );

        SearchDocument doc2 = new SearchDocument(
                "https://searchengine.org/page2", "https://searchengine.org/page2", "hash-page-2",
                "Pagination Test Page Two", "Meta description for page 2", List.of("H2"),
                "Unique content for pagination testing", "en", 200, 200, "text/html", time2, time2
        );

        searchDocumentIndexer.index(doc1);
        searchDocumentIndexer.index(doc2);

        elasticsearchClient.indices().refresh(r -> r.index("search-documents"));

        SearchResponse page0 = searchService.search("pagination", 0, 1, "newest");
        assertThat(page0.totalHits()).isGreaterThanOrEqualTo(2L);
        assertThat(page0.page()).isEqualTo(0);
        assertThat(page0.size()).isEqualTo(1);
        assertThat(page0.sort()).isEqualTo("newest");
        assertThat(page0.results()).hasSize(1);

        SearchResponse page1 = searchService.search("pagination", 1, 1, "newest");
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.size()).isEqualTo(1);
        assertThat(page1.results()).hasSize(1);
        assertThat(page1.results().get(0).urlHash()).isNotEqualTo(page0.results().get(0).urlHash());

        // 7. Test Field Weighting Relevance Ranking
        SearchDocument relDocA = new SearchDocument(
                "https://searchengine.org/relA", "https://searchengine.org/relA", "hash-rel-a",
                "Spring Boot Framework Overview", "Guide to Spring", List.of("Spring"),
                "Java programming concepts", "en", 300, 200, "text/html", time2, time2
        );

        SearchDocument relDocB = new SearchDocument(
                "https://searchengine.org/relB", "https://searchengine.org/relB", "hash-rel-b",
                "Java Basics Guide", "Introduction to Java", List.of("Java"),
                "Includes Spring Boot framework tutorial inside body", "en", 300, 200, "text/html", time2, time2
        );

        searchDocumentIndexer.index(relDocA);
        searchDocumentIndexer.index(relDocB);

        elasticsearchClient.indices().refresh(r -> r.index("search-documents"));

        SearchResponse relevanceResponse = searchService.search("Spring Boot", 0, 10, "relevance");
        assertThat(relevanceResponse.results()).isNotEmpty();
        // Document A (Title match for "Spring Boot") must rank higher than Document B (Body-only match)
        assertThat(relevanceResponse.results().get(0).urlHash()).isEqualTo("hash-rel-a");
    }
}
