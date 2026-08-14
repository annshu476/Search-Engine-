package com.searchengine.indexer.search;

import com.searchengine.indexer.config.SearchProperties;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ElasticsearchSearchQueryBuilder {

    private final SearchProperties searchProperties;

    public Query buildMultiMatchQuery(String queryText) {
        SearchProperties.Relevance relevance = searchProperties.getRelevance();

        List<String> boostedFields = List.of(
                "title^" + relevance.getTitleBoost(),
                "headings^" + relevance.getHeadingsBoost(),
                "metaDescription^" + relevance.getMetaDescriptionBoost(),
                "bodyText^" + relevance.getBodyBoost()
        );

        return Query.of(q -> q
                .multiMatch(m -> m
                        .query(queryText)
                        .fields(boostedFields)
                        .type(TextQueryType.BestFields)
                        .minimumShouldMatch("1")
                )
        );
    }
}
