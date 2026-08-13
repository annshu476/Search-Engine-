package com.searchengine.indexer.service;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.exception.SearchQueryException;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchResult;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
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

    public SearchResponse search(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        String trimmedQuery = query.trim();
        if (trimmedQuery.length() > searchProperties.getMaxQueryLength()) {
            throw new IllegalArgumentException("Search query exceeds maximum allowed length");
        }

        String indexName = indexerElasticsearchProperties.getIndexName();

        try {
            co.elastic.clients.elasticsearch.core.SearchResponse<Map> esResponse = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .size(searchProperties.getMaxResults())
                    .query(q -> q
                            .multiMatch(m -> m
                                    .query(trimmedQuery)
                                    .fields("title", "metaDescription", "headings", "bodyText")
                            )
                    ), Map.class);

            long totalHits = esResponse.hits().total() != null ? esResponse.hits().total().value() : 0L;
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

            log.info("SEARCH_QUERY_EXECUTED query={} totalHits={} returnedResults={}", trimmedQuery, totalHits, results.size());
            return new SearchResponse(trimmedQuery, totalHits, results);

        } catch (Exception e) {
            log.error("SEARCH_QUERY_FAILED query={} index={} error={}", trimmedQuery, indexName, e.getMessage(), e);
            throw new SearchQueryException("Elasticsearch search query failed for: " + trimmedQuery, e);
        }
    }
}
