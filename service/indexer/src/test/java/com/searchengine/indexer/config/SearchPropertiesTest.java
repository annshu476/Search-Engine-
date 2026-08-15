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

        SearchProperties.Cache cache = searchProperties.getCache();
        assertThat(cache.isEnabled()).isTrue();
        assertThat(cache.getMaximumSize()).isEqualTo(1000L);
        assertThat(cache.getTtl()).isEqualTo(Duration.ofSeconds(60));

        SearchProperties.Analytics analytics = searchProperties.getAnalytics();
        assertThat(analytics.isEnabled()).isTrue();
        assertThat(analytics.getMaximumQueryEntries()).isEqualTo(5000L);
        assertThat(analytics.getTopQueryLimit()).isEqualTo(20);
        assertThat(analytics.getQueryRetention()).isEqualTo(Duration.ofHours(1));

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

        searchProperties.validateProperties();

        assertThat(searchProperties.getTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(searchProperties.getMaxPageDepth()).isEqualTo(20000);
        assertThat(searchProperties.getMaxQueryTerms()).isEqualTo(30);
        assertThat(searchProperties.getMaxQueryPhrases()).isEqualTo(15);
        assertThat(searchProperties.getSlowQueryThresholdMs()).isEqualTo(2000L);
        assertThat(searchProperties.getCache().getMaximumSize()).isEqualTo(5000L);
        assertThat(searchProperties.getCache().getTtl()).isEqualTo(Duration.ofSeconds(120));
        assertThat(searchProperties.getAnalytics().getMaximumQueryEntries()).isEqualTo(10000L);
        assertThat(searchProperties.getAnalytics().getTopQueryLimit()).isEqualTo(50);
        assertThat(searchProperties.getAnalytics().getQueryRetention()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void validateProperties_invalidAnalyticsMaxEntries_throwsIllegalStateException() {
        searchProperties.getAnalytics().setEnabled(true);
        searchProperties.getAnalytics().setMaximumQueryEntries(0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Analytics maximum-query-entries must be between 1 and 100000");

        searchProperties.getAnalytics().setMaximumQueryEntries(100001);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Analytics maximum-query-entries must be between 1 and 100000");
    }

    @Test
    void validateProperties_invalidAnalyticsTopLimit_throwsIllegalStateException() {
        searchProperties.getAnalytics().setEnabled(true);
        searchProperties.getAnalytics().setTopQueryLimit(0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Analytics top-query-limit must be between 1 and 100");

        searchProperties.getAnalytics().setTopQueryLimit(101);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Analytics top-query-limit must be between 1 and 100");
    }

    @Test
    void validateProperties_invalidAnalyticsQueryRetention_throwsIllegalStateException() {
        searchProperties.getAnalytics().setEnabled(true);
        searchProperties.getAnalytics().setQueryRetention(Duration.ZERO);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Analytics query-retention must be greater than 0");

        searchProperties.getAnalytics().setQueryRetention(null);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Analytics query-retention must be greater than 0");
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
