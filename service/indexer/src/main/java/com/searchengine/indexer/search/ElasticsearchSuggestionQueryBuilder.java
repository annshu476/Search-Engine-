package com.searchengine.indexer.search;

import com.searchengine.indexer.config.SearchProperties;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ElasticsearchSuggestionQueryBuilder {

    private final SearchProperties searchProperties;

    public Query buildSuggestionQuery(String prefix) {
        SearchProperties.Relevance relevance = searchProperties.getRelevance();

        List<String> boostedFields = List.of(
                "title^" + relevance.getTitleBoost(),
                "headings^" + relevance.getHeadingsBoost(),
                "metaDescription^" + relevance.getMetaDescriptionBoost()
        );

        return Query.of(q -> q
                .multiMatch(m -> m
                        .query(prefix)
                        .fields(boostedFields)
                        .type(TextQueryType.PhrasePrefix)
                )
        );
    }
}
