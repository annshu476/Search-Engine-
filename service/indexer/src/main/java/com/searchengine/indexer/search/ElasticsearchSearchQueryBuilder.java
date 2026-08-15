package com.searchengine.indexer.search;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.dto.SearchFilter;
import com.searchengine.indexer.model.search.ParsedSearchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ElasticsearchSearchQueryBuilder {

    private final SearchProperties searchProperties;
    private final SearchQueryEnhancer searchQueryEnhancer;

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

    public Query buildMultiMatchQueryWithoutFuzziness(String queryText) {
        List<String> fields = List.of("title", "headings", "metaDescription", "bodyText");
        return Query.of(q -> q.multiMatch(m -> m
                .query(queryText)
                .fields(fields)
                .type(TextQueryType.BestFields)
        ));
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
        SearchQueryParser parser = new SearchQueryParser();
        ParsedSearchQuery parsedQuery = parser.parse(queryText);
        return buildSearchQuery(parsedQuery, filter);
    }

    public Query buildSearchQuery(ParsedSearchQuery parsedQuery, SearchFilter filter) {
        List<Query> mustQueries = new ArrayList<>();
        List<Query> filterQueries = new ArrayList<>();
        List<Query> mustNotQueries = new ArrayList<>();

        Map<String, List<String>> synonymsMap = searchQueryEnhancer != null ? searchQueryEnhancer.getSynonymsForTerms(parsedQuery) : Map.of();

        if (parsedQuery.isSimpleNormalQuery()) {
            List<String> baseTerms = !parsedQuery.normalTerms().isEmpty() ? new ArrayList<>(parsedQuery.normalTerms()) : new ArrayList<>(parsedQuery.exactPhrases());
            List<String> expandedTerms = new ArrayList<>(baseTerms);

            for (String term : baseTerms) {
                List<String> syns = synonymsMap.get(term);
                if (syns != null) {
                    for (String syn : syns) {
                        if (!expandedTerms.contains(syn)) {
                            expandedTerms.add(syn);
                        }
                    }
                }
            }

            String queryText = String.join(" ", expandedTerms);
            Query relevanceQuery = buildRelevanceQuery(queryText);
            mustQueries.add(relevanceQuery);
        } else {
            List<String> positiveTerms = new ArrayList<>();
            positiveTerms.addAll(parsedQuery.normalTerms());
            positiveTerms.addAll(parsedQuery.exactPhrases());
            positiveTerms.addAll(parsedQuery.requiredTerms());
            positiveTerms.addAll(parsedQuery.requiredPhrases());

            for (String term : new ArrayList<>(positiveTerms)) {
                List<String> syns = synonymsMap.get(term);
                if (syns != null) {
                    for (String syn : syns) {
                        if (!positiveTerms.contains(syn)) {
                            positiveTerms.add(syn);
                        }
                    }
                }
            }

            if (!positiveTerms.isEmpty()) {
                String fullPositiveText = String.join(" ", positiveTerms);
                mustQueries.add(buildRelevanceQuery(fullPositiveText));
            }

            for (String reqTerm : parsedQuery.requiredTerms()) {
                List<String> syns = synonymsMap.get(reqTerm);
                if (syns != null && !syns.isEmpty()) {
                    List<Query> synShoulds = new ArrayList<>();
                    synShoulds.add(buildMultiMatchQuery(reqTerm));
                    for (String syn : syns) {
                        synShoulds.add(buildMultiMatchQuery(syn));
                    }
                    mustQueries.add(Query.of(q -> q.bool(b -> b.should(synShoulds).minimumShouldMatch("1"))));
                } else {
                    mustQueries.add(buildMultiMatchQuery(reqTerm));
                }
            }

            for (String reqPhrase : parsedQuery.requiredPhrases()) {
                mustQueries.add(Query.of(q -> q.multiMatch(m -> m
                        .query(reqPhrase)
                        .fields(List.of("title", "headings", "metaDescription", "bodyText"))
                        .type(TextQueryType.Phrase)
                )));
            }

            for (String excTerm : parsedQuery.excludedTerms()) {
                mustNotQueries.add(buildMultiMatchQueryWithoutFuzziness(excTerm));
            }

            for (String excPhrase : parsedQuery.excludedPhrases()) {
                mustNotQueries.add(Query.of(q -> q.multiMatch(m -> m
                        .query(excPhrase)
                        .fields(List.of("title", "headings", "metaDescription", "bodyText"))
                        .type(TextQueryType.Phrase)
                )));
            }
        }

        if (filter != null && filter.hasFilters()) {
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
        }

        if (mustQueries.size() == 1 && filterQueries.isEmpty() && mustNotQueries.isEmpty()) {
            return mustQueries.get(0);
        }

        return Query.of(q -> q.bool(b -> {
            if (!mustQueries.isEmpty()) {
                b.must(mustQueries);
            }
            if (!filterQueries.isEmpty()) {
                b.filter(filterQueries);
            }
            if (!mustNotQueries.isEmpty()) {
                b.mustNot(mustNotQueries);
            }
            return b;
        }));
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
