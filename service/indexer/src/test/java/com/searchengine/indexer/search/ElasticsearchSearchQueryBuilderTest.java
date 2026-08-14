package com.searchengine.indexer.search;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.dto.SearchFilter;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
    void buildSearchQuery_noFilters_returnsMultiMatchQuery() {
        SearchFilter filter = new SearchFilter(null, null, null, null, null);
        Query query = queryBuilder.buildSearchQuery("spring", filter);

        assertThat(query.isMultiMatch()).isTrue();
        assertThat(query.multiMatch().query()).isEqualTo("spring");
    }

    @Test
    void buildSearchQuery_languageFilter_returnsBoolWithFilterClause() {
        SearchFilter filter = new SearchFilter("en", null, null, null, null);
        Query query = queryBuilder.buildSearchQuery("spring", filter);

        assertThat(query.isBool()).isTrue();
        BoolQuery bool = query.bool();
        assertThat(bool.must()).hasSize(1);
        assertThat(bool.must().get(0).isMultiMatch()).isTrue();

        assertThat(bool.filter()).hasSize(1);
        Query filterQuery = bool.filter().get(0);
        assertThat(filterQuery.isTerm()).isTrue();
        assertThat(filterQuery.term().field()).isEqualTo("language");
        assertThat(filterQuery.term().value().stringValue()).isEqualTo("en");
    }

    @Test
    void buildSearchQuery_allFilters_returnsBoolWithMustAndFilterClauses() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-14T23:59:59Z");
        SearchFilter filter = new SearchFilter("en", "text/html", 200, from, to);

        Query query = queryBuilder.buildSearchQuery("spring", filter);

        assertThat(query.isBool()).isTrue();
        BoolQuery bool = query.bool();
        assertThat(bool.must()).hasSize(1);
        assertThat(bool.must().get(0).isMultiMatch()).isTrue();

        assertThat(bool.filter()).hasSize(4);
        assertThat(bool.filter()).anySatisfy(f -> {
            assertThat(f.isTerm()).isTrue();
            assertThat(f.term().field()).isEqualTo("language");
            assertThat(f.term().value().stringValue()).isEqualTo("en");
        });
        assertThat(bool.filter()).anySatisfy(f -> {
            assertThat(f.isTerm()).isTrue();
            assertThat(f.term().field()).isEqualTo("contentType");
            assertThat(f.term().value().stringValue()).isEqualTo("text/html");
        });
        assertThat(bool.filter()).anySatisfy(f -> {
            assertThat(f.isTerm()).isTrue();
            assertThat(f.term().field()).isEqualTo("statusCode");
            assertThat(f.term().value().longValue()).isEqualTo(200);
        });
        assertThat(bool.filter()).anySatisfy(f -> {
            assertThat(f.isRange()).isTrue();
            assertThat(f.range().isDate()).isTrue();
            assertThat(f.range().date().field()).isEqualTo("fetchedAt");
        });
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
