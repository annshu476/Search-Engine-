package com.searchengine.indexer.service;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.exception.SearchQueryException;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchResult;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    private IndexerElasticsearchProperties indexerProperties;
    private SearchProperties searchProperties;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        indexerProperties = new IndexerElasticsearchProperties();
        indexerProperties.setIndexName("test-search-documents");

        searchProperties = new SearchProperties();
        searchProperties.setMaxResults(10);
        searchProperties.setMaxQueryLength(200);

        searchService = new SearchService(elasticsearchClient, indexerProperties, searchProperties);
    }

    @Test
    void search_validQuery_returnsMatchingResults() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockEsResponse = mock(co.elastic.clients.elasticsearch.core.SearchResponse.class);
        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = mock(TotalHits.class);

        given(totalHits.value()).willReturn(1L);
        given(hitsMetadata.total()).willReturn(totalHits);

        Hit<Map> hit = mock(Hit.class);
        Map<String, Object> source = Map.of(
                "url", "https://spring.io",
                "canonicalUrl", "https://spring.io",
                "urlHash", "abc123",
                "title", "Spring Framework",
                "metaDescription", "Spring makes Java easier",
                "bodyText", "Body text content that should not be in SearchResult",
                "language", "en",
                "wordCount", 450,
                "statusCode", 200
        );
        given(hit.source()).willReturn(source);
        given(hitsMetadata.hits()).willReturn(List.of(hit));
        given(mockEsResponse.hits()).willReturn(hitsMetadata);

        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockEsResponse);

        SearchResponse response = searchService.search("spring");

        assertThat(response).isNotNull();
        assertThat(response.query()).isEqualTo("spring");
        assertThat(response.totalHits()).isEqualTo(1L);
        assertThat(response.results()).hasSize(1);

        SearchResult result = response.results().get(0);
        assertThat(result.url()).isEqualTo("https://spring.io");
        assertThat(result.canonicalUrl()).isEqualTo("https://spring.io");
        assertThat(result.urlHash()).isEqualTo("abc123");
        assertThat(result.title()).isEqualTo("Spring Framework");
        assertThat(result.metaDescription()).isEqualTo("Spring makes Java easier");
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.wordCount()).isEqualTo(450);
        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    void search_zeroResults_returnsEmptyList() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockEsResponse = mock(co.elastic.clients.elasticsearch.core.SearchResponse.class);
        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = mock(TotalHits.class);

        given(totalHits.value()).willReturn(0L);
        given(hitsMetadata.total()).willReturn(totalHits);
        given(hitsMetadata.hits()).willReturn(List.of());
        given(mockEsResponse.hits()).willReturn(hitsMetadata);

        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockEsResponse);

        SearchResponse response = searchService.search("nonexistent");

        assertThat(response).isNotNull();
        assertThat(response.query()).isEqualTo("nonexistent");
        assertThat(response.totalHits()).isEqualTo(0L);
        assertThat(response.results()).isEmpty();
    }

    @Test
    void search_nullQuery_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> searchService.search(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query must not be blank");
    }

    @Test
    void search_blankQuery_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> searchService.search("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query must not be blank");
    }

    @Test
    void search_queryExceedingMaxLength_throwsIllegalArgumentException() {
        String longQuery = "a".repeat(201);
        assertThatThrownBy(() -> searchService.search(longQuery))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query exceeds maximum allowed length");
    }

    @Test
    void search_elasticsearchException_throwsSearchQueryException() throws IOException {
        given(elasticsearchClient.search(any(Function.class), any(Class.class)))
                .willThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> searchService.search("java"))
                .isInstanceOf(SearchQueryException.class)
                .hasMessageContaining("Elasticsearch search query failed for: java");
    }
}
