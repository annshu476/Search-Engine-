package com.searchengine.indexer.search;

import com.searchengine.indexer.exception.SearchQuerySyntaxException;
import com.searchengine.indexer.model.search.ParsedSearchQuery;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SearchQueryParser {

    public ParsedSearchQuery parse(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        String query = rawQuery.trim();
        List<String> normalTerms = new ArrayList<>();
        List<String> exactPhrases = new ArrayList<>();
        List<String> requiredTerms = new ArrayList<>();
        List<String> requiredPhrases = new ArrayList<>();
        List<String> excludedTerms = new ArrayList<>();
        List<String> excludedPhrases = new ArrayList<>();

        int i = 0;
        int len = query.length();

        while (i < len) {
            // Skip leading whitespace
            while (i < len && Character.isWhitespace(query.charAt(i))) {
                i++;
            }
            if (i >= len) {
                break;
            }

            Operator op = Operator.NORMAL;
            char firstChar = query.charAt(i);

            if (firstChar == '+') {
                op = Operator.REQUIRED;
                i++;
            } else if (firstChar == '-') {
                op = Operator.EXCLUDED;
                i++;
            }

            if (op != Operator.NORMAL) {
                if (i >= len || Character.isWhitespace(query.charAt(i))) {
                    throw new SearchQuerySyntaxException("Invalid search query syntax");
                }
            }

            if (query.charAt(i) == '"') {
                int closeIndex = query.indexOf('"', i + 1);
                if (closeIndex == -1) {
                    throw new SearchQuerySyntaxException("Invalid search query syntax");
                }
                String phrase = query.substring(i + 1, closeIndex).trim();
                if (phrase.isEmpty()) {
                    throw new SearchQuerySyntaxException("Invalid search query syntax");
                }
                i = closeIndex + 1;

                switch (op) {
                    case REQUIRED -> requiredPhrases.add(phrase);
                    case EXCLUDED -> excludedPhrases.add(phrase);
                    case NORMAL -> exactPhrases.add(phrase);
                }
            } else {
                int start = i;
                while (i < len && !Character.isWhitespace(query.charAt(i))) {
                    if (query.charAt(i) == '"') {
                        throw new SearchQuerySyntaxException("Invalid search query syntax");
                    }
                    i++;
                }
                String token = query.substring(start, i).trim();
                if (token.isEmpty()) {
                    throw new SearchQuerySyntaxException("Invalid search query syntax");
                }

                switch (op) {
                    case REQUIRED -> requiredTerms.add(token);
                    case EXCLUDED -> excludedTerms.add(token);
                    case NORMAL -> normalTerms.add(token);
                }
            }
        }

        ParsedSearchQuery parsed = new ParsedSearchQuery(
                rawQuery,
                normalTerms,
                exactPhrases,
                requiredTerms,
                requiredPhrases,
                excludedTerms,
                excludedPhrases
        );

        if (!parsed.hasPositiveTerms()) {
            throw new SearchQuerySyntaxException("Invalid search query syntax");
        }

        return parsed;
    }

    private enum Operator {
        NORMAL,
        REQUIRED,
        EXCLUDED
    }
}
