package com.searchengine.indexer.security;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.search.SearchQueryParser;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchRequestCostEvaluatorTest {

    private SearchProperties searchProperties;
    private SearchQueryParser searchQueryParser;
    private MeterRegistry meterRegistry;
    private SearchRequestCostEvaluator costEvaluator;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.getRateLimit().setMaxCostScore(100);

        searchQueryParser = new SearchQueryParser();
        meterRegistry = new SimpleMeterRegistry();
        costEvaluator = new SearchRequestCostEvaluator(searchProperties, searchQueryParser, meterRegistry);
    }

    @Test
    void evaluateCost_normalQuery_succeeds() {
        assertThatCode(() -> costEvaluator.evaluateCost("spring boot", "en", null, 200, null, null, 0, 10))
                .doesNotThrowAnyException();
    }

    @Test
    void evaluateCost_excessiveCost_throwsIllegalArgumentException() {
        searchProperties.getRateLimit().setMaxCostScore(10);

        assertThatThrownBy(() -> costEvaluator.evaluateCost("\"spring boot framework\" +java +kotlin +scala -xml -legacy", "en", "text/html", 200, "2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z", 50, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search request is too expensive");
    }
}
