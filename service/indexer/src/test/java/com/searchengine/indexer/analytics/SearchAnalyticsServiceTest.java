package com.searchengine.indexer.analytics;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.analytics.SearchAnalyticsEvent;
import com.searchengine.indexer.model.analytics.SearchAnalyticsSummary;
import com.searchengine.indexer.model.analytics.SearchQueryStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SearchAnalyticsServiceTest {

    private SearchProperties searchProperties;
    private MeterRegistry meterRegistry;
    private SearchAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.getAnalytics().setEnabled(true);
        searchProperties.getAnalytics().setMaximumQueryEntries(5000);
        searchProperties.getAnalytics().setTopQueryLimit(20);
        searchProperties.getAnalytics().setNormalizedQueryStorage(false);

        meterRegistry = new SimpleMeterRegistry();
        analyticsService = new SearchAnalyticsService(searchProperties, meterRegistry);
        analyticsService.init();
    }

    private SearchAnalyticsEvent createEvent(String query, boolean success, boolean zeroResults, int count, long durationMs, boolean cacheHit) {
        String hash = SearchAnalyticsService.computeQueryHash(query);
        return new SearchAnalyticsEvent(
                Instant.now(), hash, query, success, zeroResults, count, count, durationMs,
                0, 10, "relevance", cacheHit, true, false, false, false, null, false, false
        );
    }

    @Test
    void recordSearch_successfulSearch_updatesSummaryAndStats() {
        analyticsService.recordSearch(createEvent("spring boot", true, false, 5, 50, false));

        SearchAnalyticsSummary summary = analyticsService.getSummary();
        assertThat(summary.totalSearches()).isEqualTo(1);
        assertThat(summary.successfulSearches()).isEqualTo(1);
        assertThat(summary.zeroResultSearches()).isEqualTo(0);
        assertThat(summary.averageDurationMs()).isEqualTo(50.0);
        assertThat(summary.averageResultCount()).isEqualTo(5.0);

        List<SearchQueryStats> topQueries = analyticsService.getTopQueries();
        assertThat(topQueries).hasSize(1);
        assertThat(topQueries.get(0).searchCount()).isEqualTo(1);
        assertThat(topQueries.get(0).query()).isNull(); // query storage disabled
    }

    @Test
    void recordSearch_queryPrivacyEnabled_storesQueryText() {
        searchProperties.getAnalytics().setNormalizedQueryStorage(true);

        analyticsService.recordSearch(createEvent("spring boot", true, false, 5, 50, false));

        List<SearchQueryStats> topQueries = analyticsService.getTopQueries();
        assertThat(topQueries).hasSize(1);
        assertThat(topQueries.get(0).query()).isEqualTo("spring boot");
    }

    @Test
    void recordSearch_zeroResultSearch_incrementsZeroResultStats() {
        analyticsService.recordSearch(createEvent("nonexistentterm", true, true, 0, 30, false));

        SearchAnalyticsSummary summary = analyticsService.getSummary();
        assertThat(summary.zeroResultSearches()).isEqualTo(1);
        assertThat(summary.zeroResultRate()).isEqualTo(1.0);

        List<SearchQueryStats> zeroResultQueries = analyticsService.getZeroResultQueries();
        assertThat(zeroResultQueries).hasSize(1);
        assertThat(zeroResultQueries.get(0).zeroResultCount()).isEqualTo(1);
    }

    @Test
    void recordSearch_cacheHit_updatesCacheHitRate() {
        analyticsService.recordSearch(createEvent("spring", true, false, 10, 100, false));
        analyticsService.recordSearch(createEvent("spring", true, false, 10, 5, true));

        SearchAnalyticsSummary summary = analyticsService.getSummary();
        assertThat(summary.totalSearches()).isEqualTo(2);
        assertThat(summary.cacheHitRate()).isEqualTo(0.5);
    }

    @Test
    void recordFailure_incrementsFailedSearches() {
        analyticsService.recordFailure();

        SearchAnalyticsSummary summary = analyticsService.getSummary();
        assertThat(summary.totalSearches()).isEqualTo(1);
        assertThat(summary.failedSearches()).isEqualTo(1);
    }

    @Test
    void recordSearch_disabledAnalytics_doesNotRecord() {
        searchProperties.getAnalytics().setEnabled(false);
        analyticsService.init();

        analyticsService.recordSearch(createEvent("spring", true, false, 5, 50, false));

        assertThat(analyticsService.getSummary()).isNull();
        assertThat(analyticsService.getTopQueries()).isEmpty();
    }

    @Test
    void recordSearch_concurrentRecording_handlesThreadSafety() throws Exception {
        int threads = 10;
        int perThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        analyticsService.recordSearch(createEvent("concurrent query", true, false, 2, 20, false));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        SearchAnalyticsSummary summary = analyticsService.getSummary();
        assertThat(summary.totalSearches()).isEqualTo(threads * perThread);
    }

    @Test
    void recordSearch_exceptionIsolation_swallowsErrorsGracefully() {
        assertThatCode(() -> analyticsService.recordSearch(null))
                .doesNotThrowAnyException();
    }
}
