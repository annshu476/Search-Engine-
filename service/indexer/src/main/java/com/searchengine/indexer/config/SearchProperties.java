package com.searchengine.indexer.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexer.search")
public class SearchProperties {

    private int maxResults = 10;
    private int maxPageSize = 50;
    private int maxQueryLength = 200;
    private Duration timeout = Duration.ofSeconds(3);
    private int maxPageDepth = 10000;
    private int maxQueryTerms = 20;
    private int maxQueryPhrases = 10;
    private long slowQueryThresholdMs = 1000;

    private Cache cache = new Cache();
    private Analytics analytics = new Analytics();
    private Synonyms synonyms = new Synonyms();
    private SpellCorrection spellCorrection = new SpellCorrection();
    private Relevance relevance = new Relevance();
    private Fuzzy fuzzy = new Fuzzy();
    private Highlight highlight = new Highlight();
    private Suggestions suggestions = new Suggestions();

    @Getter
    @Setter
    public static class Cache {
        private boolean enabled = true;
        private long maximumSize = 1000;
        private Duration ttl = Duration.ofSeconds(60);
    }

    @Getter
    @Setter
    public static class Analytics {
        private boolean enabled = true;
        private long maximumQueryEntries = 5000;
        private int topQueryLimit = 20;
        private Duration queryRetention = Duration.ofHours(1);
    }

    @Getter
    @Setter
    public static class Synonyms {
        private boolean enabled = true;
        private int maximumSynonymsPerTerm = 5;
        private List<String> rules = List.of(
                "java, jdk",
                "js, javascript",
                "spring boot, springboot"
        );
    }

    @Getter
    @Setter
    public static class SpellCorrection {
        private boolean enabled = true;
        private int maximumSuggestions = 3;
        private int minimumTermLength = 3;
    }

    @Getter
    @Setter
    public static class Relevance {
        private double titleBoost = 4.0;
        private double headingsBoost = 3.0;
        private double metaDescriptionBoost = 2.0;
        private double bodyBoost = 1.0;
        private double phraseBoost = 2.0;
        private double titlePhraseBoost = 4.0;
    }

    @Getter
    @Setter
    public static class Fuzzy {
        private boolean enabled = true;
        private String fuzziness = "AUTO";
    }

    @Getter
    @Setter
    public static class Highlight {
        private boolean enabled = true;
        private int fragmentSize = 150;
        private int numberOfFragments = 2;
        private String preTag = "<em>";
        private String postTag = "</em>";
    }

    @Getter
    @Setter
    public static class Suggestions {
        private boolean enabled = true;
        private int maxResults = 8;
        private int minPrefixLength = 2;
    }

    @PostConstruct
    public void validateProperties() {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalStateException("Search timeout must be greater than 0");
        }
        if (maxPageDepth <= 0) {
            throw new IllegalStateException("Max page depth must be greater than 0");
        }
        if (maxQueryTerms <= 0) {
            throw new IllegalStateException("Max query terms must be greater than 0");
        }
        if (maxQueryPhrases <= 0) {
            throw new IllegalStateException("Max query phrases must be greater than 0");
        }
        if (slowQueryThresholdMs <= 0) {
            throw new IllegalStateException("Slow query threshold must be greater than 0");
        }
        if (cache.isEnabled()) {
            if (cache.getMaximumSize() <= 0) {
                throw new IllegalStateException("Search cache maximum-size must be greater than 0");
            }
            if (cache.getTtl() == null || cache.getTtl().isNegative() || cache.getTtl().isZero()) {
                throw new IllegalStateException("Search cache TTL must be greater than 0");
            }
        }
        if (analytics.isEnabled()) {
            if (analytics.getMaximumQueryEntries() <= 0 || analytics.getMaximumQueryEntries() > 100000) {
                throw new IllegalStateException("Analytics maximum-query-entries must be between 1 and 100000");
            }
            if (analytics.getTopQueryLimit() < 1 || analytics.getTopQueryLimit() > 100) {
                throw new IllegalStateException("Analytics top-query-limit must be between 1 and 100");
            }
            if (analytics.getQueryRetention() == null || analytics.getQueryRetention().isNegative() || analytics.getQueryRetention().isZero()) {
                throw new IllegalStateException("Analytics query-retention must be greater than 0");
            }
        }
        if (synonyms.isEnabled()) {
            if (synonyms.getMaximumSynonymsPerTerm() <= 0) {
                throw new IllegalStateException("Maximum synonyms per term must be greater than 0");
            }
            if (synonyms.getRules() == null || synonyms.getRules().isEmpty()) {
                throw new IllegalStateException("Synonym rules list must not be empty when synonyms are enabled");
            }
            if (synonyms.getRules().size() > 500) {
                throw new IllegalStateException("Synonym rules count exceeds maximum allowed limit");
            }
            for (String rule : synonyms.getRules()) {
                if (rule == null || rule.isBlank()) {
                    throw new IllegalStateException("Synonym rule must not be null or blank");
                }
                String[] parts = rule.split(",");
                if (parts.length < 2) {
                    throw new IllegalStateException("Malformed synonym rule: " + rule);
                }
            }
        }
        if (spellCorrection.isEnabled()) {
            if (spellCorrection.getMaximumSuggestions() <= 0) {
                throw new IllegalStateException("Maximum spell suggestions must be greater than 0");
            }
            if (spellCorrection.getMinimumTermLength() < 1) {
                throw new IllegalStateException("Minimum term length for spell correction must be greater than or equal to 1");
            }
        }
        if (relevance.getTitleBoost() <= 0) {
            throw new IllegalStateException("Title boost must be greater than 0");
        }
        if (relevance.getHeadingsBoost() <= 0) {
            throw new IllegalStateException("Headings boost must be greater than 0");
        }
        if (relevance.getMetaDescriptionBoost() <= 0) {
            throw new IllegalStateException("Meta description boost must be greater than 0");
        }
        if (relevance.getBodyBoost() <= 0) {
            throw new IllegalStateException("Body boost must be greater than 0");
        }
        if (relevance.getPhraseBoost() <= 0) {
            throw new IllegalStateException("Phrase boost must be greater than 0");
        }
        if (relevance.getTitlePhraseBoost() <= 0) {
            throw new IllegalStateException("Title phrase boost must be greater than 0");
        }
        if (fuzzy.isEnabled() && (fuzzy.getFuzziness() == null || fuzzy.getFuzziness().isBlank())) {
            throw new IllegalStateException("Fuzziness configuration must not be null or blank when fuzzy search is enabled");
        }
        if (highlight.isEnabled()) {
            if (highlight.getFragmentSize() <= 0) {
                throw new IllegalStateException("Highlight fragment size must be greater than 0");
            }
            if (highlight.getNumberOfFragments() < 0) {
                throw new IllegalStateException("Highlight number of fragments must be greater than or equal to 0");
            }
            if (highlight.getPreTag() == null || highlight.getPreTag().isBlank()) {
                throw new IllegalStateException("Highlight preTag must not be null or blank when highlighting is enabled");
            }
            if (highlight.getPostTag() == null || highlight.getPostTag().isBlank()) {
                throw new IllegalStateException("Highlight postTag must not be null or blank when highlighting is enabled");
            }
        }
        if (suggestions.isEnabled()) {
            if (suggestions.getMaxResults() <= 0 || suggestions.getMaxResults() > 20) {
                throw new IllegalStateException("Suggestions max-results must be between 1 and 20");
            }
            if (suggestions.getMinPrefixLength() < 1 || suggestions.getMinPrefixLength() > maxQueryLength) {
                throw new IllegalStateException("Suggestions min-prefix-length must be between 1 and " + maxQueryLength);
            }
        }
    }
}
