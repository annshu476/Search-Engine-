package com.searchengine.indexer.model.search;

import java.util.List;

public record ParsedSearchQuery(
        String rawQuery,
        List<String> normalTerms,
        List<String> exactPhrases,
        List<String> requiredTerms,
        List<String> requiredPhrases,
        List<String> excludedTerms,
        List<String> excludedPhrases
) {
    public boolean hasPositiveTerms() {
        return (normalTerms != null && !normalTerms.isEmpty()) ||
               (exactPhrases != null && !exactPhrases.isEmpty()) ||
               (requiredTerms != null && !requiredTerms.isEmpty()) ||
               (requiredPhrases != null && !requiredPhrases.isEmpty());
    }

    public boolean isSimpleNormalQuery() {
        return (requiredTerms == null || requiredTerms.isEmpty()) &&
               (requiredPhrases == null || requiredPhrases.isEmpty()) &&
               (excludedTerms == null || excludedTerms.isEmpty()) &&
               (excludedPhrases == null || excludedPhrases.isEmpty()) &&
               (exactPhrases == null || exactPhrases.isEmpty());
    }
}
