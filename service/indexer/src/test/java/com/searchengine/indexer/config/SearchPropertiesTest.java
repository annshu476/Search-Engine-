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
    }

    @Test
    void validateProperties_validCustomBoosts_succeeds() {
        SearchProperties.Relevance relevance = searchProperties.getRelevance();
        relevance.setTitleBoost(5.0);
        relevance.setHeadingsBoost(2.5);
        relevance.setMetaDescriptionBoost(1.5);
        relevance.setBodyBoost(0.5);

        searchProperties.validateProperties();

        assertThat(relevance.getTitleBoost()).isEqualTo(5.0);
    }

    @Test
    void validateProperties_invalidTitleBoost_throwsIllegalStateException() {
        searchProperties.getRelevance().setTitleBoost(0.0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Title boost must be greater than 0");
    }

    @Test
    void validateProperties_invalidHeadingsBoost_throwsIllegalStateException() {
        searchProperties.getRelevance().setHeadingsBoost(-1.0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Headings boost must be greater than 0");
    }

    @Test
    void validateProperties_invalidMetaDescriptionBoost_throwsIllegalStateException() {
        searchProperties.getRelevance().setMetaDescriptionBoost(0.0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Meta description boost must be greater than 0");
    }

    @Test
    void validateProperties_invalidBodyBoost_throwsIllegalStateException() {
        searchProperties.getRelevance().setBodyBoost(0.0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Body boost must be greater than 0");
    }
}
