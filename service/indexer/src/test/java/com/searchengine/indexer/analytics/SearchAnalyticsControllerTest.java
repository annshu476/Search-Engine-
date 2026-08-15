package com.searchengine.indexer.analytics;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.health.ElasticsearchHealthIndicator;
import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import com.searchengine.indexer.model.analytics.SearchAnalyticsSummary;
import com.searchengine.indexer.model.analytics.SearchQueryStats;
import com.searchengine.indexer.service.SearchService;
import com.searchengine.indexer.service.SearchSuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchAnalyticsController.class)
class SearchAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchAnalyticsService analyticsService;

    @MockitoBean
    private SearchProperties searchProperties;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private SearchSuggestionService searchSuggestionService;

    @MockitoBean
    private ElasticsearchHealthIndicator elasticsearchHealthIndicator;

    @MockitoBean
    private SearchDocumentIndexInitializer searchDocumentIndexInitializer;

    private SearchProperties.Analytics analyticsConfig;

    @BeforeEach
    void setUp() {
        analyticsConfig = new SearchProperties.Analytics();
        analyticsConfig.setEnabled(true);
        given(searchProperties.getAnalytics()).willReturn(analyticsConfig);
    }

    @Test
    void getSummary_returns200AndSummaryJson() throws Exception {
        SearchAnalyticsSummary summary = new SearchAnalyticsSummary(
                100L, 95L, 5L, 10L,
                0.10, 45.5, 5.2, 0.40, 0.70, 0.15,
                10L, 8L, 25L, "2026-08-15T21:45:00Z"
        );

        given(analyticsService.getSummary()).willReturn(summary);

        mockMvc.perform(get("/api/search/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSearches").value(100))
                .andExpect(jsonPath("$.successfulSearches").value(95))
                .andExpect(jsonPath("$.zeroResultSearches").value(10))
                .andExpect(jsonPath("$.cacheHitRate").value(0.40));
    }

    @Test
    void getTopQueries_returns200AndTopQueriesList() throws Exception {
        SearchQueryStats stats = new SearchQueryStats(
                "hash123", null, 50L, 48L, 2L, 40.0, 10L, 100L, 6.0, 300L, 20L, 2L, 2L, 5L
        );

        given(analyticsService.getTopQueries()).willReturn(List.of(stats));

        mockMvc.perform(get("/api/search/analytics/top-queries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queries[0].queryHash").value("hash123"))
                .andExpect(jsonPath("$.queries[0].searchCount").value(50));
    }

    @Test
    void getZeroResults_returns200AndZeroResultQueriesList() throws Exception {
        SearchQueryStats stats = new SearchQueryStats(
                "hash456", null, 10L, 10L, 10L, 30.0, 15L, 50L, 0.0, 0L, 0L, 10L, 8L, 0L
        );

        given(analyticsService.getZeroResultQueries()).willReturn(List.of(stats));

        mockMvc.perform(get("/api/search/analytics/zero-results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queries[0].queryHash").value("hash456"))
                .andExpect(jsonPath("$.queries[0].zeroResultCount").value(10));
    }

    @Test
    void getSummary_disabledAnalytics_returns404() throws Exception {
        analyticsConfig.setEnabled(false);

        mockMvc.perform(get("/api/search/analytics/summary"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetAnalytics_returns204NoContent() throws Exception {
        mockMvc.perform(post("/api/search/analytics/reset"))
                .andExpect(status().isNoContent());
    }
}
