package com.searchengine.indexer.analytics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.analytics.QueryStats;
import com.searchengine.indexer.model.analytics.SearchAnalyticsSnapshot;
import com.searchengine.indexer.model.analytics.ZeroResultsResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchAnalyticsService {

    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;

    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder validationErrors = new LongAdder();
    private final LongAdder zeroResultSearches = new LongAdder();
    private final LongAdder resultfulSearches = new LongAdder();
    private final LongAdder totalLatencyMs = new LongAdder();
    private final AtomicLong maxLatencyMs = new AtomicLong(0L);

    private Cache<String, MutableQueryStats> queryCache;

    @PostConstruct
    public void init() {
        if (searchProperties.getAnalytics().isEnabled()) {
            this.queryCache = Caffeine.newBuilder()
                    .maximumSize(searchProperties.getAnalytics().getMaximumQueryEntries())
                    .expireAfterWrite(searchProperties.getAnalytics().getQueryRetention())
                    .build();

            log.info("SEARCH_ANALYTICS_INITIALIZED enabled=true maximumQueryEntries={} topQueryLimit={} queryRetention={}",
                    searchProperties.getAnalytics().getMaximumQueryEntries(),
                    searchProperties.getAnalytics().getTopQueryLimit(),
                    searchProperties.getAnalytics().getQueryRetention());
        } else {
            log.info("SEARCH_ANALYTICS_INITIALIZED enabled=false");
        }
    }

    public boolean isAnalyticsEnabled() {
        return searchProperties.getAnalytics().isEnabled();
    }

    public void recordRequest() {
        if (!isAnalyticsEnabled()) return;
        totalRequests.increment();
        meterRegistry.counter("search.analytics.requests").increment();
    }

    public void recordValidationError() {
        if (!isAnalyticsEnabled()) return;
        validationErrors.increment();
        meterRegistry.counter("search.analytics.validation_errors").increment();
    }

    public void recordSuccess(String query, long totalHits, long durationMs) {
        if (!isAnalyticsEnabled()) return;

        successfulRequests.increment();
        meterRegistry.counter("search.analytics.success").increment();

        totalLatencyMs.add(durationMs);
        maxLatencyMs.accumulateAndGet(durationMs, Math::max);
        Timer.builder("search.analytics.duration").register(meterRegistry).record(durationMs, TimeUnit.MILLISECONDS);

        if (totalHits == 0) {
            zeroResultSearches.increment();
            meterRegistry.counter("search.analytics.zero_results").increment();
        } else {
            resultfulSearches.increment();
            meterRegistry.counter("search.analytics.resultful").increment();
        }

        if (query != null && !query.isBlank() && queryCache != null) {
            String trimmed = query.trim();
            MutableQueryStats stats = queryCache.get(trimmed, q -> new MutableQueryStats(q));
            if (stats != null) {
                stats.count.increment();
                stats.totalHits.add(totalHits);
                if (totalHits == 0) {
                    stats.zeroResultCount.increment();
                }
                stats.lastSeenAt.set(Instant.now());
            }
        }
    }

    public void recordFailure() {
        if (!isAnalyticsEnabled()) return;
        failedRequests.increment();
        meterRegistry.counter("search.analytics.errors").increment();
    }

    public SearchAnalyticsSnapshot getSnapshot() {
        if (queryCache != null) {
            queryCache.cleanUp();
        }

        long totalReq = totalRequests.sum();
        long successReq = successfulRequests.sum();
        long failedReq = failedRequests.sum();
        long valErrors = validationErrors.sum();
        long zeroResults = zeroResultSearches.sum();
        long resultful = resultfulSearches.sum();
        long totalLat = totalLatencyMs.sum();
        long maxLat = maxLatencyMs.get();
        double avgLat = successReq > 0 ? (double) totalLat / successReq : 0.0;

        double cacheHitsCount = meterRegistry.counter("search.cache.hit", "operation", "search").count();
        double cacheMissesCount = meterRegistry.counter("search.cache.miss", "operation", "search").count();

        long trackedQueryCount = queryCache != null ? queryCache.estimatedSize() : 0L;
        int limit = searchProperties.getAnalytics().getTopQueryLimit();

        List<QueryStats> topQueriesList = queryCache == null ? List.of() : queryCache.asMap().values().stream()
                .map(MutableQueryStats::toSnapshot)
                .sorted(Comparator.comparingLong(QueryStats::count).reversed()
                        .thenComparing(Comparator.comparingLong(QueryStats::zeroResultCount).reversed())
                        .thenComparing(QueryStats::query))
                .limit(limit)
                .toList();

        return new SearchAnalyticsSnapshot(
                totalReq, successReq, failedReq, valErrors,
                zeroResults, resultful, (long) cacheHitsCount, (long) cacheMissesCount,
                avgLat, maxLat, trackedQueryCount, topQueriesList
        );
    }

    public ZeroResultsResponse getZeroResults() {
        if (queryCache != null) {
            queryCache.cleanUp();
        }

        int limit = searchProperties.getAnalytics().getTopQueryLimit();
        List<ZeroResultsResponse.QueryZeroResultItem> items = queryCache == null ? List.of() : queryCache.asMap().values().stream()
                .map(MutableQueryStats::toSnapshot)
                .filter(qs -> qs.zeroResultCount() > 0)
                .sorted(Comparator.comparingLong(QueryStats::zeroResultCount).reversed()
                        .thenComparing(QueryStats::query))
                .limit(limit)
                .map(qs -> new ZeroResultsResponse.QueryZeroResultItem(qs.query(), qs.zeroResultCount()))
                .toList();

        return new ZeroResultsResponse(items);
    }

    public void reset() {
        totalRequests.reset();
        successfulRequests.reset();
        failedRequests.reset();
        validationErrors.reset();
        zeroResultSearches.reset();
        resultfulSearches.reset();
        totalLatencyMs.reset();
        maxLatencyMs.set(0L);

        if (queryCache != null) {
            queryCache.invalidateAll();
        }

        log.info("SEARCH_ANALYTICS_RESET");
    }

    private static class MutableQueryStats {
        private final String query;
        private final LongAdder count = new LongAdder();
        private final LongAdder zeroResultCount = new LongAdder();
        private final LongAdder totalHits = new LongAdder();
        private final AtomicReference<Instant> lastSeenAt = new AtomicReference<>(Instant.now());

        public MutableQueryStats(String query) {
            this.query = query;
        }

        public QueryStats toSnapshot() {
            return new QueryStats(query, count.sum(), zeroResultCount.sum(), totalHits.sum(), lastSeenAt.get());
        }
    }
}
