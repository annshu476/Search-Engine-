package com.searchengine.indexer.analytics;

import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import com.searchengine.indexer.indexer.SearchDocumentIndexer;
import com.searchengine.indexer.model.analytics.SearchAnalyticsSummary;
import com.searchengine.indexer.model.analytics.SearchQueryStats;
import com.searchengine.indexer.model.kafka.SearchDocument;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SearchAnalyticsIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private SearchDocumentIndexer searchDocumentIndexer;

    @Autowired
    private SearchDocumentIndexInitializer indexInitializer;

    @Autowired
    private SearchAnalyticsService analyticsService;

    @BeforeEach
    void setUp() throws Exception {
        analyticsService.reset();

        if (elasticsearchClient.indices().exists(e -> e.index("search-documents")).value()) {
            elasticsearchClient.indices().delete(d -> d.index("search-documents"));
        }

        indexInitializer.initializeIndex();

        Instant now = Instant.now();
        SearchDocument doc1 = new SearchDocument(
                "https://searchengine.org/java-guide", "https://searchengine.org/java-guide", "analytics-doc-1",
                "Spring Boot and Java JDK Guide", "Comprehensive Spring Boot Java tutorial", List.of("Spring Boot", "Java JDK"),
                "Guide explaining Spring Boot framework development and Java JDK tools", "en", 200, 200, "text/html", now, now
        );

        searchDocumentIndexer.index(doc1);
        elasticsearchClient.indices().refresh(r -> r.index("search-documents"));
    }

    @Test
    void executeSearchWorkloadAndVerifyAnalyticsAggregations() {
        // 1. Initial Search: "spring"
        ResponseEntity<Map> resp1 = restTemplate.getForEntity("/api/search?q=spring", Map.class);
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 2. Zero-result search: "nonexistenttermxyz"
        ResponseEntity<Map> resp2 = restTemplate.getForEntity("/api/search?q=nonexistenttermxyz", Map.class);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Synonym search: "jdk"
        ResponseEntity<Map> resp3 = restTemplate.getForEntity("/api/search?q=jdk", Map.class);
        assertThat(resp3.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 4. Repeated search for cache hit: "spring"
        ResponseEntity<Map> resp4 = restTemplate.getForEntity("/api/search?q=spring", Map.class);
        assertThat(resp4.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 5. Verify Summary Endpoint
        ResponseEntity<SearchAnalyticsSummary> summaryResp = restTemplate.getForEntity("/api/search/analytics/summary", SearchAnalyticsSummary.class);
        assertThat(summaryResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        SearchAnalyticsSummary summary = summaryResp.getBody();
        assertThat(summary).isNotNull();
        assertThat(summary.totalSearches()).isGreaterThanOrEqualTo(4);
        assertThat(summary.successfulSearches()).isGreaterThanOrEqualTo(4);
        assertThat(summary.zeroResultSearches()).isGreaterThanOrEqualTo(1);
        assertThat(summary.cacheHitRate()).isGreaterThan(0.0);

        // 6. Verify Top Queries Endpoint
        ResponseEntity<Map> topQueriesResp = restTemplate.getForEntity("/api/search/analytics/top-queries", Map.class);
        assertThat(topQueriesResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> queries = (List<Map<String, Object>>) topQueriesResp.getBody().get("queries");
        assertThat(queries).isNotEmpty();

        // 7. Verify Zero-Result Endpoint
        ResponseEntity<Map> zeroResultsResp = restTemplate.getForEntity("/api/search/analytics/zero-results", Map.class);
        assertThat(zeroResultsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> zeroQueries = (List<Map<String, Object>>) zeroResultsResp.getBody().get("queries");
        assertThat(zeroQueries).isNotEmpty();
    }
}
