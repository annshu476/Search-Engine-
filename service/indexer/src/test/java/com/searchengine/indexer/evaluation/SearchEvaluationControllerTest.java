package com.searchengine.indexer.evaluation;

import com.searchengine.indexer.health.ElasticsearchHealthIndicator;
import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import com.searchengine.indexer.model.evaluation.EvaluationReport;
import com.searchengine.indexer.service.SearchService;
import com.searchengine.indexer.service.SearchSuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchEvaluationController.class)
class SearchEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchEvaluationService evaluationService;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private SearchSuggestionService searchSuggestionService;

    @MockitoBean
    private ElasticsearchHealthIndicator elasticsearchHealthIndicator;

    @MockitoBean
    private SearchDocumentIndexInitializer searchDocumentIndexInitializer;

    @Test
    void runEvaluation_returns200AndReportJson() throws Exception {
        EvaluationReport report = new EvaluationReport(
                5, 5, 0,
                1.0, 0.9, 0.9, 0.9,
                1.0, 1.0, 1.0, 1.0,
                1.0, 1.0, 1.0, 1.0, 1.0,
                0.0, 3.5, true,
                "2026-08-15T21:30:00Z"
        );

        given(evaluationService.runBuiltInEvaluation()).willReturn(report);

        mockMvc.perform(post("/api/search/evaluation/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQueries").value(5))
                .andExpect(jsonPath("$.successfulQueries").value(5))
                .andExpect(jsonPath("$.failedQueries").value(0))
                .andExpect(jsonPath("$.mrr").value(1.0))
                .andExpect(jsonPath("$.hitAt1").value(1.0))
                .andExpect(jsonPath("$.passedThresholds").value(true));
    }
}
