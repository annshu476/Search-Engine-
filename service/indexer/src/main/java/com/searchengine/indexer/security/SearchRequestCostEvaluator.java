package com.searchengine.indexer.security;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.search.ParsedSearchQuery;
import com.searchengine.indexer.search.SearchQueryParser;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchRequestCostEvaluator {

    private final SearchProperties searchProperties;
    private final SearchQueryParser searchQueryParser;
    private final MeterRegistry meterRegistry;

    public void evaluateCost(String query, String language, String contentType, Integer statusCode, String fromDate, String toDate, int page, int size) {
        int maxCost = searchProperties.getRateLimit().getMaxCostScore();
        if (maxCost <= 0) {
            return;
        }

        int score = 1;

        if (query != null && !query.isBlank()) {
            ParsedSearchQuery parsed = searchQueryParser.parse(query.trim());
            score += parsed.normalTerms().size();
            score += parsed.requiredTerms().size() * 2;
            score += parsed.excludedTerms().size() * 2;
            score += (parsed.exactPhrases().size() + parsed.requiredPhrases().size() + parsed.excludedPhrases().size()) * 3;
        }

        if (language != null && !language.isBlank()) score += 1;
        if (contentType != null && !contentType.isBlank()) score += 1;
        if (statusCode != null) score += 1;
        if (fromDate != null && !fromDate.isBlank()) score += 1;
        if (toDate != null && !toDate.isBlank()) score += 1;

        if (searchProperties.getFuzzy().isEnabled()) score += 2;
        if (searchProperties.getHighlight().isEnabled()) score += 2;

        if (size > 20) {
            score += (size / 10);
        }

        long fromOffset = (long) page * size;
        if (fromOffset > 100) {
            score += (int) (fromOffset / 100);
        }

        if (score > maxCost) {
            meterRegistry.counter("search.query.cost_rejected").increment();
            log.warn("SEARCH_QUERY_COST_REJECTED costScore={} maxCostScore={} page={} size={}", score, maxCost, page, size);
            throw new IllegalArgumentException("Search request is too expensive");
        }
    }
}
