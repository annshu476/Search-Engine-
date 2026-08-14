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
    void buildRelevanceQuery_singleWordQuery_returnsMultiMatchQueryWithoutPhraseClauses() {
        Query query = queryBuilder.buildRelevanceQuery("spring");

        assertThat(query.isMultiMatch()).isTrue();
        assertThat(query.multiMatch().query()).isEqualTo("spring");
    }

    @Test
    void buildRelevanceQuery_multiWordQuery_returnsBoolWithPhraseAndTitlePhraseBoosts() {
        Query query = queryBuilder.buildRelevanceQuery("spring boot");

        assertThat(query.isBool()).isTrue();
        BoolQuery bool = query.bool();
        assertThat(bool.should()).hasSize(3);

        // 1. Baseline multi_match best_fields
        Query baseline = bool.should().get(0);
        assertThat(baseline.isMultiMatch()).isTrue();
        assertThat(baseline.multiMatch().type()).isEqualTo(TextQueryType.BestFields);
        assertThat(baseline.multiMatch().fuzziness()).isEqualTo("AUTO");

        // 2. Phrase multi_match
        Query phraseMulti = bool.should().get(1);
        assertThat(phraseMulti.isMultiMatch()).isTrue();
        assertThat(phraseMulti.multiMatch().type()).isEqualTo(TextQueryType.Phrase);
        assertThat(phraseMulti.multiMatch().fields()).containsExactly(
                "title^8.0",
                "headings^6.0",
                "metaDescription^4.0",
                "bodyText^2.0"
        );

        // 3. Exact title phrase boost
        Query titlePhrase = bool.should().get(2);
        assertThat(titlePhrase.isMatchPhrase()).isTrue();
        assertThat(titlePhrase.matchPhrase().field()).isEqualTo("title");
        assertThat(titlePhrase.matchPhrase().query()).isEqualTo("spring boot");
        assertThat(titlePhrase.matchPhrase().boost()).isEqualTo(4.0f);
    }

    @Test
    void buildRelevanceQuery_multiWordQueryFuzzyDisabled_stillBuildsValidPhraseQuery() {
        searchProperties.getFuzzy().setEnabled(false);

        Query query = queryBuilder.buildRelevanceQuery("spring boot");

        assertThat(query.isBool()).isTrue();
        BoolQuery bool = query.bool();
        assertThat(bool.should()).hasSize(3);
        assertThat(bool.should().get(0).multiMatch().fuzziness()).isNull();
    }

    @Test
    void buildSearchQuery_noFilters_returnsRelevanceQuery() {
        SearchFilter filter = new SearchFilter(null, null, null, null, null);
        Query query = queryBuilder.buildSearchQuery("spring boot", filter);

        assertThat(query.isBool()).isTrue();
        assertThat(query.bool().should()).hasSize(3);
    }

    @Test
    void buildSearchQuery_filters_keepsPhraseLogicInScoringContextAndFiltersInFilterContext() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-14T23:59:59Z");
        SearchFilter filter = new SearchFilter("en", "text/html", 200, from, to);

        Query query = queryBuilder.buildSearchQuery("spring boot", filter);

        assertThat(query.isBool()).isTrue();
        BoolQuery outerBool = query.bool();

        // Must contains the relevance query
        assertThat(outerBool.must()).hasSize(1);
        Query relevanceQuery = outerBool.must().get(0);
        assertThat(relevanceQuery.isBool()).isTrue();
        assertThat(relevanceQuery.bool().should()).hasSize(3);

        // Filter contains non-scoring filters
        assertThat(outerBool.filter()).hasSize(4);
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
