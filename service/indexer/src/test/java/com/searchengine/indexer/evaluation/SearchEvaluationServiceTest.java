package com.searchengine.indexer.evaluation;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchResult;
import com.searchengine.indexer.model.evaluation.EvaluationQuery;
import com.searchengine.indexer.model.evaluation.EvaluationReport;
import com.searchengine.indexer.service.SearchService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SearchEvaluationServiceTest {

    @Mock
    private SearchService searchService;

    private SearchProperties searchProperties;
    private MeterRegistry meterRegistry;
    private SearchEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.getEvaluation().setEnabled(true);
        searchProperties.getEvaluation().setMinimumMrr(0.80);
        searchProperties.getEvaluation().setMinimumHitAt1(0.70);
        searchProperties.getEvaluation().setMinimumHitAt3(0.90);
        searchProperties.getEvaluation().setMinimumRecallAt10(0.95);
        searchProperties.getEvaluation().setMaximumZeroResultRate(0.10);

        meterRegistry = new SimpleMeterRegistry();
        evaluationService = new SearchEvaluationService(searchService, searchProperties, meterRegistry);
    }

    private SearchResult createResult(String urlHash, String title) {
        return new SearchResult(
                "https://example.com/" + urlHash,
                "https://example.com/" + urlHash,
                urlHash,
                title,
                "Meta description",
                "en",
                100,
                200,
                Map.of()
        );
    }

    @Test
    void evaluate_highQualityResults_calculatesMetricsAndPassesThresholds() {
        SearchResult res1 = createResult("doc-title-spring", "Spring Boot Framework");
        SearchResult res2 = createResult("doc-heading-spring", "Spring Tutorial");
        SearchResponse resp1 = new SearchResponse("spring boot", 2L, 0, 10, 1, "relevance", List.of(res1, res2));

        given(searchService.search("spring boot")).willReturn(resp1);

        EvaluationQuery query1 = new EvaluationQuery("spring boot", List.of("doc-title-spring"), List.of("doc-heading-spring"), List.of(), 1, "Title phrase test");
        EvaluationReport report = evaluationService.evaluate(List.of(query1));

        assertThat(report.totalQueries()).isEqualTo(1);
        assertThat(report.successfulQueries()).isEqualTo(1);
        assertThat(report.failedQueries()).isEqualTo(0);
        assertThat(report.mrr()).isEqualTo(1.0);
        assertThat(report.hitAt1()).isEqualTo(1.0);
        assertThat(report.hitAt3()).isEqualTo(1.0);
        assertThat(report.zeroResultRate()).isEqualTo(0.0);
        assertThat(report.passedThresholds()).isTrue();
    }

    @Test
    void evaluate_belowThresholdResults_failsThresholdCheck() {
        SearchResult resUnrelated = createResult("doc-unrelated", "Python Machine Learning");
        SearchResponse respUnrelated = new SearchResponse("spring boot", 1L, 0, 10, 1, "relevance", List.of(resUnrelated));

        given(searchService.search("spring boot")).willReturn(respUnrelated);

        EvaluationQuery query1 = new EvaluationQuery("spring boot", List.of("doc-title-spring"), List.of(), List.of(), 1, "Title phrase test");
        EvaluationReport report = evaluationService.evaluate(List.of(query1));

        assertThat(report.mrr()).isEqualTo(0.0);
        assertThat(report.hitAt1()).isEqualTo(0.0);
        assertThat(report.passedThresholds()).isFalse();
    }

    @Test
    void evaluate_emptyQueries_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> evaluationService.evaluate(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Evaluation query dataset must not be empty");
    }

    @Test
    void evaluate_tooManyQueries_throwsIllegalArgumentException() {
        List<EvaluationQuery> largeList = java.util.Collections.nCopies(51, new EvaluationQuery("q", List.of("id"), List.of(), List.of(), 1, "desc"));
        assertThatThrownBy(() -> evaluationService.evaluate(largeList))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum allowed limit");
    }
}
