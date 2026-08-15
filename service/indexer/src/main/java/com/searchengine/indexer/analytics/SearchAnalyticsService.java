package com.searchengine.indexer.analytics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.analytics.QueryStats;
import com.searchengine.indexer.model.analytics.SearchAnalyticsEvent;
import com.searchengine.indexer.model.analytics.SearchAnalyticsSnapshot;
import com.searchengine.indexer.model.analytics.SearchAnalyticsSummary;
import com.searchengine.indexer.model.analytics.SearchQueryStats;
import com.searchengine.indexer.model.analytics.ZeroResultsResponse;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchAnalyticsService {

    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;

    private final LongAdder totalSearches = new LongAdder();
    private final LongAdder successfulSearches = new LongAdder();
    private final LongAdder failedSearches = new LongAdder();
    private final LongAdder zeroResultSearches = new LongAdder();
    private final LongAdder totalDurationMs = new LongAdder();
    private final LongAdder totalResultHits = new LongAdder();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder fuzzyUsageCount = new LongAdder();
    private final LongAdder synonymUsageCount = new LongAdder();
    private final LongAdder correctionAttemptsCount = new LongAdder();
    private final LongAdder correctionAppliedCount = new LongAdder();

    private Cache<String, QueryAccumulator> queryCache;

    @PostConstruct
    public void init() {
        if (searchProperties.getAnalytics().isEnabled()) {
            this.queryCache = Caffeine.newBuilder()
                    .maximumSize(searchProperties.getAnalytics().getMaximumQueryEntries())
                    .expireAfterWrite(searchProperties.getAnalytics().getRetention())
                    .build();
            log.info("SEARCH_ANALYTICS_INITIALIZED enabled=true maxEntries={} retention={}",
                    searchProperties.getAnalytics().getMaximumQueryEntries(), searchProperties.getAnalytics().getRetention());
        } else {
            log.info("SEARCH_ANALYTICS_INITIALIZED enabled=false");
        }
    }

    public static String computeQueryHash(String queryText) {
        if (queryText == null) return "";
        String normalized = queryText.trim().toLowerCase();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(normalized.hashCode());
        }
    }

    public void recordSearch(SearchAnalyticsEvent event) {
        if (!searchProperties.getAnalytics().isEnabled() || event == null) {
            return;
        }

        try {
            totalSearches.increment();
            meterRegistry.counter("search.analytics.requests").increment();

            if (event.successful()) {
                successfulSearches.increment();
                meterRegistry.counter("search.analytics.success").increment();
            } else {
                failedSearches.increment();
                meterRegistry.counter("search.analytics.errors").increment();
            }

            if (event.zeroResults()) {
                zeroResultSearches.increment();
                meterRegistry.counter("search.analytics.zero_results").increment();
            }

            totalDurationMs.add(event.durationMs());
            totalResultHits.add(event.resultCount());

            if (event.cacheHit()) {
                cacheHits.increment();
            }
            if (event.fuzzyEnabled()) {
                fuzzyUsageCount.increment();
            }
            if (event.synonymExpansionUsed()) {
                synonymUsageCount.increment();
                meterRegistry.counter("search.analytics.synonym_expansions").increment();
            }
            if (event.spellCorrectionAttempted()) {
                correctionAttemptsCount.increment();
                meterRegistry.counter("search.analytics.corrections.attempted").increment();
            }
            if (event.spellCorrectionApplied()) {
                correctionAppliedCount.increment();
                meterRegistry.counter("search.analytics.corrections.applied").increment();
            }

            if (queryCache != null && event.queryHash() != null && !event.queryHash().isBlank()) {
                QueryAccumulator acc = queryCache.get(event.queryHash(), k -> new QueryAccumulator(
                        event.queryHash(),
                        event.normalizedQuery()
                ));
                if (acc != null) {
                    acc.record(event);
                }
            }

        } catch (Exception e) {
            log.warn("SEARCH_ANALYTICS_RECORDING_FAILED error={}", e.getMessage());
        }
    }

    public void recordRequest() {
        // Backwards compatibility delegate
    }

    public void recordSuccess(String query, long totalHits, long durationMs) {
        // Backwards compatibility delegate
    }

    public void recordFailure() {
        if (!searchProperties.getAnalytics().isEnabled()) {
            return;
        }
        try {
            totalSearches.increment();
            failedSearches.increment();
            meterRegistry.counter("search.analytics.requests").increment();
            meterRegistry.counter("search.analytics.errors").increment();
        } catch (Exception e) {
            log.warn("SEARCH_ANALYTICS_RECORDING_FAILED error={}", e.getMessage());
        }
    }

    public void recordValidationError() {
        if (!searchProperties.getAnalytics().isEnabled()) {
            return;
        }
        try {
            meterRegistry.counter("search.analytics.validation_errors").increment();
        } catch (Exception e) {
            log.warn("SEARCH_ANALYTICS_RECORDING_FAILED error={}", e.getMessage());
        }
    }

    public SearchAnalyticsSummary getSummary() {
        if (!searchProperties.getAnalytics().isEnabled()) {
            return null;
        }

        long total = totalSearches.sum();
        long success = successfulSearches.sum();
        long failed = failedSearches.sum();
        long zeroResults = zeroResultSearches.sum();
        long duration = totalDurationMs.sum();
        long resultHits = totalResultHits.sum();
        long cacheHitCount = cacheHits.sum();
        long fuzzyCount = fuzzyUsageCount.sum();
        long synonymCount = synonymUsageCount.sum();
        long correctionAttempts = correctionAttemptsCount.sum();
        long correctionApplied = correctionAppliedCount.sum();

        double zeroResultRate = total > 0 ? round((double) zeroResults / total) : 0.0;
        double avgDurationMs = total > 0 ? round((double) duration / total) : 0.0;
        double avgResultCount = total > 0 ? round((double) resultHits / total) : 0.0;
        double cacheHitRate = total > 0 ? round((double) cacheHitCount / total) : 0.0;
        double fuzzyUsage = total > 0 ? round((double) fuzzyCount / total) : 0.0;
        double synonymUsage = total > 0 ? round((double) synonymCount / total) : 0.0;

        long trackedCount = queryCache != null ? queryCache.estimatedSize() : 0L;
        meterRegistry.gauge("search.analytics.tracked_queries", trackedCount);

        return new SearchAnalyticsSummary(
                total, success, failed, zeroResults,
                zeroResultRate, avgDurationMs, avgResultCount,
                cacheHitRate, fuzzyUsage, synonymUsage,
                correctionAttempts, correctionApplied,
                trackedCount, Instant.now().toString()
        );
    }

    public SearchAnalyticsSnapshot getSnapshot() {
        SearchAnalyticsSummary summary = getSummary();
        if (summary == null) {
            return new SearchAnalyticsSnapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0.0, 0L, 0L, List.of());
        }
        long resultful = Math.max(0L, summary.successfulSearches() - summary.zeroResultSearches());
        long cacheHitCount = cacheHits.sum();
        long cacheMissCount = Math.max(0L, summary.totalSearches() - cacheHitCount);
        return new SearchAnalyticsSnapshot(
                summary.totalSearches(),
                summary.successfulSearches(),
                summary.failedSearches(),
                0L,
                summary.zeroResultSearches(),
                resultful,
                cacheHitCount,
                cacheMissCount,
                summary.averageDurationMs(),
                0L,
                summary.trackedQueries(),
                List.of()
        );
    }

    public ZeroResultsResponse getZeroResults() {
        List<SearchQueryStats> statsList = getZeroResultQueries();
        List<ZeroResultsResponse.QueryZeroResultItem> list = statsList.stream()
                .map(s -> new ZeroResultsResponse.QueryZeroResultItem(s.query() != null ? s.query() : s.queryHash(), s.zeroResultCount()))
                .toList();
        return new ZeroResultsResponse(list);
    }

    public List<SearchQueryStats> getTopQueries() {
        if (!searchProperties.getAnalytics().isEnabled() || queryCache == null) {
            return List.of();
        }

        queryCache.cleanUp();
        int limit = searchProperties.getAnalytics().getTopQueryLimit();
        boolean storeText = searchProperties.getAnalytics().isNormalizedQueryStorage();

        return queryCache.asMap().values().stream()
                .map(acc -> acc.toSnapshot(storeText))
                .sorted((a, b) -> {
                    int cmp = Long.compare(b.searchCount(), a.searchCount());
                    if (cmp != 0) return cmp;
                    return a.queryHash().compareTo(b.queryHash());
                })
                .limit(limit)
                .toList();
    }

    public List<SearchQueryStats> getZeroResultQueries() {
        if (!searchProperties.getAnalytics().isEnabled() || queryCache == null) {
            return List.of();
        }

        queryCache.cleanUp();
        int limit = searchProperties.getAnalytics().getTopQueryLimit();
        boolean storeText = searchProperties.getAnalytics().isNormalizedQueryStorage();

        return queryCache.asMap().values().stream()
                .map(acc -> acc.toSnapshot(storeText))
                .filter(s -> s.zeroResultCount() > 0)
                .sorted((a, b) -> {
                    int cmp = Long.compare(b.zeroResultCount(), a.zeroResultCount());
                    if (cmp != 0) return cmp;
                    return a.queryHash().compareTo(b.queryHash());
                })
                .limit(limit)
                .toList();
    }

    public void reset() {
        totalSearches.reset();
        successfulSearches.reset();
        failedSearches.reset();
        zeroResultSearches.reset();
        totalDurationMs.reset();
        totalResultHits.reset();
        cacheHits.reset();
        fuzzyUsageCount.reset();
        synonymUsageCount.reset();
        correctionAttemptsCount.reset();
        correctionAppliedCount.reset();
        if (queryCache != null) {
            queryCache.invalidateAll();
        }
    }

    private static double round(double val) {
        return Math.round(val * 10000.0) / 10000.0;
    }

    private static class QueryAccumulator {
        private final String queryHash;
        private final String queryText;
        private final LongAdder searchCount = new LongAdder();
        private final LongAdder successfulCount = new LongAdder();
        private final LongAdder zeroResultCount = new LongAdder();
        private final LongAdder totalDurationMs = new LongAdder();
        private final AtomicLong minDurationMs = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxDurationMs = new AtomicLong(0);
        private final LongAdder totalResultCount = new LongAdder();
        private final LongAdder cacheHitCount = new LongAdder();
        private final LongAdder correctionAttemptCount = new LongAdder();
        private final LongAdder correctionAppliedCount = new LongAdder();
        private final LongAdder synonymExpansionCount = new LongAdder();

        public QueryAccumulator(String queryHash, String queryText) {
            this.queryHash = queryHash;
            this.queryText = queryText;
        }

        public void record(SearchAnalyticsEvent event) {
            searchCount.increment();
            if (event.successful()) successfulCount.increment();
            if (event.zeroResults()) zeroResultCount.increment();

            totalDurationMs.add(event.durationMs());
            updateMin(event.durationMs());
            updateMax(event.durationMs());

            totalResultCount.add(event.resultCount());
            if (event.cacheHit()) cacheHitCount.increment();
            if (event.spellCorrectionAttempted()) correctionAttemptCount.increment();
            if (event.spellCorrectionApplied()) correctionAppliedCount.increment();
            if (event.synonymExpansionUsed()) synonymExpansionCount.increment();
        }

        private void updateMin(long val) {
            minDurationMs.accumulateAndGet(val, Math::min);
        }

        private void updateMax(long val) {
            maxDurationMs.accumulateAndGet(val, Math::max);
        }

        public SearchQueryStats toSnapshot(boolean exposeQueryText) {
            long total = searchCount.sum();
            long durationSum = totalDurationMs.sum();
            long hitsSum = totalResultCount.sum();
            long minDur = minDurationMs.get();
            long maxDur = maxDurationMs.get();

            double avgDur = total > 0 ? (double) durationSum / total : 0.0;
            double avgHits = total > 0 ? (double) hitsSum / total : 0.0;

            return new SearchQueryStats(
                    queryHash,
                    exposeQueryText ? queryText : null,
                    total,
                    successfulCount.sum(),
                    zeroResultCount.sum(),
                    round(avgDur),
                    minDur == Long.MAX_VALUE ? 0L : minDur,
                    maxDur,
                    round(avgHits),
                    hitsSum,
                    cacheHitCount.sum(),
                    correctionAttemptCount.sum(),
                    correctionAppliedCount.sum(),
                    synonymExpansionCount.sum()
            );
        }
    }
}
