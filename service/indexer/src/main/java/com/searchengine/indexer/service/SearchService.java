package com.searchengine.indexer.service;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.exception.SearchQueryException;
import com.searchengine.indexer.model.dto.SearchFilter;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchResult;
import com.searchengine.indexer.search.ElasticsearchSearchQueryBuilder;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final IndexerElasticsearchProperties indexerElasticsearchProperties;
    private final SearchProperties searchProperties;
    private final ElasticsearchSearchQueryBuilder searchQueryBuilder;

    public SearchResponse search(String query) {
        return search(query, 0, searchProperties.getMaxResults(), "relevance");
    }

    public SearchResponse search(String query, int page, int size, String sort) {
        return search(query, null, null, null, null, null, page, size, sort);
    }

    public SearchResponse search(String query, String language, String contentType, Integer statusCode, String fromDate, String toDate, int page, int size, String sort) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        String trimmedQuery = query.trim();
        if (trimmedQuery.length() > searchProperties.getMaxQueryLength()) {
            throw new IllegalArgumentException("Search query exceeds maximum allowed length");
        }

        String validatedLanguage = validateLanguage(language);
        String validatedContentType = validateContentType(contentType);
        Integer validatedStatusCode = validateStatusCode(statusCode);
        Instant parsedFromDate = parseDate(fromDate, "fromDate");
        Instant parsedToDate = parseDate(toDate, "toDate");

        if (parsedFromDate != null && parsedToDate != null && parsedFromDate.isAfter(parsedToDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }

        if (page < 0) {
            throw new IllegalArgumentException("Page must be greater than or equal to 0");
        }

        if (size < 1 || size > searchProperties.getMaxPageSize()) {
            throw new IllegalArgumentException("Page size must be between 1 and " + searchProperties.getMaxPageSize());
        }

        String normalizedSort = (sort == null || sort.isBlank()) ? "relevance" : sort.trim().toLowerCase();
        if (!normalizedSort.equals("relevance") && !normalizedSort.equals("newest")) {
            throw new IllegalArgumentException("Unsupported sort option: " + sort);
        }

        long fromOffset = (long) page * size;
        if (fromOffset < 0 || fromOffset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid page and size offset combination");
        }
        int from = (int) fromOffset;

        SearchFilter filter = new SearchFilter(validatedLanguage, validatedContentType, validatedStatusCode, parsedFromDate, parsedToDate);
        String indexName = indexerElasticsearchProperties.getIndexName();
        Query esQuery = searchQueryBuilder.buildSearchQuery(trimmedQuery, filter);
        Highlight highlightConfig = searchQueryBuilder.buildHighlight();

        try {
            co.elastic.clients.elasticsearch.core.SearchResponse<Map> esResponse = elasticsearchClient.search(s -> {
                s.index(indexName)
                        .from(from)
                        .size(size)
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

            long totalHits = esResponse.hits().total() != null ? esResponse.hits().total().value() : 0L;
            int totalPages = size > 0 ? (int) Math.ceil((double) totalHits / size) : 0;

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

            log.info("SEARCH_QUERY_EXECUTED query={} language={} contentType={} statusCode={} fromDate={} toDate={} page={} size={} sort={} fuzzyEnabled={} highlightEnabled={} totalHits={} totalPages={} returnedResults={}",
                    trimmedQuery, validatedLanguage, validatedContentType, validatedStatusCode, parsedFromDate, parsedToDate, page, size, normalizedSort, searchProperties.getFuzzy().isEnabled(), searchProperties.getHighlight().isEnabled(), totalHits, totalPages, results.size());

            return new SearchResponse(trimmedQuery, totalHits, page, size, totalPages, normalizedSort, results);

        } catch (Exception e) {
            log.error("SEARCH_QUERY_FAILED query={} index={} error={}", trimmedQuery, indexName, e.getMessage(), e);
            throw new SearchQueryException("Elasticsearch search query failed for: " + trimmedQuery, e);
        }
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
