package com.searchengine.indexer.service;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.search.ParsedSearchQuery;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.elasticsearch.core.search.TermSuggestOption;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchSpellCorrectionService {

    private final ElasticsearchClient elasticsearchClient;
    private final IndexerElasticsearchProperties indexerElasticsearchProperties;
    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;

    public String suggestCorrection(String originalQuery, ParsedSearchQuery parsedQuery) {
        if (!searchProperties.getSpellCorrection().isEnabled() || originalQuery == null || originalQuery.isBlank()) {
            return null;
        }

        List<String> eligibleTerms = new ArrayList<>();
        eligibleTerms.addAll(parsedQuery.normalTerms());
        eligibleTerms.addAll(parsedQuery.requiredTerms());

        int minLen = searchProperties.getSpellCorrection().getMinimumTermLength();
        int maxSuggestions = searchProperties.getSpellCorrection().getMaximumSuggestions();
        String indexName = indexerElasticsearchProperties.getIndexName();

        boolean correctedAny = false;
        String correctedQuery = originalQuery;

        for (String term : eligibleTerms) {
            if (term == null || term.length() < minLen) {
                continue;
            }

            String suggestedTerm = findTermSuggestion(term, indexName, maxSuggestions, minLen);
            if (suggestedTerm != null && !suggestedTerm.equalsIgnoreCase(term)) {
                // Replace whole word term in query safely
                correctedQuery = replaceWord(correctedQuery, term, suggestedTerm);
                correctedAny = true;
            }
        }

        return correctedAny && !correctedQuery.trim().equalsIgnoreCase(originalQuery.trim()) ? correctedQuery.trim() : null;
    }

    private String findTermSuggestion(String term, String indexName, int maxSuggestions, int minLen) {
        try {
            co.elastic.clients.elasticsearch.core.SearchResponse<Map> esResponse = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .size(0)
                    .suggest(sug -> sug
                            .text(term)
                            .suggesters("spell_suggest", f -> f
                                    .term(t -> t
                                            .field("title")
                                            .size(maxSuggestions)
                                            .minWordLength(minLen)
                                    )
                            )
                    ), Map.class);

            if (esResponse.suggest() != null && esResponse.suggest().containsKey("spell_suggest")) {
                List<Suggestion<Map>> suggestions = esResponse.suggest().get("spell_suggest");
                for (Suggestion<Map> suggestion : suggestions) {
                    if (suggestion.isTerm() && suggestion.term().options() != null && !suggestion.term().options().isEmpty()) {
                        TermSuggestOption topOption = suggestion.term().options().get(0);
                        if (topOption.text() != null && !topOption.text().isBlank()) {
                            return topOption.text();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Elasticsearch term suggest lookup failed for term={}: {}", term, e.getMessage());
        }

        return null;
    }

    private String replaceWord(String text, String target, String replacement) {
        return text.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(target) + "\\b", replacement);
    }
}
