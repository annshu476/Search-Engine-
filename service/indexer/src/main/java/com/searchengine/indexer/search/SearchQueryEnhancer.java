package com.searchengine.indexer.search;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.search.ParsedSearchQuery;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchQueryEnhancer {

    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;

    public Map<String, List<String>> getSynonymsForTerms(ParsedSearchQuery parsedQuery) {
        Map<String, List<String>> result = new HashMap<>();

        if (!searchProperties.getSynonyms().isEnabled() || parsedQuery == null) {
            return result;
        }

        Map<String, List<String>> synonymMap = buildSynonymMap();

        List<String> eligibleTerms = new ArrayList<>();
        eligibleTerms.addAll(parsedQuery.normalTerms());
        eligibleTerms.addAll(parsedQuery.requiredTerms());

        for (String term : eligibleTerms) {
            if (term == null || term.isBlank()) continue;
            String lowerTerm = term.toLowerCase();
            List<String> synonyms = synonymMap.get(lowerTerm);
            if (synonyms != null && !synonyms.isEmpty()) {
                int limit = searchProperties.getSynonyms().getMaximumSynonymsPerTerm();
                List<String> boundedSynonyms = synonyms.stream().limit(limit).toList();
                result.put(term, boundedSynonyms);

                meterRegistry.counter("search.synonym.expansions").increment();
                log.info("SEARCH_SYNONYM_EXPANDED term=\"{}\" expansionCount={}", term, boundedSynonyms.size());
            }
        }

        return result;
    }

    private Map<String, List<String>> buildSynonymMap() {
        Map<String, List<String>> map = new HashMap<>();
        List<String> rules = searchProperties.getSynonyms().getRules();
        if (rules == null) return map;

        for (String rule : rules) {
            if (rule == null || rule.isBlank()) continue;
            String[] parts = rule.split(",");
            List<String> tokens = new ArrayList<>();
            for (String p : parts) {
                String trimmed = p.trim().toLowerCase();
                if (!trimmed.isEmpty()) {
                    tokens.add(trimmed);
                }
            }

            for (int i = 0; i < tokens.size(); i++) {
                String key = tokens.get(i);
                List<String> equivalents = new ArrayList<>();
                for (int j = 0; j < tokens.size(); j++) {
                    if (i != j) {
                        equivalents.add(tokens.get(j));
                    }
                }
                map.computeIfAbsent(key, k -> new ArrayList<>()).addAll(equivalents);
            }
        }

        return map;
    }
}
