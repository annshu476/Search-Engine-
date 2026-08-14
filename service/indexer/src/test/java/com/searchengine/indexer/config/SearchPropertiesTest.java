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

        SearchProperties.Fuzzy fuzzy = searchProperties.getFuzzy();
        assertThat(fuzzy.isEnabled()).isTrue();
        assertThat(fuzzy.getFuzziness()).isEqualTo("AUTO");
    }

    @Test
    void validateProperties_validCustomBoostsAndFuzzy_succeeds() {
        SearchProperties.Relevance relevance = searchProperties.getRelevance();
        relevance.setTitleBoost(5.0);
        relevance.setHeadingsBoost(2.5);
        relevance.setMetaDescriptionBoost(1.5);
        relevance.setBodyBoost(0.5);

        SearchProperties.Fuzzy fuzzy = searchProperties.getFuzzy();
        fuzzy.setEnabled(true);
        fuzzy.setFuzziness("1");

        searchProperties.validateProperties();

        assertThat(relevance.getTitleBoost()).isEqualTo(5.0);
        assertThat(fuzzy.getFuzziness()).isEqualTo("1");
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

    @Test
    void validateProperties_invalidNullFuzzinessWhenEnabled_throwsIllegalStateException() {
        searchProperties.getFuzzy().setEnabled(true);
        searchProperties.getFuzzy().setFuzziness(null);

        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fuzziness configuration must not be null or blank when fuzzy search is enabled");
    }

    @Test
    void validateProperties_invalidBlankFuzzinessWhenEnabled_throwsIllegalStateException() {
        searchProperties.getFuzzy().setEnabled(true);
        searchProperties.getFuzzy().setFuzziness("   ");

        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fuzziness configuration must not be null or blank when fuzzy search is enabled");
    }

    @Test
    void validateProperties_nullFuzzinessWhenDisabled_succeeds() {
        searchProperties.getFuzzy().setEnabled(false);
        searchProperties.getFuzzy().setFuzziness(null);

        searchProperties.validateProperties();

        assertThat(searchProperties.getFuzzy().isEnabled()).isFalse();
    }
}
