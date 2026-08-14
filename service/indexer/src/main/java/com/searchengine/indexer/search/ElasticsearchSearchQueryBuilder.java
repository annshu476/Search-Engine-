package com.searchengine.indexer.search;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.dto.SearchFilter;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ElasticsearchSearchQueryBuilder {

    private final SearchProperties searchProperties;

    public Query buildMultiMatchQuery(String queryText) {
        SearchProperties.Relevance relevance = searchProperties.getRelevance();
        SearchProperties.Fuzzy fuzzy = searchProperties.getFuzzy();

        List<String> boostedFields = List.of(
                "title^" + relevance.getTitleBoost(),
                "headings^" + relevance.getHeadingsBoost(),
                "metaDescription^" + relevance.getMetaDescriptionBoost(),
                "bodyText^" + relevance.getBodyBoost()
        );

        return Query.of(q -> q
                .multiMatch(m -> {
                    m.query(queryText)
                     .fields(boostedFields)
                     .type(TextQueryType.BestFields)
                     .minimumShouldMatch("1");

                    if (fuzzy.isEnabled()) {
                        m.fuzziness(fuzzy.getFuzziness());
                    }

                    return m;
                })
        );
    }

    public Query buildRelevanceQuery(String queryText) {
        String trimmedQuery = queryText.trim();
        String[] tokens = trimmedQuery.split("\\s+");

        Query baselineQuery = buildMultiMatchQuery(trimmedQuery);
        if (tokens.length <= 1) {
            return baselineQuery;
        }

        SearchProperties.Relevance relevance = searchProperties.getRelevance();
        double pBoost = relevance.getPhraseBoost();

        List<String> phraseFields = List.of(
                "title^" + (relevance.getTitleBoost() * pBoost),
                "headings^" + (relevance.getHeadingsBoost() * pBoost),
                "metaDescription^" + (relevance.getMetaDescriptionBoost() * pBoost),
                "bodyText^" + (relevance.getBodyBoost() * pBoost)
        );

        Query phraseMultiMatchQuery = Query.of(q -> q
                .multiMatch(m -> m
                        .query(trimmedQuery)
                        .fields(phraseFields)
                        .type(TextQueryType.Phrase)
                )
        );

        float tPhraseBoost = (float) relevance.getTitlePhraseBoost();
        Query titlePhraseQuery = Query.of(q -> q
                .matchPhrase(m -> m
                        .field("title")
                        .query(trimmedQuery)
                        .boost(tPhraseBoost)
                )
        );

        return Query.of(q -> q
                .bool(b -> b
                        .should(baselineQuery)
                        .should(phraseMultiMatchQuery)
                        .should(titlePhraseQuery)
                        .minimumShouldMatch("1")
                )
        );
    }

    public Query buildSearchQuery(String queryText, SearchFilter filter) {
        Query relevanceQuery = buildRelevanceQuery(queryText);
        if (filter == null || !filter.hasFilters()) {
            return relevanceQuery;
        }

        List<Query> filterQueries = new ArrayList<>();

        if (filter.language() != null) {
            filterQueries.add(Query.of(q -> q.term(t -> t.field("language").value(filter.language()))));
        }

        if (filter.contentType() != null) {
            filterQueries.add(Query.of(q -> q.term(t -> t.field("contentType").value(filter.contentType()))));
        }

        if (filter.statusCode() != null) {
            filterQueries.add(Query.of(q -> q.term(t -> t.field("statusCode").value(filter.statusCode()))));
        }

        if (filter.fromDate() != null || filter.toDate() != null) {
            filterQueries.add(Query.of(q -> q.range(r -> r.date(d -> {
                d.field("fetchedAt");
                if (filter.fromDate() != null) {
                    d.gte(filter.fromDate().toString());
                }
                if (filter.toDate() != null) {
                    d.lte(filter.toDate().toString());
                }
                return d;
            }))));
        }

        if (filterQueries.isEmpty()) {
            return relevanceQuery;
        }

        return Query.of(q -> q.bool(b -> b
                .must(relevanceQuery)
                .filter(filterQueries)
        ));
    }

    public Highlight buildHighlight() {
        SearchProperties.Highlight highlightProps = searchProperties.getHighlight();
        if (!highlightProps.isEnabled()) {
            return null;
        }

        HighlightField fieldConfig = HighlightField.of(hf -> hf
                .fragmentSize(highlightProps.getFragmentSize())
                .numberOfFragments(highlightProps.getNumberOfFragments())
        );

        return Highlight.of(h -> h
                .preTags(highlightProps.getPreTag())
                .postTags(highlightProps.getPostTag())
                .fields(Map.of(
                        "title", fieldConfig,
                        "headings", fieldConfig,
                        "metaDescription", fieldConfig,
                        "bodyText", fieldConfig
                ))
        );
    }
}
