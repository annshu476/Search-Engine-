package com.searchengine.indexer.service;

import com.searchengine.indexer.analytics.SearchAnalyticsService;
import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.exception.SearchQueryException;
import com.searchengine.indexer.model.dto.SearchFilter;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchResult;
import com.searchengine.indexer.model.search.ParsedSearchQuery;
import com.searchengine.indexer.model.search.SearchCacheKey;
import com.searchengine.indexer.search.ElasticsearchSearchQueryBuilder;
import com.searchengine.indexer.search.SearchQueryParser;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final List<String> SOURCE_INCLUDES = List.of(
            "url", "canonicalUrl", "urlHash", "title", "metaDescription", "language", "wordCount", "statusCode"
    );

    private final ElasticsearchClient elasticsearchClient;
    private final IndexerElasticsearchProperties indexerElasticsearchProperties;
    private final SearchProperties searchProperties;
    private final ElasticsearchSearchQueryBuilder searchQueryBuilder;
    private final SearchQueryParser searchQueryParser;
    private final SearchCacheService searchCacheService;
    private final SearchAnalyticsService searchAnalyticsService;
    private final SearchSpellCorrectionService searchSpellCorrectionService;
    private final MeterRegistry meterRegistry;

    public SearchResponse search(String query) {
        return search(query, 0, searchProperties.getMaxResults(), "relevance");
    }

    public SearchResponse search(String query, int page, int size, String sort) {
        return search(query, null, null, null, null, null, page, size, sort);
    }

    public SearchResponse search(String query, String language, String contentType, Integer statusCode, String fromDate, String toDate, int page, int size, String sort) {
        meterRegistry.counter("search.requests").increment();
        searchAnalyticsService.recordRequest();
        long requestStartTime = System.currentTimeMillis();

        try {
            if (query == null || query.isBlank()) {
                recordValidationError();
                throw new IllegalArgumentException("Search query must not be blank");
            }

            String trimmedQuery = query.trim();
            if (trimmedQuery.length() > searchProperties.getMaxQueryLength()) {
                recordValidationError();
                throw new IllegalArgumentException("Search query exceeds maximum allowed length");
            }

            ParsedSearchQuery parsedQuery;
            try {
                parsedQuery = searchQueryParser.parse(trimmedQuery);
            } catch (RuntimeException e) {
                recordValidationError();
                throw e;
            }

            int totalTerms = parsedQuery.normalTerms().size() + parsedQuery.requiredTerms().size() + parsedQuery.excludedTerms().size();
            int totalPhrases = parsedQuery.exactPhrases().size() + parsedQuery.requiredPhrases().size() + parsedQuery.excludedPhrases().size();

            if (totalTerms > searchProperties.getMaxQueryTerms() || totalPhrases > searchProperties.getMaxQueryPhrases()) {
                recordValidationError();
                throw new IllegalArgumentException("Search query is too complex");
            }

            String validatedLanguage = validateLanguage(language);
            String validatedContentType = validateContentType(contentType);
            Integer validatedStatusCode = validateStatusCode(statusCode);
            Instant parsedFromDate = parseDate(fromDate, "fromDate");
            Instant parsedToDate = parseDate(toDate, "toDate");

            if (parsedFromDate != null && parsedToDate != null && parsedFromDate.isAfter(parsedToDate)) {
                recordValidationError();
                throw new IllegalArgumentException("fromDate must not be after toDate");
            }

            if (page < 0) {
                recordValidationError();
                throw new IllegalArgumentException("Page must be greater than or equal to 0");
            }

            if (size < 1 || size > searchProperties.getMaxPageSize()) {
                recordValidationError();
                throw new IllegalArgumentException("Page size must be between 1 and " + searchProperties.getMaxPageSize());
            }

            String normalizedSort = (sort == null || sort.isBlank()) ? "relevance" : sort.trim().toLowerCase();
            if (!normalizedSort.equals("relevance") && !normalizedSort.equals("newest")) {
                recordValidationError();
                throw new IllegalArgumentException("Unsupported sort option: " + sort);
            }

            long fromOffset = (long) page * size;
            if (fromOffset < 0 || fromOffset > searchProperties.getMaxPageDepth()) {
                recordValidationError();
                throw new IllegalArgumentException("Requested page is too deep");
            }
            int from = (int) fromOffset;

            SearchCacheKey cacheKey = new SearchCacheKey(
                    trimmedQuery, page, size, normalizedSort,
                    validatedLanguage, validatedContentType, validatedStatusCode,
                    parsedFromDate, parsedToDate, searchCacheService.getConfigVersion()
            );

            SearchResponse cachedResponse = searchCacheService.get(cacheKey);
            if (cachedResponse != null) {
                long cacheDurationMs = System.currentTimeMillis() - requestStartTime;
                searchAnalyticsService.recordSuccess(trimmedQuery, cachedResponse.totalHits(), cacheDurationMs);
                return cachedResponse;
            }

            SearchFilter filter = new SearchFilter(validatedLanguage, validatedContentType, validatedStatusCode, parsedFromDate, parsedToDate);
            String indexName = indexerElasticsearchProperties.getIndexName();
            Query esQuery = searchQueryBuilder.buildSearchQuery(parsedQuery, filter);
            Highlight highlightConfig = searchQueryBuilder.buildHighlight();

            try {
                co.elastic.clients.elasticsearch.core.SearchResponse<Map> esResponse = elasticsearchClient.search(s -> {
                    s.index(indexName)
                            .from(from)
                            .size(size)
                            .source(src -> src.filter(f -> f.includes(SOURCE_INCLUDES)))
                            .timeout(searchProperties.getTimeout().toMillis() + "ms")
                            .query(esQuery);

                    if (highlightConfig != null) {
                        s.highlight(highlightConfig);
                    }

                    if ("newest".equals(normalizedSort)) {
                        s.sort(so -> so.field(f -> f.field("indexedAt").order(SortOrder.Desc)))
                         .sort(so -> so.field(f -> f.field("urlHash").order(SortOrder.Asc)));
                    } else {
                        s.sort(so -> so.score(sc -> sc.order(SortOrder.Desc)));
                    }

                    return s;
                }, Map.class);

                long durationMs = System.currentTimeMillis() - requestStartTime;
                meterRegistry.timer("search.duration", "sort", normalizedSort).record(durationMs, TimeUnit.MILLISECONDS);

                if (durationMs > searchProperties.getSlowQueryThresholdMs()) {
                    log.warn("SLOW_SEARCH_QUERY query={} durationMs={} thresholdMs={}", query, durationMs, searchProperties.getSlowQueryThresholdMs());
                }

                long totalHits = esResponse.hits().total() != null ? esResponse.hits().total().value() : 0L;
                int totalPages = size > 0 ? (int) Math.ceil((double) totalHits / size) : 0;

                List<SearchResult> results = extractResults(esResponse);

                meterRegistry.counter("search.success").increment();
                searchAnalyticsService.recordSuccess(trimmedQuery, totalHits, durationMs);
                log.info("SEARCH_QUERY_EXECUTED query={} page={} size={} sort={} totalHits={} totalPages={} returnedResults={} durationMs={}",
                        query, page, size, normalizedSort, totalHits, totalPages, results.size(), durationMs);

                SearchResponse searchResponse = new SearchResponse(query, totalHits, page, size, totalPages, normalizedSort, results);

                if (totalHits == 0 && searchProperties.getSpellCorrection().isEnabled()) {
                    try {
                        String suggestedQuery = searchSpellCorrectionService.suggestCorrection(trimmedQuery, parsedQuery);
                        if (suggestedQuery != null && !suggestedQuery.equalsIgnoreCase(trimmedQuery)) {
                            log.info("SEARCH_QUERY_CORRECTION_ATTEMPT query=\"{}\"", trimmedQuery);
                            meterRegistry.counter("search.correction.attempts").increment();

                            ParsedSearchQuery correctedParsedQuery = searchQueryParser.parse(suggestedQuery);
                            Query correctedEsQuery = searchQueryBuilder.buildSearchQuery(correctedParsedQuery, filter);

                            co.elastic.clients.elasticsearch.core.SearchResponse<Map> correctedEsResp = elasticsearchClient.search(s -> {
                                s.index(indexName)
                                        .from(from)
                                        .size(size)
                                        .source(src -> src.filter(f -> f.includes(SOURCE_INCLUDES)))
                                        .timeout(searchProperties.getTimeout().toMillis() + "ms")
                                        .query(correctedEsQuery);

                                if (highlightConfig != null) {
                                    s.highlight(highlightConfig);
                                }

                                if ("newest".equals(normalizedSort)) {
                                    s.sort(so -> so.field(f -> f.field("indexedAt").order(SortOrder.Desc)))
                                     .sort(so -> so.field(f -> f.field("urlHash").order(SortOrder.Asc)));
                                } else {
                                    s.sort(so -> so.score(sc -> sc.order(SortOrder.Desc)));
                                }

                                return s;
                            }, Map.class);

                            long correctedTotalHits = correctedEsResp.hits().total() != null ? correctedEsResp.hits().total().value() : 0L;
                            if (correctedTotalHits > 0) {
                                int correctedTotalPages = size > 0 ? (int) Math.ceil((double) correctedTotalHits / size) : 0;
                                List<SearchResult> correctedResults = extractResults(correctedEsResp);

                                log.info("SEARCH_QUERY_CORRECTED original=\"{}\" corrected=\"{}\"", trimmedQuery, suggestedQuery);
                                meterRegistry.counter("search.correction.applied").increment();

                                SearchResponse responseWithCorrection = new SearchResponse(
                                        query, suggestedQuery, correctedTotalHits, page, size, correctedTotalPages, normalizedSort, correctedResults
                                );
                                searchCacheService.put(cacheKey, responseWithCorrection);
                                return responseWithCorrection;
                            } else {
                                log.info("SEARCH_QUERY_CORRECTION_NONE query=\"{}\"", trimmedQuery);
                                meterRegistry.counter("search.correction.failed").increment();
                            }
                        }
                    } catch (Exception e) {
                        log.warn("SEARCH_QUERY_CORRECTION_FAILED query=\"{}\" error={}", trimmedQuery, e.getMessage());
                    }
                }

                searchCacheService.put(cacheKey, searchResponse);

                return searchResponse;

            } catch (Exception e) {
                searchAnalyticsService.recordFailure();
                if (isTimeoutException(e)) {
                    meterRegistry.counter("search.timeouts").increment();
                    meterRegistry.counter("search.errors").increment();
                    log.error("SEARCH_QUERY_TIMEOUT query={} index={} durationMs={} error={}", query, indexName, System.currentTimeMillis() - requestStartTime, e.getMessage());
                    throw new SearchQueryException("Search query timed out for: " + query, e);
                } else {
                    meterRegistry.counter("search.errors").increment();
                    log.error("SEARCH_QUERY_FAILED query={} index={} error={}", query, indexName, e.getMessage(), e);
                    throw new SearchQueryException("Elasticsearch search query failed for: " + query, e);
                }
            }

        } catch (RuntimeException e) {
            throw e;
        }
    }

    private List<SearchResult> extractResults(co.elastic.clients.elasticsearch.core.SearchResponse<Map> esResponse) {
        List<SearchResult> results = new ArrayList<>();
        if (esResponse.hits().hits() != null) {
            for (Hit<Map> hit : esResponse.hits().hits()) {
                Map source = hit.source();
                if (source != null) {
                    String url = (String) source.get("url");
                    String canonicalUrl = (String) source.get("canonicalUrl");
                    String urlHash = (String) source.get("urlHash");
                    String title = (String) source.get("title");
                    String metaDescription = (String) source.get("metaDescription");
                    String docLanguage = (String) source.get("language");
                    Integer wordCount = source.get("wordCount") != null ? ((Number) source.get("wordCount")).intValue() : null;
                    Integer docStatusCode = source.get("statusCode") != null ? ((Number) source.get("statusCode")).intValue() : null;

                    Map<String, List<String>> highlightsMap = new HashMap<>();
                    Map<String, List<String>> esHighlights = hit.highlight();
                    if (esHighlights != null && !esHighlights.isEmpty()) {
                        for (Map.Entry<String, List<String>> entry : esHighlights.entrySet()) {
                            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                                highlightsMap.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }

                    results.add(new SearchResult(url, canonicalUrl, urlHash, title, metaDescription, docLanguage, wordCount, docStatusCode, highlightsMap));
                }
            }
        }
        return results;
    }

    private void recordValidationError() {
        meterRegistry.counter("search.validation.errors").increment();
        searchAnalyticsService.recordValidationError();
    }

    private boolean isTimeoutException(Exception e) {
        if (e instanceof java.util.concurrent.TimeoutException || e instanceof java.net.SocketTimeoutException) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null && (msg.toLowerCase().contains("timeout") || msg.toLowerCase().contains("timed out"));
    }

    private String validateLanguage(String language) {
        if (language == null) {
            return null;
        }
        if (language.isBlank()) {
            throw new IllegalArgumentException("Language filter must not be blank");
        }
        String trimmed = language.trim();
        if (trimmed.length() > 20) {
            throw new IllegalArgumentException("Language filter exceeds maximum allowed length");
        }
        return trimmed;
    }

    private String validateContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        if (contentType.isBlank()) {
            throw new IllegalArgumentException("Content-Type filter must not be blank");
        }
        String trimmed = contentType.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Content-Type filter exceeds maximum allowed length");
        }
        return trimmed;
    }

    private Integer validateStatusCode(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("Status code must be between 100 and 599");
        }
        return statusCode;
    }

    private Instant parseDate(String dateStr, String paramName) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format for " + paramName + ": " + dateStr);
        }
    }
}
