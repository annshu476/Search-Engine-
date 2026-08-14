package com.searchengine.indexer.search;

import com.searchengine.indexer.config.SearchProperties;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchSuggestionQueryBuilderTest {

    private SearchProperties searchProperties;
    private ElasticsearchSuggestionQueryBuilder queryBuilder;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        queryBuilder = new ElasticsearchSuggestionQueryBuilder(searchProperties);
    }

    @Test
    void buildSuggestionQuery_buildsPhrasePrefixQueryAcrossBoostedFields() {
        Query query = queryBuilder.buildSuggestionQuery("spr");

        assertThat(query.isMultiMatch()).isTrue();
        MultiMatchQuery multiMatch = query.multiMatch();

        assertThat(multiMatch.query()).isEqualTo("spr");
        assertThat(multiMatch.type()).isEqualTo(TextQueryType.PhrasePrefix);
        assertThat(multiMatch.fields()).containsExactly(
                "title^4.0",
                "headings^3.0",
                "metaDescription^2.0"
        );
        assertThat(multiMatch.fields()).doesNotContain("bodyText^1.0");
    }
}
