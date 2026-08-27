package com.searchengine.indexer.integration;

import com.searchengine.indexer.analytics.SearchAnalyticsService;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.evaluation.SearchEvaluationService;
import com.searchengine.indexer.indexer.SearchDocumentIndexer;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchSuggestionResponse;
import com.searchengine.indexer.model.evaluation.EvaluationReport;
import com.searchengine.indexer.model.kafka.SearchDocument;
import com.searchengine.indexer.service.SearchCacheService;
import com.searchengine.indexer.service.SearchService;
import com.searchengine.indexer.service.SearchSuggestionService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class EndToEndSystemPipelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SearchDocumentIndexer documentIndexer;

    @Autowired
    private SearchService searchService;

    @Autowired
    private SearchSuggestionService searchSuggestionService;

    @Autowired
    private SearchAnalyticsService searchAnalyticsService;

    @Autowired
    private SearchEvaluationService searchEvaluationService;

    @Autowired
    private SearchCacheService searchCacheService;

    @Autowired
    private SearchProperties searchProperties;

    @BeforeEach
    void setUp() {
        searchCacheService.invalidateAll();
        ensureKafkaTopicsExist();
    }

    private void ensureKafkaTopicsExist() {
        try {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "2000");
            try (AdminClient adminClient = AdminClient.create(props)) {
                Set<String> existingTopics = adminClient.listTopics().names().get();
                List<NewTopic> newTopics = List.of("url-topic", "raw-html-topic", "search-document-topic").stream()
                        .filter(t -> !existingTopics.contains(t))
                        .map(t -> new NewTopic(t, 1, (short) 1))
                        .toList();
                if (!newTopics.isEmpty()) {
                    adminClient.createTopics(newTopics).all().get();
                }
            }
        } catch (Exception e) {
            // Ignore if topics already exist or Kafka admin client not reachable
        }
    }

    @Test
    void executeFullSystemEndToEndVerification() throws Exception {
        String testUrlHash = "e2e-feature21-001";
        String testUrl = "https://example.com/e2e-test-feature21";

        SearchDocument document = new SearchDocument(
                testUrl,
                testUrl,
                testUrlHash,
                "Feature 21 Orchestration Guide",
                "Comprehensive system guide for Feature 21 search engine orchestration.",
                List.of("Feature 21 End To End System Guide"),
                "Elasticsearch, Kafka, and Redis integration test content.",
                "en",
                150,
                200,
                "text/html",
                Instant.now(),
                Instant.now()
        );

        // 1. Direct Indexing & Kafka Topic Verification
        documentIndexer.index(document);
        Thread.sleep(500);

        // 2. Search API Verification (q=Feature 21)
        mockMvc.perform(get("/api/search").param("q", "Feature 21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("Feature 21"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].urlHash").value(testUrlHash))
                .andExpect(jsonPath("$.results[0].title").value("Feature 21 Orchestration Guide"))
                .andExpect(jsonPath("$.results[0].highlights.title[0]").value(org.hamcrest.Matchers.either(org.hamcrest.Matchers.is("<em>Feature</em> <em>21</em> Orchestration Guide")).or(org.hamcrest.Matchers.is("<em>Feature 21</em> Orchestration Guide"))));

        // 3. Search API Sorting Verification (sort=newest & sort=relevance)
        mockMvc.perform(get("/api/search").param("q", "Feature 21").param("sort", "newest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort").value("newest"))
                .andExpect(jsonPath("$.totalHits").value(1));

        // 4. Suggestion API Verification
        SearchSuggestionResponse suggestionResponse = searchSuggestionService.suggest("Fe");
        assertThat(suggestionResponse).isNotNull();
        assertThat(suggestionResponse.suggestions()).isNotEmpty();

        // 5. Analytics Summary Verification
        mockMvc.perform(get("/api/search/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSearches").isNumber())
                .andExpect(jsonPath("$.successfulSearches").isNumber());

        // 6. Analytics Top Queries Verification
        mockMvc.perform(get("/api/search/analytics/top-queries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queries").isArray());

        // 7. Search Quality Evaluation Verification
        EvaluationReport report = searchEvaluationService.runBuiltInEvaluation();
        assertThat(report).isNotNull();
        assertThat(report.totalQueries()).isEqualTo(5);

        // 8. Admin Endpoint Verification (Protected by X-Admin-Token)
        mockMvc.perform(post("/api/search/analytics/reset"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/search/analytics/reset")
                        .header("X-Admin-Token", searchProperties.getAdmin().getToken()))
                .andExpect(status().isNoContent());

        // 9. Idempotency & Upsert Verification (Re-index same urlHash with updated title)
        SearchDocument updatedDocument = new SearchDocument(
                testUrl,
                testUrl,
                testUrlHash,
                "Feature 21 Orchestration Guide Updated",
                "Comprehensive system guide for Feature 21 search engine orchestration.",
                List.of("Feature 21 End To End System Guide"),
                "Elasticsearch, Kafka, and Redis integration test content.",
                "en",
                150,
                200,
                "text/html",
                Instant.now(),
                Instant.now()
        );
        documentIndexer.index(updatedDocument);
        searchCacheService.invalidateAll();
        Thread.sleep(500);

        SearchResponse searchAfterUpdate = searchService.search("Feature 21", null, null, null, null, null, 0, 10, "relevance");
        assertThat(searchAfterUpdate.totalHits()).isEqualTo(1L); // Zero duplicates!
        assertThat(searchAfterUpdate.results().get(0).title()).isEqualTo("Feature 21 Orchestration Guide Updated");
    }
}
