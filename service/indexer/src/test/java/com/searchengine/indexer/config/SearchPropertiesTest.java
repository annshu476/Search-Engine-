package com.searchengine.indexer.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        SearchProperties.Suggestions suggestions = searchProperties.getSuggestions();
        suggestions.setEnabled(true);
        suggestions.setMaxResults(15);
        suggestions.setMinPrefixLength(3);

        searchProperties.validateProperties();

        assertThat(suggestions.getMaxResults()).isEqualTo(15);
        assertThat(suggestions.getMinPrefixLength()).isEqualTo(3);
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
