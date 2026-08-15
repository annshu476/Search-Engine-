package com.searchengine.indexer.security;

import com.searchengine.indexer.analytics.SearchAnalyticsController;
import com.searchengine.indexer.analytics.SearchAnalyticsService;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.controller.SearchController;
import com.searchengine.indexer.evaluation.SearchEvaluationController;
import com.searchengine.indexer.evaluation.SearchEvaluationService;
import com.searchengine.indexer.health.ElasticsearchHealthIndicator;
import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.evaluation.EvaluationReport;
import com.searchengine.indexer.search.SearchQueryParser;
import com.searchengine.indexer.service.SearchService;
import com.searchengine.indexer.service.SearchSuggestionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SearchController.class, SearchAnalyticsController.class, SearchEvaluationController.class})
@Import({
        SearchProperties.class,
        SearchSecurityFilter.class,
        RequestCorrelationFilter.class,
        SearchRateLimiter.class,
        ClientIdentityResolver.class,
        AdminTokenValidator.class,
        SearchRequestCostEvaluator.class,
        SearchQueryParser.class,
        SimpleMeterRegistry.class
})
class SearchSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SearchProperties searchProperties;

    @Autowired
    private SearchRateLimiter searchRateLimiter;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private SearchSuggestionService searchSuggestionService;

    @MockitoBean
    private SearchAnalyticsService analyticsService;

    @MockitoBean
    private SearchEvaluationService evaluationService;

    @MockitoBean
    private ElasticsearchHealthIndicator elasticsearchHealthIndicator;

    @MockitoBean
    private SearchDocumentIndexInitializer searchDocumentIndexInitializer;

    @BeforeEach
    void setUp() {
        searchRateLimiter.reset();
        searchProperties.getRedis().setEnabled(false);
        searchProperties.getRateLimit().setEnabled(true);
        searchProperties.getRateLimit().getSearch().setRequestsPerMinute(2);
        searchProperties.getRateLimit().getAnalyticsReset().setRequestsPerMinute(1);
        searchProperties.getRateLimit().getEvaluation().setRequestsPerMinute(1);
        searchProperties.getRateLimit().setMaxCostScore(100);

        searchProperties.getAdmin().setEnabled(true);
        searchProperties.getAdmin().setToken("secret-test-token");
    }

    @Test
    void search_securityHeadersAndCorrelationId_arePresentInResponse() throws Exception {
        SearchResponse response = new SearchResponse("spring", 1L, 0, 10, 1, "relevance", List.of());
        given(searchService.search("spring", null, null, null, null, null, 0, 10, "relevance")).willReturn(response);

        mockMvc.perform(get("/api/search").param("q", "spring").header("X-Request-Id", "req-12345"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-12345"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void search_rateLimitExceeded_returns429TooManyRequests() throws Exception {
        SearchResponse response = new SearchResponse("spring", 1L, 0, 10, 1, "relevance", List.of());
        given(searchService.search("spring", null, null, null, null, null, 0, 10, "relevance")).willReturn(response);

        // Requests 1 & 2 succeed
        mockMvc.perform(get("/api/search").param("q", "spring")).andExpect(status().isOk());
        mockMvc.perform(get("/api/search").param("q", "spring")).andExpect(status().isOk());

        // Request 3 exceeds 2 RPM limit
        mockMvc.perform(get("/api/search").param("q", "spring"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too many requests"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    void resetAnalytics_withoutAdminToken_returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/search/analytics/reset"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void resetAnalytics_withValidAdminToken_returns204NoContent() throws Exception {
        mockMvc.perform(post("/api/search/analytics/reset").header("X-Admin-Token", "secret-test-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void runEvaluation_withoutAdminToken_returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/search/evaluation/run"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void runEvaluation_withValidAdminToken_returns200OK() throws Exception {
        given(evaluationService.runBuiltInEvaluation()).willReturn(new EvaluationReport(
                5, 5, 0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 3.0, true, "2026-08-15"
        ));

        mockMvc.perform(post("/api/search/evaluation/run").header("X-Admin-Token", "secret-test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQueries").value(5));
    }

    @Test
    void search_expensiveQuery_returns400SearchRequestIsTooExpensive() throws Exception {
        searchProperties.getRateLimit().setMaxCostScore(5);

        mockMvc.perform(get("/api/search")
                        .param("q", "\"spring boot framework\" +java +kotlin -xml -legacy")
                        .param("page", "10")
                        .param("size", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search request is too expensive"));
    }
}
