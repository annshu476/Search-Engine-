package com.searchengine.indexer.search;

import com.searchengine.indexer.config.SearchProperties;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
