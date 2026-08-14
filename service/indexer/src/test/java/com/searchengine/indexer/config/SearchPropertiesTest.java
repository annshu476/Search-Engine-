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

        SearchProperties.Highlight highlight = searchProperties.getHighlight();
        assertThat(highlight.isEnabled()).isTrue();
        assertThat(highlight.getFragmentSize()).isEqualTo(150);
        assertThat(highlight.getNumberOfFragments()).isEqualTo(2);
        assertThat(highlight.getPreTag()).isEqualTo("<em>");
        assertThat(highlight.getPostTag()).isEqualTo("</em>");
    }

    @Test
    void validateProperties_validCustomConfig_succeeds() {
        SearchProperties.Relevance relevance = searchProperties.getRelevance();
        relevance.setTitleBoost(5.0);
        relevance.setHeadingsBoost(2.5);

        SearchProperties.Fuzzy fuzzy = searchProperties.getFuzzy();
        fuzzy.setEnabled(true);
        fuzzy.setFuzziness("1");

        SearchProperties.Highlight highlight = searchProperties.getHighlight();
        highlight.setEnabled(true);
        highlight.setFragmentSize(200);
        highlight.setNumberOfFragments(3);
        highlight.setPreTag("<mark>");
        highlight.setPostTag("</mark>");

        searchProperties.validateProperties();

        assertThat(highlight.getFragmentSize()).isEqualTo(200);
        assertThat(highlight.getPreTag()).isEqualTo("<mark>");
    }

    @Test
    void validateProperties_invalidTitleBoost_throwsIllegalStateException() {
        searchProperties.getRelevance().setTitleBoost(0.0);
        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Title boost must be greater than 0");
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
    void validateProperties_invalidHighlightFragmentSize_throwsIllegalStateException() {
        searchProperties.getHighlight().setEnabled(true);
        searchProperties.getHighlight().setFragmentSize(0);

        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Highlight fragment size must be greater than 0");
    }

    @Test
    void validateProperties_invalidHighlightNumberOfFragments_throwsIllegalStateException() {
        searchProperties.getHighlight().setEnabled(true);
        searchProperties.getHighlight().setNumberOfFragments(-1);

        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Highlight number of fragments must be greater than or equal to 0");
    }

    @Test
    void validateProperties_invalidHighlightBlankPreTag_throwsIllegalStateException() {
        searchProperties.getHighlight().setEnabled(true);
        searchProperties.getHighlight().setPreTag("   ");

        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Highlight preTag must not be null or blank when highlighting is enabled");
    }

    @Test
    void validateProperties_invalidHighlightBlankPostTag_throwsIllegalStateException() {
        searchProperties.getHighlight().setEnabled(true);
        searchProperties.getHighlight().setPostTag("");

        assertThatThrownBy(() -> searchProperties.validateProperties())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Highlight postTag must not be null or blank when highlighting is enabled");
    }
}
