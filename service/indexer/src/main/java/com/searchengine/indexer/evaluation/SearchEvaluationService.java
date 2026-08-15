package com.searchengine.indexer.evaluation;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchResult;
import com.searchengine.indexer.model.evaluation.EvaluationQuery;
import com.searchengine.indexer.model.evaluation.EvaluationReport;
import com.searchengine.indexer.model.evaluation.EvaluationResult;
import com.searchengine.indexer.service.SearchService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchEvaluationService {

    private final SearchService searchService;
    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;

    public EvaluationReport runBuiltInEvaluation() {
        List<EvaluationQuery> queries = getBuiltInEvaluationDataset();
        return evaluate(queries);
    }

    public EvaluationReport evaluate(List<EvaluationQuery> queries) {
        meterRegistry.counter("search.evaluation.runs").increment();
        long startTime = System.currentTimeMillis();

        if (queries == null || queries.isEmpty()) {
            throw new IllegalArgumentException("Evaluation query dataset must not be empty");
        }

        if (queries.size() > 50) {
            throw new IllegalArgumentException("Evaluation query dataset exceeds maximum allowed limit of 50 queries");
        }

        log.info("SEARCH_EVALUATION_STARTED queryCount={}", queries.size());

        int totalQueries = queries.size();
        int successfulQueries = 0;
        int failedQueries = 0;
        long totalResultHitsCount = 0;
        int zeroHitsCount = 0;

        double sumPrecision1 = 0.0, sumPrecision3 = 0.0, sumPrecision5 = 0.0, sumPrecision10 = 0.0;
        double sumRecall1 = 0.0, sumRecall3 = 0.0, sumRecall5 = 0.0, sumRecall10 = 0.0;
        double sumHit1 = 0.0, sumHit3 = 0.0, sumHit5 = 0.0, sumHit10 = 0.0;
        double sumReciprocalRank = 0.0;

        List<EvaluationResult> evaluationResults = new ArrayList<>();

        for (EvaluationQuery q : queries) {
            try {
                SearchResponse response = searchService.search(q.query());
                successfulQueries++;
                long hitsCount = response.totalHits();
                totalResultHitsCount += hitsCount;
                if (hitsCount == 0) {
                    zeroHitsCount++;
                }

                List<SearchResult> results = response.results();
                String topUrlHash = (results != null && !results.isEmpty()) ? results.get(0).urlHash() : null;

                Set<String> expectedSet = new HashSet<>();
                if (q.expectedResultIds() != null) expectedSet.addAll(q.expectedResultIds());
                if (q.preferredResultIds() != null) expectedSet.addAll(q.preferredResultIds());

                int firstRank = 0;
                if (results != null) {
                    for (int i = 0; i < results.size(); i++) {
                        String hash = results.get(i).urlHash();
                        if (expectedSet.contains(hash)) {
                            firstRank = i + 1;
                            break;
                        }
                    }
                }

                double rr = firstRank > 0 ? (1.0 / firstRank) : 0.0;
                sumReciprocalRank += rr;
                boolean isHit = firstRank > 0;

                sumHit1 += calculateHitAtK(results, expectedSet, 1);
                sumHit3 += calculateHitAtK(results, expectedSet, 3);
                sumHit5 += calculateHitAtK(results, expectedSet, 5);
                sumHit10 += calculateHitAtK(results, expectedSet, 10);

                sumPrecision1 += calculatePrecisionAtK(results, expectedSet, 1);
                sumPrecision3 += calculatePrecisionAtK(results, expectedSet, 3);
                sumPrecision5 += calculatePrecisionAtK(results, expectedSet, 5);
                sumPrecision10 += calculatePrecisionAtK(results, expectedSet, 10);

                int totalRelevant = expectedSet.size();
                sumRecall1 += calculateRecallAtK(results, expectedSet, 1, totalRelevant);
                sumRecall3 += calculateRecallAtK(results, expectedSet, 3, totalRelevant);
                sumRecall5 += calculateRecallAtK(results, expectedSet, 5, totalRelevant);
                sumRecall10 += calculateRecallAtK(results, expectedSet, 10, totalRelevant);

                evaluationResults.add(new EvaluationResult(q.query(), q.description(), isHit, rr, hitsCount, topUrlHash));
                log.info("SEARCH_EVALUATION_QUERY query=\"{}\" topResult={} reciprocalRank={}", q.query(), topUrlHash, String.format("%.2f", rr));

            } catch (Exception e) {
                failedQueries++;
                log.error("SEARCH_EVALUATION_QUERY_FAILED query=\"{}\" error={}", q.query(), e.getMessage());
            }
        }

        double mrr = round(sumReciprocalRank / totalQueries);
        double precision1 = round(sumPrecision1 / totalQueries);
        double precision3 = round(sumPrecision3 / totalQueries);
        double precision5 = round(sumPrecision5 / totalQueries);
        double precision10 = round(sumPrecision10 / totalQueries);

        double recall1 = round(sumRecall1 / totalQueries);
        double recall3 = round(sumRecall3 / totalQueries);
        double recall5 = round(sumRecall5 / totalQueries);
        double recall10 = round(sumRecall10 / totalQueries);

        double hit1 = round(sumHit1 / totalQueries);
        double hit3 = round(sumHit3 / totalQueries);
        double hit5 = round(sumHit5 / totalQueries);
        double hit10 = round(sumHit10 / totalQueries);

        double zeroResultRate = round((double) zeroHitsCount / totalQueries);
        double avgResultCount = round((double) totalResultHitsCount / totalQueries);

        SearchProperties.Evaluation evalProps = searchProperties.getEvaluation();
        boolean passed = mrr >= evalProps.getMinimumMrr()
                && hit1 >= evalProps.getMinimumHitAt1()
                && hit3 >= evalProps.getMinimumHitAt3()
                && recall10 >= evalProps.getMinimumRecallAt10()
                && zeroResultRate <= evalProps.getMaximumZeroResultRate();

        long durationMs = System.currentTimeMillis() - startTime;
        meterRegistry.timer("search.evaluation.duration").record(durationMs, TimeUnit.MILLISECONDS);

        if (passed) {
            meterRegistry.counter("search.evaluation.success").increment();
            log.info("SEARCH_EVALUATION_COMPLETED queryCount={} mrr={} hitAt3={} durationMs={}", totalQueries, mrr, hit3, durationMs);
        } else {
            meterRegistry.counter("search.evaluation.failure").increment();
            log.warn("SEARCH_EVALUATION_FAILED reason=\"Quality metrics below configured thresholds\" mrr={} hitAt1={} hitAt3={} recallAt10={} zeroResultRate={}",
                    mrr, hit1, hit3, recall10, zeroResultRate);
        }

        return new EvaluationReport(
                totalQueries, successfulQueries, failedQueries,
                precision1, precision3, precision5, precision10,
                recall1, recall3, recall5, recall10,
                mrr, hit1, hit3, hit5, hit10,
                zeroResultRate, avgResultCount, passed,
                Instant.now().toString()
        );
    }

    public List<EvaluationQuery> getBuiltInEvaluationDataset() {
        return List.of(
                new EvaluationQuery("spring boot", List.of("doc-title-spring"), List.of("doc-heading-spring"), List.of("doc-unrelated"), 1, "Exact title phrase should rank first"),
                new EvaluationQuery("spring", List.of("doc-title-spring", "doc-heading-spring"), List.of("doc-body-spring"), List.of("doc-unrelated"), 1, "Field weighted title/heading outranks body"),
                new EvaluationQuery("sprng boot", List.of("doc-title-spring"), List.of("doc-heading-spring"), List.of("doc-unrelated"), 1, "Fuzzy match should retrieve Spring Boot"),
                new EvaluationQuery("jdk", List.of("doc-java-guide"), List.of("doc-title-spring"), List.of("doc-unrelated"), 1, "Synonym JDK retrieves Java document"),
                new EvaluationQuery("spring -xml", List.of("doc-title-spring"), List.of(), List.of("doc-xml-spring"), 1, "Excluded term removes XML document")
        );
    }

    private double calculateHitAtK(List<SearchResult> results, Set<String> expectedSet, int k) {
        if (results == null || expectedSet == null || expectedSet.isEmpty()) return 0.0;
        int limit = Math.min(k, results.size());
        for (int i = 0; i < limit; i++) {
            if (expectedSet.contains(results.get(i).urlHash())) {
                return 1.0;
            }
        }
        return 0.0;
    }

    private double calculatePrecisionAtK(List<SearchResult> results, Set<String> expectedSet, int k) {
        if (results == null || expectedSet == null || expectedSet.isEmpty() || k <= 0) return 0.0;
        int limit = Math.min(k, results.size());
        int count = 0;
        for (int i = 0; i < limit; i++) {
            if (expectedSet.contains(results.get(i).urlHash())) {
                count++;
            }
        }
        return (double) count / k;
    }

    private double calculateRecallAtK(List<SearchResult> results, Set<String> expectedSet, int k, int totalRelevant) {
        if (results == null || expectedSet == null || expectedSet.isEmpty() || totalRelevant <= 0) return 0.0;
        int limit = Math.min(k, results.size());
        int count = 0;
        for (int i = 0; i < limit; i++) {
            if (expectedSet.contains(results.get(i).urlHash())) {
                count++;
            }
        }
        return (double) count / totalRelevant;
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
