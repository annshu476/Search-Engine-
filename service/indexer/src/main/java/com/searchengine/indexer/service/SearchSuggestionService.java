package com.searchengine.indexer.service;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.exception.SearchSuggestionException;
import com.searchengine.indexer.model.dto.SearchSuggestionResponse;
import com.searchengine.indexer.search.ElasticsearchSuggestionQueryBuilder;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchSuggestionService {

    private final ElasticsearchClient elasticsearchClient;
    private final IndexerElasticsearchProperties indexerElasticsearchProperties;
    private final SearchProperties searchProperties;
    private final ElasticsearchSuggestionQueryBuilder suggestionQueryBuilder;

    public SearchSuggestionResponse suggest(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        String trimmedPrefix = query.trim();
        if (trimmedPrefix.length() > searchProperties.getMaxQueryLength()) {
            throw new IllegalArgumentException("Search query exceeds maximum allowed length");
        }

        SearchProperties.Suggestions suggestionsConfig = searchProperties.getSuggestions();
        if (trimmedPrefix.length() < suggestionsConfig.getMinPrefixLength()) {
            throw new IllegalArgumentException("Search prefix must contain at least " + suggestionsConfig.getMinPrefixLength() + " characters");
        }

        if (!suggestionsConfig.isEnabled()) {
            log.warn("SEARCH_SUGGESTION_FAILED query={} reason=Service disabled", trimmedPrefix);
            throw new SearchSuggestionException("Search suggestion service is disabled");
        }

        String indexName = indexerElasticsearchProperties.getIndexName();
        Query esQuery = suggestionQueryBuilder.buildSuggestionQuery(trimmedPrefix);

        try {
            int fetchSize = Math.min(suggestionsConfig.getMaxResults() * 2, 20);
            co.elastic.clients.elasticsearch.core.SearchResponse<Map> esResponse = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .from(0)
                    .size(fetchSize)
                    .query(esQuery), Map.class);

            Set<String> suggestionSet = new LinkedHashSet<>();
            String lowerPrefix = trimmedPrefix.toLowerCase();

            if (esResponse.hits().hits() != null) {
                for (Hit<Map> hit : esResponse.hits().hits()) {
                    Map source = hit.source();
                    if (source != null) {
                        extractCandidate(source.get("title"), lowerPrefix, suggestionSet, suggestionsConfig.getMaxResults());
                        
                        Object headingsObj = source.get("headings");
                        if (headingsObj instanceof List<?> headingsList) {
                            for (Object h : headingsList) {
                                extractCandidate(h, lowerPrefix, suggestionSet, suggestionsConfig.getMaxResults());
                            }
                        }

                        extractCandidate(source.get("metaDescription"), lowerPrefix, suggestionSet, suggestionsConfig.getMaxResults());
                    }
                }
            }

            List<String> suggestions = new ArrayList<>(suggestionSet);
            if (suggestions.size() > suggestionsConfig.getMaxResults()) {
                suggestions = suggestions.subList(0, suggestionsConfig.getMaxResults());
            }

            log.info("SEARCH_SUGGESTION_EXECUTED query={} returnedSuggestions={}", trimmedPrefix, suggestions.size());
            return new SearchSuggestionResponse(trimmedPrefix, suggestions);

        } catch (SearchSuggestionException e) {
            throw e;
        } catch (Exception e) {
            log.error("SEARCH_SUGGESTION_FAILED query={} reason={}", trimmedPrefix, e.getMessage(), e);
            throw new SearchSuggestionException("Search suggestion service temporarily unavailable", e);
        }
    }

    private void extractCandidate(Object fieldValue, String lowerPrefix, Set<String> suggestionSet, int limit) {
        if (suggestionSet.size() >= limit || fieldValue == null) {
            return;
        }
        String text = fieldValue.toString().trim();
        if (text.isBlank()) {
            return;
        }
        String lowerText = text.toLowerCase();

        if (lowerText.startsWith(lowerPrefix)) {
            suggestionSet.add(cleanCandidateText(text));
            return;
        }

        int index = lowerText.indexOf(" " + lowerPrefix);
        if (index != -1) {
            String substring = text.substring(index + 1).trim();
            suggestionSet.add(cleanCandidateText(substring));
        }
    }

    private String cleanCandidateText(String rawText) {
        String[] words = rawText.split("\\s+");
        if (words.length <= 4) {
            return rawText.toLowerCase();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) sb.append(" ");
            sb.append(words[i]);
        }
        return sb.toString().toLowerCase();
    }
}
