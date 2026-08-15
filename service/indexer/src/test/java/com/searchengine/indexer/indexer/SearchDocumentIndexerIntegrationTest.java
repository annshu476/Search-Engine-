package com.searchengine.indexer.indexer;

import com.searchengine.indexer.analytics.SearchAnalyticsService;
import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import com.searchengine.indexer.model.analytics.SearchAnalyticsSnapshot;
import com.searchengine.indexer.model.analytics.ZeroResultsResponse;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchResult;
import com.searchengine.indexer.model.dto.SearchSuggestionResponse;
import com.searchengine.indexer.model.kafka.SearchDocument;
import com.searchengine.indexer.service.SearchCacheService;
import com.searchengine.indexer.service.SearchService;
import com.searchengine.indexer.service.SearchSuggestionService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

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
    private SearchSuggestionService suggestionService;

    @Autowired
    private SearchCacheService searchCacheService;

    @Autowired
    private SearchAnalyticsService searchAnalyticsService;

    @Test
    void endToEndElasticsearchIndexingPaginationRelevanceFuzzyHighlightingSuggestionsFiltersPhraseMatchingAdvancedQuerySyntaxCachingAnalyticsSynonymsAndSpellCorrection() throws Exception {
        // 0. Clean index for deterministic integration test run
        if (elasticsearchClient.indices().exists(e -> e.index("search-documents")).value()) {
            elasticsearchClient.indices().delete(d -> d.index("search-documents"));
        }

        // 1. Initialize index mapping
        indexInitializer.initializeIndex();

        // 2. Index documents with distinct relevance properties
        Instant now = Instant.now();

        // Doc 1: Title match for "Spring Framework", English, 200 OK
        SearchDocument doc1 = new SearchDocument(
                "https://searchengine.org/doc1", "https://searchengine.org/doc1", "hash1",
                "Spring Framework Tutorial", "Learn Spring Framework step by step", List.of("Spring Basics", "Java"),
                "This tutorial covers Spring Framework core concepts and dependency injection", "en", 100, 200, "text/html", now, now
        );

        // Doc 2: Body match for "Spring", German, 200 OK
        SearchDocument doc2 = new SearchDocument(
                "https://searchengine.org/doc2", "https://searchengine.org/doc2", "hash2",
                "Java Entwickler Handbuch", "Ein Leitfaden fur Java Softwareentwicklung", List.of("Java"),
                "In diesem Kapitel besprechen wir Spring Boot und enterprise Architektur", "de", 150, 200, "text/html", now, now
        );

        // Doc 3: Heading match for "Spring Security", English, 404 Status Code
        SearchDocument doc3 = new SearchDocument(
                "https://searchengine.org/doc3", "https://searchengine.org/doc3", "hash3",
                "Security Architecture", "Authentication and Authorization", List.of("Spring Security Modules"),
                "Detailed overview of authentication filters and security protocols", "en", 80, 404, "text/html", now, now
        );

        // Doc 4: Document containing phrase "distributed search engine"
        SearchDocument doc4 = new SearchDocument(
                "https://searchengine.org/doc4", "https://searchengine.org/doc4", "hash4",
                "Distributed Search Engine Architecture", "High performance distributed search system", List.of("Search Architecture"),
                "Building a robust distributed search engine using Kafka and Elasticsearch", "en", 300, 200, "text/html", now, now
        );

        // Doc 5: Document for advanced operator test (+java -xml)
        SearchDocument doc5 = new SearchDocument(
                "https://searchengine.org/doc5", "https://searchengine.org/doc5", "hash5",
                "Spring XML Legacy Configuration", "Legacy XML beans for Spring", List.of("Spring XML"),
                "Configuring Spring application context using legacy XML files", "en", 120, 200, "text/html", now, now
        );

        searchDocumentIndexer.index(doc1);
        searchDocumentIndexer.index(doc2);
        searchDocumentIndexer.index(doc3);
        searchDocumentIndexer.index(doc4);
        searchDocumentIndexer.index(doc5);

        // Force refresh so indexed documents are immediately searchable
        elasticsearchClient.indices().refresh(r -> r.index("search-documents"));

        // 3. Test Feature 7 Search Execution & Feature 11 Relevance
        SearchResponse response = searchService.search("Spring", 0, 10, "relevance");
        assertThat(response.totalHits()).isGreaterThanOrEqualTo(4L);
        assertThat(response.results()).isNotEmpty();
        // Title match (doc1) should rank higher than body match (doc2) due to title boost 4.0 vs body boost 1.0
        assertThat(response.results().get(0).urlHash()).isEqualTo("hash1");

        // 4. Test Feature 8 Highlighting
        SearchResult topResult = response.results().get(0);
        assertThat(topResult.highlights()).isNotNull();
        assertThat(topResult.highlights()).containsKey("title");
        assertThat(topResult.highlights().get("title").get(0)).contains("<em>Spring</em>");

        // 5. Test Feature 9 Pagination
        SearchResponse page0 = searchService.search("Spring", 0, 2, "relevance");
        assertThat(page0.results()).hasSize(2);
        assertThat(page0.page()).isEqualTo(0);
        assertThat(page0.totalPages()).isGreaterThanOrEqualTo(2);

        SearchResponse page1 = searchService.search("Spring", 1, 2, "relevance");
        assertThat(page1.results()).hasSize(2);
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.results().get(0).urlHash()).isNotEqualTo(page0.results().get(0).urlHash());

        // 6. Test Feature 9 Sorting by Newest
        SearchResponse newestResponse = searchService.search("Spring", 0, 10, "newest");
        assertThat(newestResponse.sort()).isEqualTo("newest");
        assertThat(newestResponse.results()).isNotEmpty();

        // 7. Test Feature 10 Filter by Language
        SearchResponse enResponse = searchService.search("Spring", "en", null, null, null, null, 0, 10, "relevance");
        assertThat(enResponse.results()).allMatch(r -> "en".equals(r.language()));

        // 8. Test Feature 10 Filter by Status Code (404)
        SearchResponse status404Response = searchService.search("Spring", null, null, 404, null, null, 0, 10, "relevance");
        assertThat(status404Response.results()).hasSize(1);
        assertThat(status404Response.results().get(0).urlHash()).isEqualTo("hash3");

        // 9. Test Feature 7 Fuzzy Search
        SearchResponse fuzzyResponse = searchService.search("Sprng", 0, 10, "relevance");
        assertThat(fuzzyResponse.totalHits()).isGreaterThan(0L);
        assertThat(fuzzyResponse.results().get(0).title()).contains("Spring");

        // 10. Test Suggestions API (Feature 6 & 16)
        SearchSuggestionResponse suggestions = suggestionService.suggest("Spr");
        assertThat(suggestions.suggestions()).isNotEmpty();
        assertThat(suggestions.suggestions()).anyMatch(s -> s.toLowerCase().contains("spring"));

        // 11. Test Feature 12 Advanced Query Syntax
        SearchResponse phraseResponse = searchService.search("\"distributed search engine\"", 0, 10, "relevance");
        assertThat(phraseResponse.totalHits()).isEqualTo(1L);
        assertThat(phraseResponse.results().get(0).urlHash()).isEqualTo("hash4");

        SearchResponse advancedOperatorResponse = searchService.search("Spring -xml", 0, 10, "relevance");
        assertThat(advancedOperatorResponse.results()).noneMatch(r -> "hash5".equals(r.urlHash()));

        // 12. Test Feature 14 Search Cache
        SearchResponse cacheResp1 = searchService.search("UniqueCacheKeyword", 0, 10, "relevance");
        SearchResponse cacheResp2 = searchService.search("UniqueCacheKeyword", 0, 10, "relevance");
        assertThat(cacheResp1.totalHits()).isEqualTo(cacheResp2.totalHits());

        // Invalidate cache by indexing new document
        SearchDocument doc6 = new SearchDocument(
                "https://searchengine.org/doc6", "https://searchengine.org/doc6", "hash6",
                "UniqueCacheKeyword Title", "Meta description", List.of("Tag"),
                "Body content with UniqueCacheKeyword", "en", 100, 200, "text/html", now, now
        );
        searchDocumentIndexer.index(doc6);
        elasticsearchClient.indices().refresh(r -> r.index("search-documents"));

        SearchResponse cacheResp3 = searchService.search("UniqueCacheKeyword", 0, 10, "relevance");
        assertThat(cacheResp3.totalHits()).isEqualTo(1L);

        // 13. Test Feature 15 & 18 Search Analytics Integration
        SearchResponse zeroHitResp = searchService.search("NonExistentKeywordXYZ999", 0, 10, "relevance");
        assertThat(zeroHitResp.totalHits()).isEqualTo(0L);

        SearchAnalyticsSnapshot snapshot = searchAnalyticsService.getSnapshot();
        assertThat(snapshot.totalRequests()).isGreaterThan(0L);
        assertThat(snapshot.successfulRequests()).isGreaterThan(0L);
        assertThat(snapshot.zeroResultSearches()).isGreaterThanOrEqualTo(1L);

        ZeroResultsResponse zeroResultsResponse = searchAnalyticsService.getZeroResults();
        String expectedHash = SearchAnalyticsService.computeQueryHash("NonExistentKeywordXYZ999");
        assertThat(zeroResultsResponse.queries()).extracting("query").contains(expectedHash);

        // 14. Test Feature 16 Synonym-Aware Search
        SearchDocument synDoc = new SearchDocument(
                "https://searchengine.org/syn1", "https://searchengine.org/syn1", "hash-syn-1",
                "Java Development Kit Guide", "Guide to Java JDK tools", List.of("Java"),
                "Body content about Java Development Kit", "en", 100, 200, "text/html", now, now
        );
        searchDocumentIndexer.index(synDoc);
        elasticsearchClient.indices().refresh(r -> r.index("search-documents"));

        SearchResponse synonymResponse = searchService.search("jdk", 0, 10, "relevance");
        assertThat(synonymResponse.totalHits()).isGreaterThan(0L);
        assertThat(synonymResponse.results()).anyMatch(r -> "hash-syn-1".equals(r.urlHash()) || "hash1".equals(r.urlHash()));
    }
}
