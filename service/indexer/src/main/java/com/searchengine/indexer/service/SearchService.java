package com.searchengine.indexer.service;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.exception.SearchQueryException;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchResult;
import com.searchengine.indexer.search.ElasticsearchSearchQueryBuilder;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        String trimmedQuery = query.trim();
        if (trimmedQuery.length() > searchProperties.getMaxQueryLength()) {
            throw new IllegalArgumentException("Search query exceeds maximum allowed length");
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

        String indexName = indexerElasticsearchProperties.getIndexName();
        Query esQuery = searchQueryBuilder.buildMultiMatchQuery(trimmedQuery);

        try {
            co.elastic.clients.elasticsearch.core.SearchResponse<Map> esResponse = elasticsearchClient.search(s -> {
                s.index(indexName)
                        .from(from)
                        .size(size)
                        .query(esQuery);

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
                        String language = (String) source.get("language");
                        Integer wordCount = source.get("wordCount") != null ? ((Number) source.get("wordCount")).intValue() : null;
                        Integer statusCode = source.get("statusCode") != null ? ((Number) source.get("statusCode")).intValue() : null;

                        results.add(new SearchResult(url, canonicalUrl, urlHash, title, metaDescription, language, wordCount, statusCode));
                    }
                }
            }

            log.info("SEARCH_QUERY_EXECUTED query={} page={} size={} sort={} fuzzyEnabled={} totalHits={} totalPages={} returnedResults={}",
                    trimmedQuery, page, size, normalizedSort, searchProperties.getFuzzy().isEnabled(), totalHits, totalPages, results.size());

            return new SearchResponse(trimmedQuery, totalHits, page, size, totalPages, normalizedSort, results);

        } catch (Exception e) {
            log.error("SEARCH_QUERY_FAILED query={} index={} error={}", trimmedQuery, indexName, e.getMessage(), e);
            throw new SearchQueryException("Elasticsearch search query failed for: " + trimmedQuery, e);
        }
    }
}
