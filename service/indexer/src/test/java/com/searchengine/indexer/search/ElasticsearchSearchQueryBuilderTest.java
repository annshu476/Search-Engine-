package com.searchengine.indexer.search;

import com.searchengine.indexer.config.SearchProperties;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchSearchQueryBuilderTest {

    private SearchProperties searchProperties;
    private ElasticsearchSearchQueryBuilder queryBuilder;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        queryBuilder = new ElasticsearchSearchQueryBuilder(searchProperties);
    }

    @Test
    void buildMultiMatchQuery_fuzzyEnabled_includesFuzzinessAndBoosts() {
        Query query = queryBuilder.buildMultiMatchQuery("Spring Boot");

        assertThat(query.isMultiMatch()).isTrue();
        MultiMatchQuery multiMatch = query.multiMatch();

        assertThat(multiMatch.query()).isEqualTo("Spring Boot");
        assertThat(multiMatch.type()).isEqualTo(TextQueryType.BestFields);
        assertThat(multiMatch.minimumShouldMatch()).isEqualTo("1");
        assertThat(multiMatch.fuzziness()).isEqualTo("AUTO");

        assertThat(multiMatch.fields()).containsExactly(
                "title^4.0",
                "headings^3.0",
                "metaDescription^2.0",
                "bodyText^1.0"
        );
    }

    @Test
    void buildMultiMatchQuery_fuzzyDisabled_omitsFuzziness() {
        searchProperties.getFuzzy().setEnabled(false);

        Query query = queryBuilder.buildMultiMatchQuery("Spring Boot");
        MultiMatchQuery multiMatch = query.multiMatch();

        assertThat(multiMatch.query()).isEqualTo("Spring Boot");
        assertThat(multiMatch.fuzziness()).isNull();
    }

    @Test
    void buildHighlight_enabled_buildsHighlightWithConfiguredFields() {
        Highlight highlight = queryBuilder.buildHighlight();

        assertThat(highlight).isNotNull();
        assertThat(highlight.preTags()).containsExactly("<em>");
        assertThat(highlight.postTags()).containsExactly("</em>");
        assertThat(highlight.fields()).containsKeys("title", "headings", "metaDescription", "bodyText");
        assertThat(highlight.fields().get("title").fragmentSize()).isEqualTo(150);
        assertThat(highlight.fields().get("title").numberOfFragments()).isEqualTo(2);
    }

    @Test
    void buildHighlight_disabled_returnsNull() {
        searchProperties.getHighlight().setEnabled(false);

        Highlight highlight = queryBuilder.buildHighlight();

        assertThat(highlight).isNull();
    }
}
