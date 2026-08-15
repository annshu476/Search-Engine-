package com.searchengine.indexer.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchPropertiesTest {

    private SearchProperties searchProperties;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
    }

    @Test
    void defaultValues_areSetCorrectly() {
        assertThat(searchProperties.getMaxResults()).isEqualTo(10);
        assertThat(searchProperties.getMaxPageSize()).isEqualTo(50);
        assertThat(searchProperties.getMaxQueryLength()).isEqualTo(200);
        assertThat(searchProperties.getTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(searchProperties.getMaxPageDepth()).isEqualTo(10000);
        assertThat(searchProperties.getMaxQueryTerms()).isEqualTo(20);
        assertThat(searchProperties.getMaxQueryPhrases()).isEqualTo(10);
        assertThat(searchProperties.getSlowQueryThresholdMs()).isEqualTo(1000L);

        SearchProperties.Relevance relevance = searchProperties.getRelevance();
        assertThat(relevance.getTitleBoost()).isEqualTo(4.0);
        assertThat(relevance.getHeadingsBoost()).isEqualTo(3.0);
        assertThat(relevance.getMetaDescriptionBoost()).isEqualTo(2.0);
        assertThat(relevance.getBodyBoost()).isEqualTo(1.0);
        assertThat(relevance.getPhraseBoost()).isEqualTo(2.0);
        assertThat(relevance.getTitlePhraseBoost()).isEqualTo(4.0);

        SearchProperties.Fuzzy fuzzy = searchProperties.getFuzzy();
        assertThat(fuzzy.isEnabled()).isTrue();
        assertThat(fuzzy.getFuzziness()).isEqualTo("AUTO");

        SearchProperties.Highlight highlight = searchProperties.getHighlight();
        assertThat(highlight.isEnabled()).isTrue();
        assertThat(highlight.getFragmentSize()).isEqualTo(150);

        SearchProperties.Suggestions suggestions = searchProperties.getSuggestions();
        assertThat(suggestions.isEnabled()).isTrue();
        assertThat(suggestions.getMaxResults()).isEqualTo(8);
        assertThat(suggestions.getMinPrefixLength()).isEqualTo(2);
    }

    @Test
    void validateProperties_validCustomConfig_succeeds() {
        searchProperties.setTimeout(Duration.ofSeconds(5));
        searchProperties.setMaxPageDepth(20000);
        searchProperties.setMaxQueryTerms(30);
        searchProperties.setMaxQueryPhrases(15);
        searchProperties.setSlowQueryThresholdMs(2000L);

        searchProperties.validateProperties();

        assertThat(searchProperties.getTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(searchProperties.getMaxPageDepth()).isEqualTo(20000);
        assertThat(searchProperties.getMaxQueryTerms()).isEqualTo(30);
        assertThat(searchProperties.getMaxQueryPhrases()).isEqualTo(15);
        assertThat(searchProperties.getSlowQueryThresholdMs()).isEqualTo(2000L);
    }

    @Test
    void validateProperties_invalidTimeout_throwsIllegalStateException() {
        searchProperties.setTimeout(Duration.ZERO);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Search timeout must be greater than 0");

        searchProperties.setTimeout(null);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Search timeout must be greater than 0");
    }

    @Test
    void validateProperties_invalidMaxPageDepth_throwsIllegalStateException() {
        searchProperties.setMaxPageDepth(0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Max page depth must be greater than 0");
    }

    @Test
    void validateProperties_invalidMaxQueryTerms_throwsIllegalStateException() {
        searchProperties.setMaxQueryTerms(0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Max query terms must be greater than 0");
    }

    @Test
    void validateProperties_invalidMaxQueryPhrases_throwsIllegalStateException() {
        searchProperties.setMaxQueryPhrases(0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Max query phrases must be greater than 0");
    }

    @Test
    void validateProperties_invalidSlowQueryThreshold_throwsIllegalStateException() {
        searchProperties.setSlowQueryThresholdMs(0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Slow query threshold must be greater than 0");
    }

    @Test
    void validateProperties_invalidPhraseBoostZero_throwsIllegalStateException() {
        searchProperties.getRelevance().setPhraseBoost(0.0);

        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Phrase boost must be greater than 0");
    }

    @Test
    void validateProperties_invalidTitlePhraseBoostZero_throwsIllegalStateException() {
        searchProperties.getRelevance().setTitlePhraseBoost(0.0);

        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Title phrase boost must be greater than 0");
    }

    @Test
    void validateProperties_invalidSuggestionsMaxResultsZero_throwsIllegalStateException() {
        searchProperties.getSuggestions().setEnabled(true);
        searchProperties.getSuggestions().setMaxResults(0);

        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Suggestions max-results must be between 1 and 20");
    }

    @Test
    void validateProperties_invalidSuggestionsMaxResultsTooLarge_throwsIllegalStateException() {
        searchProperties.getSuggestions().setEnabled(true);
        searchProperties.getSuggestions().setMaxResults(21);

        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Suggestions max-results must be between 1 and 20");
    }

    @Test
    void validateProperties_invalidSuggestionsMinPrefixLengthZero_throwsIllegalStateException() {
        searchProperties.getSuggestions().setEnabled(true);
        searchProperties.getSuggestions().setMinPrefixLength(0);

        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Suggestions min-prefix-length must be between 1 and ");
    }
}
