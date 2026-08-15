package com.searchengine.indexer.controller;

import com.searchengine.indexer.exception.SearchQueryException;
import com.searchengine.indexer.exception.SearchQuerySyntaxException;
import com.searchengine.indexer.exception.SearchSuggestionException;
import com.searchengine.indexer.health.ElasticsearchHealthIndicator;
import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchResult;
import com.searchengine.indexer.model.dto.SearchSuggestionResponse;
import com.searchengine.indexer.service.SearchService;
import com.searchengine.indexer.service.SearchSuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

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
    private SearchSuggestionService searchSuggestionService;

    @MockitoBean
    private ElasticsearchHealthIndicator elasticsearchHealthIndicator;

    @MockitoBean
    private SearchDocumentIndexInitializer searchDocumentIndexInitializer;

    @Test
    void search_validQueryDefaultParams_returns200AndJsonStructureWithHighlights() throws Exception {
        SearchResult result = new SearchResult(
                "https://spring.io",
                "https://spring.io",
                "abc123hash",
                "Spring Framework",
                "Spring description",
                "en",
                450,
                200,
                Map.of("title", List.of("<em>Spring Framework</em>"))
        );
        SearchResponse response = new SearchResponse("spring", 1L, 0, 10, 1, "relevance", List.of(result));

        given(searchService.search("spring", null, null, null, null, null, 0, 10, "relevance")).willReturn(response);

        mockMvc.perform(get("/api/search").param("q", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("spring"))
                .andExpect(jsonPath("$.correctedQuery").isEmpty())
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
                .andExpect(jsonPath("$.results[0].statusCode").value(200))
                .andExpect(jsonPath("$.results[0].highlights.title[0]").value("<em>Spring Framework</em>"));
    }

    @Test
    void search_withCorrectedQuery_returns200AndCorrectedQueryInJson() throws Exception {
        SearchResponse response = new SearchResponse("sprng boot", "spring boot", 5L, 0, 10, 1, "relevance", List.of());
        given(searchService.search("sprng boot", null, null, null, null, null, 0, 10, "relevance")).willReturn(response);

        mockMvc.perform(get("/api/search").param("q", "sprng boot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("sprng boot"))
                .andExpect(jsonPath("$.correctedQuery").value("spring boot"))
                .andExpect(jsonPath("$.totalHits").value(5));
    }

    @Test
    void search_advancedOperators_returns200() throws Exception {
        SearchResponse response = new SearchResponse("\"spring boot\" +java -xml", 1L, 0, 10, 1, "relevance", List.of());
        given(searchService.search("\"spring boot\" +java -xml", null, null, null, null, null, 0, 10, "relevance")).willReturn(response);

        mockMvc.perform(get("/api/search").param("q", "\"spring boot\" +java -xml"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("\"spring boot\" +java -xml"));
    }

    @Test
    void search_pageTooDeep_returns400() throws Exception {
        given(searchService.search("spring", null, null, null, null, null, 1001, 10, "relevance"))
                .willThrow(new IllegalArgumentException("Requested page is too deep"));

        mockMvc.perform(get("/api/search").param("q", "spring").param("page", "1001").param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requested page is too deep"));
    }

    @Test
    void search_queryTooComplex_returns400() throws Exception {
        given(searchService.search("one two three four five six", null, null, null, null, null, 0, 10, "relevance"))
                .willThrow(new IllegalArgumentException("Search query is too complex"));

        mockMvc.perform(get("/api/search").param("q", "one two three four five six"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query is too complex"));
    }

    @Test
    void search_malformedSyntax_returns400() throws Exception {
        given(searchService.search("\"unclosed quote", null, null, null, null, null, 0, 10, "relevance"))
                .willThrow(new SearchQuerySyntaxException("Invalid search query syntax"));

        mockMvc.perform(get("/api/search").param("q", "\"unclosed quote"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid search query syntax"));
    }

    @Test
    void search_withFilters_passesFilterParametersToServiceAndReturns200() throws Exception {
        SearchResponse response = new SearchResponse("spring", 1L, 0, 10, 1, "relevance", List.of());
        given(searchService.search("spring", "en", "text/html", 200, "2026-08-01T00:00:00Z", "2026-08-14T23:59:59Z", 0, 10, "relevance"))
                .willReturn(response);

        mockMvc.perform(get("/api/search")
                        .param("q", "spring")
                        .param("language", "en")
                        .param("contentType", "text/html")
                        .param("statusCode", "200")
                        .param("fromDate", "2026-08-01T00:00:00Z")
                        .param("toDate", "2026-08-14T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("spring"))
                .andExpect(jsonPath("$.totalHits").value(1));
    }

    @Test
    void search_fromDateAfterToDate_returns400() throws Exception {
        given(searchService.search("spring", null, null, null, "2026-08-15T00:00:00Z", "2026-08-14T00:00:00Z", 0, 10, "relevance"))
                .willThrow(new IllegalArgumentException("fromDate must not be after toDate"));

        mockMvc.perform(get("/api/search")
                        .param("q", "spring")
                        .param("fromDate", "2026-08-15T00:00:00Z")
                        .param("toDate", "2026-08-14T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("fromDate must not be after toDate"));
    }

    @Test
    void suggest_validPrefix_returns200AndJsonStructure() throws Exception {
        SearchSuggestionResponse response = new SearchSuggestionResponse("spr", List.of("spring", "spring boot", "spring framework"));
        given(searchSuggestionService.suggest("spr")).willReturn(response);

        mockMvc.perform(get("/api/search/suggest").param("q", "spr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("spr"))
                .andExpect(jsonPath("$.suggestions[0]").value("spring"))
                .andExpect(jsonPath("$.suggestions[1]").value("spring boot"))
                .andExpect(jsonPath("$.suggestions[2]").value("spring framework"));
    }

    @Test
    void suggest_blankQuery_returns400() throws Exception {
        given(searchSuggestionService.suggest("   "))
                .willThrow(new IllegalArgumentException("Search query must not be blank"));

        mockMvc.perform(get("/api/search/suggest").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query must not be blank"));
    }

    @Test
    void suggest_oneCharacterPrefix_returns400() throws Exception {
        given(searchSuggestionService.suggest("s"))
                .willThrow(new IllegalArgumentException("Search prefix must contain at least 2 characters"));

        mockMvc.perform(get("/api/search/suggest").param("q", "s"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search prefix must contain at least 2 characters"));
    }

    @Test
    void suggest_oversizedQuery_returns400() throws Exception {
        String longQuery = "a".repeat(201);
        given(searchSuggestionService.suggest(longQuery))
                .willThrow(new IllegalArgumentException("Search query exceeds maximum allowed length"));

        mockMvc.perform(get("/api/search/suggest").param("q", longQuery))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query exceeds maximum allowed length"));
    }

    @Test
    void suggest_elasticsearchFailure_returns503() throws Exception {
        given(searchSuggestionService.suggest("spr"))
                .willThrow(new SearchSuggestionException("Search suggestion service temporarily unavailable", new RuntimeException("ES down")));

        mockMvc.perform(get("/api/search/suggest").param("q", "spr"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Search suggestion service temporarily unavailable"));
    }

    @Test
    void search_untrimmedQuery_delegatesTrimmedQueryToService() throws Exception {
        SearchResponse response = new SearchResponse("Spring Boot", 1L, 0, 10, 1, "relevance", List.of());
        given(searchService.search("   Spring Boot   ", null, null, null, null, null, 0, 10, "relevance")).willReturn(response);

        mockMvc.perform(get("/api/search").param("q", "   Spring Boot   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("Spring Boot"));
    }

    @Test
    void search_explicitPageAndSize_returns200() throws Exception {
        SearchResponse response = new SearchResponse("spring", 25L, 1, 20, 2, "relevance", List.of());
        given(searchService.search("spring", null, null, null, null, null, 1, 20, "relevance")).willReturn(response);

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
        given(searchService.search("nonexistent", null, null, null, null, null, 0, 10, "relevance")).willReturn(response);

        mockMvc.perform(get("/api/search").param("q", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("nonexistent"))
                .andExpect(jsonPath("$.totalHits").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void search_negativePage_returns400() throws Exception {
        given(searchService.search("spring", null, null, null, null, null, -1, 10, "relevance"))
                .willThrow(new IllegalArgumentException("Page must be greater than or equal to 0"));

        mockMvc.perform(get("/api/search").param("q", "spring").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page must be greater than or equal to 0"));
    }

    @Test
    void search_sizeZero_returns400() throws Exception {
        given(searchService.search("spring", null, null, null, null, null, 0, 0, "relevance"))
                .willThrow(new IllegalArgumentException("Page size must be between 1 and 50"));

        mockMvc.perform(get("/api/search").param("q", "spring").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page size must be between 1 and 50"));
    }

    @Test
    void search_sizeExceedsMaximum_returns400() throws Exception {
        given(searchService.search("spring", null, null, null, null, null, 0, 51, "relevance"))
                .willThrow(new IllegalArgumentException("Page size must be between 1 and 50"));

        mockMvc.perform(get("/api/search").param("q", "spring").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page size must be between 1 and 50"));
    }

    @Test
    void search_invalidSort_returns400() throws Exception {
        given(searchService.search("spring", null, null, null, null, null, 0, 10, "invalid_sort"))
                .willThrow(new IllegalArgumentException("Unsupported sort option: invalid_sort"));

        mockMvc.perform(get("/api/search").param("q", "spring").param("sort", "invalid_sort"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported sort option: invalid_sort"));
    }

    @Test
    void search_blankQuery_returns400() throws Exception {
        given(searchService.search("   ", null, null, null, null, null, 0, 10, "relevance"))
                .willThrow(new IllegalArgumentException("Search query must not be blank"));

        mockMvc.perform(get("/api/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query must not be blank"));
    }

    @Test
    void search_oversizedQuery_returns400() throws Exception {
        String longQuery = "a".repeat(201);
        given(searchService.search(longQuery, null, null, null, null, null, 0, 10, "relevance"))
                .willThrow(new IllegalArgumentException("Search query exceeds maximum allowed length"));

        mockMvc.perform(get("/api/search").param("q", longQuery))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query exceeds maximum allowed length"));
    }

    @Test
    void search_elasticsearchFailure_returns503() throws Exception {
        given(searchService.search("java", null, null, null, null, null, 0, 10, "relevance"))
                .willThrow(new SearchQueryException("Search query failed", new RuntimeException("ES down")));

        mockMvc.perform(get("/api/search").param("q", "java"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Search service temporarily unavailable"));
    }
}
