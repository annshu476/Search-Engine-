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
    void search_validQueryDefaultParams_returns200AndJsonStructure() throws Exception {
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
        SearchResponse response = new SearchResponse("spring", 1L, 0, 10, 1, "relevance", List.of(result));

        given(searchService.search("spring", 0, 10, "relevance")).willReturn(response);

        mockMvc.perform(get("/api/search").param("q", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("spring"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.sort").value("relevance"))
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
    void search_untrimmedQuery_delegatesTrimmedQueryToService() throws Exception {
        SearchResponse response = new SearchResponse("Spring Boot", 1L, 0, 10, 1, "relevance", List.of());
        given(searchService.search("   Spring Boot   ", 0, 10, "relevance")).willReturn(response);

        mockMvc.perform(get("/api/search").param("q", "   Spring Boot   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("Spring Boot"));
    }

    @Test
    void search_explicitPageAndSize_returns200() throws Exception {
        SearchResponse response = new SearchResponse("spring", 25L, 1, 20, 2, "relevance", List.of());
        given(searchService.search("spring", 1, 20, "relevance")).willReturn(response);

        mockMvc.perform(get("/api/search")
                        .param("q", "spring")
                        .param("page", "1")
                        .param("size", "20")
                        .param("sort", "relevance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("spring"))
                .andExpect(jsonPath("$.totalHits").value(25))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.sort").value("relevance"));
    }

    @Test
    void search_zeroResults_returns200AndTotalPages0() throws Exception {
        SearchResponse response = new SearchResponse("nonexistent", 0L, 0, 10, 0, "relevance", List.of());
        given(searchService.search("nonexistent", 0, 10, "relevance")).willReturn(response);

        mockMvc.perform(get("/api/search").param("q", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("nonexistent"))
                .andExpect(jsonPath("$.totalHits").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void search_negativePage_returns400() throws Exception {
        given(searchService.search("spring", -1, 10, "relevance"))
                .willThrow(new IllegalArgumentException("Page must be greater than or equal to 0"));

        mockMvc.perform(get("/api/search").param("q", "spring").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page must be greater than or equal to 0"));
    }

    @Test
    void search_sizeZero_returns400() throws Exception {
        given(searchService.search("spring", 0, 0, "relevance"))
                .willThrow(new IllegalArgumentException("Page size must be between 1 and 50"));

        mockMvc.perform(get("/api/search").param("q", "spring").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page size must be between 1 and 50"));
    }

    @Test
    void search_sizeExceedsMaximum_returns400() throws Exception {
        given(searchService.search("spring", 0, 51, "relevance"))
                .willThrow(new IllegalArgumentException("Page size must be between 1 and 50"));

        mockMvc.perform(get("/api/search").param("q", "spring").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page size must be between 1 and 50"));
    }

    @Test
    void search_invalidSort_returns400() throws Exception {
        given(searchService.search("spring", 0, 10, "invalid_sort"))
                .willThrow(new IllegalArgumentException("Unsupported sort option: invalid_sort"));

        mockMvc.perform(get("/api/search").param("q", "spring").param("sort", "invalid_sort"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported sort option: invalid_sort"));
    }

    @Test
    void search_blankQuery_returns400() throws Exception {
        given(searchService.search("   ", 0, 10, "relevance"))
                .willThrow(new IllegalArgumentException("Search query must not be blank"));

        mockMvc.perform(get("/api/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query must not be blank"));
    }

    @Test
    void search_oversizedQuery_returns400() throws Exception {
        String longQuery = "a".repeat(201);
        given(searchService.search(longQuery, 0, 10, "relevance"))
                .willThrow(new IllegalArgumentException("Search query exceeds maximum allowed length"));

        mockMvc.perform(get("/api/search").param("q", longQuery))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query exceeds maximum allowed length"));
    }

    @Test
    void search_elasticsearchFailure_returns503() throws Exception {
        given(searchService.search("java", 0, 10, "relevance"))
                .willThrow(new SearchQueryException("Search query failed", new RuntimeException("ES down")));

        mockMvc.perform(get("/api/search").param("q", "java"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Search service temporarily unavailable"));
    }
}
