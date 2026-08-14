package com.searchengine.indexer.search;

import com.searchengine.indexer.config.SearchProperties;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
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
    void buildMultiMatchQuery_buildsWeightedQueryWithConfiguredBoosts() {
        Query query = queryBuilder.buildMultiMatchQuery("Spring Boot");

        assertThat(query.isMultiMatch()).isTrue();
        MultiMatchQuery multiMatch = query.multiMatch();

        assertThat(multiMatch.query()).isEqualTo("Spring Boot");
        assertThat(multiMatch.type()).isEqualTo(TextQueryType.BestFields);
        assertThat(multiMatch.minimumShouldMatch()).isEqualTo("1");

        assertThat(multiMatch.fields()).containsExactly(
                "title^4.0",
                "headings^3.0",
                "metaDescription^2.0",
                "bodyText^1.0"
        );
    }

    @Test
    void buildMultiMatchQuery_usesCustomConfiguredBoosts() {
        SearchProperties.Relevance relevance = searchProperties.getRelevance();
        relevance.setTitleBoost(10.0);
        relevance.setHeadingsBoost(5.0);
        relevance.setMetaDescriptionBoost(2.5);
        relevance.setBodyBoost(0.5);

        Query query = queryBuilder.buildMultiMatchQuery("Java");
        MultiMatchQuery multiMatch = query.multiMatch();

        assertThat(multiMatch.fields()).containsExactly(
                "title^10.0",
                "headings^5.0",
                "metaDescription^2.5",
                "bodyText^0.5"
        );
    }
}
