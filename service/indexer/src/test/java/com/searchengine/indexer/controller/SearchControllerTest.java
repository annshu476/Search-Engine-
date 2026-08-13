package com.searchengine.indexer.controller;

import com.searchengine.indexer.exception.SearchQueryException;
import com.searchengine.indexer.health.ElasticsearchHealthIndicator;
import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchResult;
import com.searchengine.indexer.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private ElasticsearchHealthIndicator elasticsearchHealthIndicator;

    @MockitoBean
    private SearchDocumentIndexInitializer searchDocumentIndexInitializer;

    @Test
    void search_validQuery_returns200AndJsonStructure() throws Exception {
        SearchResult result = new SearchResult(
                "https://spring.io",
                "https://spring.io",
                "abc123hash",
                "Spring Framework",
                "Spring description",
                "en",
                450,
                200
        );
        SearchResponse response = new SearchResponse("spring", 1L, List.of(result));

        given(searchService.search("spring")).willReturn(response);

        mockMvc.perform(get("/api/search").param("q", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("spring"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].url").value("https://spring.io"))
                .andExpect(jsonPath("$.results[0].canonicalUrl").value("https://spring.io"))
                .andExpect(jsonPath("$.results[0].urlHash").value("abc123hash"))
                .andExpect(jsonPath("$.results[0].title").value("Spring Framework"))
                .andExpect(jsonPath("$.results[0].metaDescription").value("Spring description"))
                .andExpect(jsonPath("$.results[0].language").value("en"))
                .andExpect(jsonPath("$.results[0].wordCount").value(450))
                .andExpect(jsonPath("$.results[0].statusCode").value(200));
    }

    @Test
    void search_zeroResults_returns200AndEmptyResults() throws Exception {
        SearchResponse response = new SearchResponse("nonexistent", 0L, List.of());
        given(searchService.search("nonexistent")).willReturn(response);

        mockMvc.perform(get("/api/search").param("q", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("nonexistent"))
                .andExpect(jsonPath("$.totalHits").value(0))
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void search_blankQuery_returns400() throws Exception {
        given(searchService.search("   "))
                .willThrow(new IllegalArgumentException("Search query must not be blank"));

        mockMvc.perform(get("/api/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query must not be blank"));
    }

    @Test
    void search_oversizedQuery_returns400() throws Exception {
        String longQuery = "a".repeat(201);
        given(searchService.search(longQuery))
                .willThrow(new IllegalArgumentException("Search query exceeds maximum allowed length"));

        mockMvc.perform(get("/api/search").param("q", longQuery))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query exceeds maximum allowed length"));
    }

    @Test
    void search_elasticsearchFailure_returns530() throws Exception {
        given(searchService.search("java"))
                .willThrow(new SearchQueryException("Search query failed", new RuntimeException("ES down")));

        mockMvc.perform(get("/api/search").param("q", "java"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Search service temporarily unavailable"));
    }
}
