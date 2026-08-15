package com.searchengine.indexer.search;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.search.ParsedSearchQuery;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryEnhancerTest {

    private SearchProperties searchProperties;
    private MeterRegistry meterRegistry;
    private SearchQueryEnhancer queryEnhancer;
    private SearchQueryParser queryParser;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.getSynonyms().setEnabled(true);
        searchProperties.getSynonyms().setMaximumSynonymsPerTerm(5);
        searchProperties.getSynonyms().setRules(List.of(
                "java, jdk",
                "js, javascript",
                "spring boot, springboot"
        ));

        meterRegistry = new SimpleMeterRegistry();
        queryEnhancer = new SearchQueryEnhancer(searchProperties, meterRegistry);
        queryParser = new SearchQueryParser();
    }

    @Test
    void normalTerm_expandsSynonym() {
        ParsedSearchQuery parsed = queryParser.parse("java spring");
        Map<String, List<String>> synonyms = queryEnhancer.getSynonymsForTerms(parsed);

        assertThat(synonyms).containsKey("java");
        assertThat(synonyms.get("java")).containsExactly("jdk");
    }

    @Test
    void requiredTerm_expandsSynonym() {
        ParsedSearchQuery parsed = queryParser.parse("+java spring");
        Map<String, List<String>> synonyms = queryEnhancer.getSynonymsForTerms(parsed);

        assertThat(synonyms).containsKey("java");
        assertThat(synonyms.get("java")).containsExactly("jdk");
    }

    @Test
    void excludedTerm_doesNotExpandSynonym() {
        ParsedSearchQuery parsed = queryParser.parse("spring -java");
        Map<String, List<String>> synonyms = queryEnhancer.getSynonymsForTerms(parsed);

        assertThat(synonyms).doesNotContainKey("java");
    }

    @Test
    void exactPhrase_doesNotExpandSynonym() {
        ParsedSearchQuery parsed = queryParser.parse("\"java spring\"");
        Map<String, List<String>> synonyms = queryEnhancer.getSynonymsForTerms(parsed);

        assertThat(synonyms).isEmpty();
    }

    @Test
    void multiWordSynonym_expandsCorrectly() {
        ParsedSearchQuery parsed = queryParser.parse("springboot framework");
        Map<String, List<String>> synonyms = queryEnhancer.getSynonymsForTerms(parsed);

        assertThat(synonyms).containsKey("springboot");
        assertThat(synonyms.get("springboot")).containsExactly("spring boot");
    }

    @Test
    void disabledSynonyms_returnsEmptyMap() {
        searchProperties.getSynonyms().setEnabled(false);
        ParsedSearchQuery parsed = queryParser.parse("java spring");
        Map<String, List<String>> synonyms = queryEnhancer.getSynonymsForTerms(parsed);

        assertThat(synonyms).isEmpty();
    }
}
