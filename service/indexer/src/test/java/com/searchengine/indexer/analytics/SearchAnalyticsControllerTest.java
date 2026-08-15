package com.searchengine.indexer.analytics;

import com.searchengine.indexer.model.analytics.SearchAnalyticsSnapshot;
import com.searchengine.indexer.model.analytics.ZeroResultsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchAnalyticsController.class)
class SearchAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchAnalyticsService searchAnalyticsService;

    @Test
    void getAnalytics_returns200AndSnapshotJson() throws Exception {
        SearchAnalyticsSnapshot snapshot = new SearchAnalyticsSnapshot(
                100L, 90L, 5L, 5L, 10L, 80L, 30L, 60L, 42.5, 300L, 15L, List.of()
        );
        given(searchAnalyticsService.getSnapshot()).willReturn(snapshot);

        mockMvc.perform(get("/api/search/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(100))
                .andExpect(jsonPath("$.successfulRequests").value(90))
                .andExpect(jsonPath("$.failedRequests").value(5))
                .andExpect(jsonPath("$.validationErrors").value(5))
                .andExpect(jsonPath("$.zeroResultSearches").value(10))
                .andExpect(jsonPath("$.resultfulSearches").value(80))
                .andExpect(jsonPath("$.cacheHits").value(30))
                .andExpect(jsonPath("$.cacheMisses").value(60))
                .andExpect(jsonPath("$.averageLatencyMs").value(42.5))
                .andExpect(jsonPath("$.maxLatencyMs").value(300))
                .andExpect(jsonPath("$.trackedQueries").value(15));
    }

    @Test
    void getZeroResults_returns200AndQueriesList() throws Exception {
        ZeroResultsResponse response = new ZeroResultsResponse(List.of(
                new ZeroResultsResponse.QueryZeroResultItem("sprng boot", 12L),
                new ZeroResultsResponse.QueryZeroResultItem("java microservice", 8L)
        ));
        given(searchAnalyticsService.getZeroResults()).willReturn(response);

        mockMvc.perform(get("/api/search/analytics/zero-results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queries[0].query").value("sprng boot"))
                .andExpect(jsonPath("$.queries[0].count").value(12))
                .andExpect(jsonPath("$.queries[1].query").value("java microservice"))
                .andExpect(jsonPath("$.queries[1].count").value(8));
    }

    @Test
    void resetAnalytics_returns204NoContent() throws Exception {
        mockMvc.perform(post("/api/search/analytics/reset"))
                .andExpect(status().isNoContent());

        verify(searchAnalyticsService).reset();
    }
}
