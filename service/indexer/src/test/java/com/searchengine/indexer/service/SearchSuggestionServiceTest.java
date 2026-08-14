package com.searchengine.indexer.service;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.exception.SearchSuggestionException;
import com.searchengine.indexer.model.dto.SearchSuggestionResponse;
import com.searchengine.indexer.search.ElasticsearchSuggestionQueryBuilder;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SearchSuggestionServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    private IndexerElasticsearchProperties indexerProperties;
    private SearchProperties searchProperties;
    private ElasticsearchSuggestionQueryBuilder suggestionQueryBuilder;
    private SearchSuggestionService suggestionService;

    @BeforeEach
    void setUp() {
        indexerProperties = new IndexerElasticsearchProperties();
        indexerProperties.setIndexName("test-search-documents");

        searchProperties = new SearchProperties();
        searchProperties.setMaxQueryLength(200);

        SearchProperties.Suggestions suggestionsProps = searchProperties.getSuggestions();
        suggestionsProps.setEnabled(true);
        suggestionsProps.setMaxResults(8);
        suggestionsProps.setMinPrefixLength(2);

        suggestionQueryBuilder = new ElasticsearchSuggestionQueryBuilder(searchProperties);
        suggestionService = new SearchSuggestionService(elasticsearchClient, indexerProperties, searchProperties, suggestionQueryBuilder);
    }

    private co.elastic.clients.elasticsearch.core.SearchResponse<Map> createMockEsResponse(List<Map<String, Object>> sources) {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockEsResponse = mock(co.elastic.clients.elasticsearch.core.SearchResponse.class);
        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);

        List<Hit<Map>> hits = sources.stream().map(s -> {
            Hit<Map> hit = mock(Hit.class);
            given(hit.source()).willReturn(s);
            return hit;
        }).toList();

        given(hitsMetadata.hits()).willReturn(hits);
        given(mockEsResponse.hits()).willReturn(hitsMetadata);
        return mockEsResponse;
    }

    @Test
    void suggest_validPrefix_returnsSuggestions() throws IOException {
        Map<String, Object> source1 = Map.of("title", "Spring Boot Framework", "headings", List.of("Spring Boot Tutorial"));
        Map<String, Object> source2 = Map.of("title", "Spring Framework Guide");
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(List.of(source1, source2));
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchSuggestionResponse response = suggestionService.suggest("spr");

        assertThat(response.query()).isEqualTo("spr");
        assertThat(response.suggestions()).containsExactly("spring boot framework", "spring boot tutorial", "spring framework guide");
    }

    @Test
    void suggest_trimsLeadingAndTrailingWhitespace() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(List.of());
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchSuggestionResponse response = suggestionService.suggest("   spr   ");

        assertThat(response.query()).isEqualTo("spr");
        assertThat(response.suggestions()).isEmpty();
    }

    @Test
    void suggest_removesDuplicatesAndLimitsToMaxResults() throws IOException {
        searchProperties.getSuggestions().setMaxResults(2);

        Map<String, Object> source1 = Map.of("title", "Spring Boot Framework");
        Map<String, Object> source2 = Map.of("title", "Spring Boot Framework");
        Map<String, Object> source3 = Map.of("title", "Spring Security Overview");
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(List.of(source1, source2, source3));
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchSuggestionResponse response = suggestionService.suggest("spring");

        assertThat(response.suggestions()).hasSize(2);
        assertThat(response.suggestions()).containsExactly("spring boot framework", "spring security overview");
    }

    @Test
    void suggest_nullOrBlankQuery_rejected() {
        assertThatThrownBy(() -> suggestionService.suggest(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query must not be blank");

        assertThatThrownBy(() -> suggestionService.suggest("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query must not be blank");
    }

    @Test
    void suggest_prefixShorterThanMinLength_rejected() {
        assertThatThrownBy(() -> suggestionService.suggest("s"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search prefix must contain at least 2 characters");
    }

    @Test
    void suggest_queryExceedingMaxQueryLength_rejected() {
        String longQuery = "a".repeat(201);
        assertThatThrownBy(() -> suggestionService.suggest(longQuery))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query exceeds maximum allowed length");
    }

    @Test
    void suggest_disabled_throwsSearchSuggestionException() {
        searchProperties.getSuggestions().setEnabled(false);

        assertThatThrownBy(() -> suggestionService.suggest("spring"))
                .isInstanceOf(SearchSuggestionException.class)
                .hasMessageContaining("Search suggestion service is disabled");
    }

    @Test
    void suggest_elasticsearchFailure_throwsSearchSuggestionException() throws IOException {
        given(elasticsearchClient.search(any(Function.class), any(Class.class)))
                .willThrow(new RuntimeException("ES cluster unreachable"));

        assertThatThrownBy(() -> suggestionService.suggest("spring"))
                .isInstanceOf(SearchSuggestionException.class)
                .hasMessageContaining("Search suggestion service temporarily unavailable");
    }
}
