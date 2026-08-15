package com.searchengine.indexer.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

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

        SearchProperties.Cache cache = searchProperties.getCache();
        assertThat(cache.isEnabled()).isTrue();
        assertThat(cache.getMaximumSize()).isEqualTo(1000L);
        assertThat(cache.getTtl()).isEqualTo(Duration.ofSeconds(60));

        SearchProperties.Analytics analytics = searchProperties.getAnalytics();
        assertThat(analytics.isEnabled()).isTrue();
        assertThat(analytics.getMaximumQueryEntries()).isEqualTo(5000L);
        assertThat(analytics.getTopQueryLimit()).isEqualTo(20);
        assertThat(analytics.getQueryRetention()).isEqualTo(Duration.ofHours(1));

        SearchProperties.Synonyms synonyms = searchProperties.getSynonyms();
        assertThat(synonyms.isEnabled()).isTrue();
        assertThat(synonyms.getMaximumSynonymsPerTerm()).isEqualTo(5);
        assertThat(synonyms.getRules()).contains("java, jdk");

        SearchProperties.SpellCorrection spellCorrection = searchProperties.getSpellCorrection();
        assertThat(spellCorrection.isEnabled()).isTrue();
        assertThat(spellCorrection.getMaximumSuggestions()).isEqualTo(3);
        assertThat(spellCorrection.getMinimumTermLength()).isEqualTo(3);

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
        searchProperties.getCache().setMaximumSize(5000L);
        searchProperties.getCache().setTtl(Duration.ofSeconds(120));
        searchProperties.getAnalytics().setMaximumQueryEntries(10000L);
        searchProperties.getAnalytics().setTopQueryLimit(50);
        searchProperties.getAnalytics().setQueryRetention(Duration.ofHours(2));
        searchProperties.getSynonyms().setMaximumSynonymsPerTerm(10);
        searchProperties.getSpellCorrection().setMaximumSuggestions(5);
        searchProperties.getSpellCorrection().setMinimumTermLength(4);

        searchProperties.validateProperties();

        assertThat(searchProperties.getTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(searchProperties.getSynonyms().getMaximumSynonymsPerTerm()).isEqualTo(10);
        assertThat(searchProperties.getSpellCorrection().getMaximumSuggestions()).isEqualTo(5);
        assertThat(searchProperties.getSpellCorrection().getMinimumTermLength()).isEqualTo(4);
    }

    @Test
    void validateProperties_invalidSynonymConfig_throwsIllegalStateException() {
        searchProperties.getSynonyms().setEnabled(true);
        searchProperties.getSynonyms().setMaximumSynonymsPerTerm(0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum synonyms per term must be greater than 0");

        searchProperties.getSynonyms().setMaximumSynonymsPerTerm(5);
        searchProperties.getSynonyms().setRules(List.of("invalid_rule_no_comma"));
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Malformed synonym rule");
    }

    @Test
    void validateProperties_invalidSpellCorrectionConfig_throwsIllegalStateException() {
        searchProperties.getSpellCorrection().setEnabled(true);
        searchProperties.getSpellCorrection().setMaximumSuggestions(0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum spell suggestions must be greater than 0");

        searchProperties.getSpellCorrection().setMaximumSuggestions(3);
        searchProperties.getSpellCorrection().setMinimumTermLength(0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Minimum term length for spell correction must be greater than or equal to 1");
    }

    @Test
    void validateProperties_invalidCacheMaximumSize_throwsIllegalStateException() {
        searchProperties.getCache().setEnabled(true);
        searchProperties.getCache().setMaximumSize(0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Search cache maximum-size must be greater than 0");
    }

    @Test
    void validateProperties_invalidCacheTtl_throwsIllegalStateException() {
        searchProperties.getCache().setEnabled(true);
        searchProperties.getCache().setTtl(Duration.ZERO);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Search cache TTL must be greater than 0");
    }
}
