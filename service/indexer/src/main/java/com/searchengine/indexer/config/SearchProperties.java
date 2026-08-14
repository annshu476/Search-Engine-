package com.searchengine.indexer.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexer.search")
public class SearchProperties {

    private int maxResults = 10;
    private int maxPageSize = 50;
    private int maxQueryLength = 200;

    private Relevance relevance = new Relevance();
    private Fuzzy fuzzy = new Fuzzy();
    private Highlight highlight = new Highlight();
    private Suggestions suggestions = new Suggestions();

    @Getter
    @Setter
    public static class Relevance {
        private double titleBoost = 4.0;
        private double headingsBoost = 3.0;
        private double metaDescriptionBoost = 2.0;
        private double bodyBoost = 1.0;
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
