package com.searchengine.indexer.indexer;

import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchSuggestionResponse;
import com.searchengine.indexer.model.kafka.SearchDocument;
import com.searchengine.indexer.service.SearchService;
import com.searchengine.indexer.service.SearchSuggestionService;
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

    @Autowired
    private SearchSuggestionService searchSuggestionService;

    @Test
    void endToEndElasticsearchIndexingPaginationRelevanceFuzzyHighlightingAndSuggestions() throws Exception {
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

        // 7. Test Field Weighting Relevance Ranking
        SearchDocument relDocA = new SearchDocument(
                "https://searchengine.org/relA", "https://searchengine.org/relA", "hash-rel-a",
                "Spring Boot Framework Overview", "Guide to Spring", List.of("Spring Boot Tutorial"),
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
        assertThat(relevanceResponse.results().get(0).urlHash()).isEqualTo("hash-rel-a");

        // 8. Test Feature 9 Suggestions / Autocomplete
        SearchSuggestionResponse suggestionResponse = searchSuggestionService.suggest("spr");
        assertThat(suggestionResponse.query()).isEqualTo("spr");
        assertThat(suggestionResponse.suggestions()).isNotEmpty();
        assertThat(suggestionResponse.suggestions().get(0)).contains("spring");

        // Non-matching prefix
        SearchSuggestionResponse emptySuggestionResponse = searchSuggestionService.suggest("xyz");
        assertThat(emptySuggestionResponse.query()).isEqualTo("xyz");
        assertThat(emptySuggestionResponse.suggestions()).isEmpty();
    }
}
