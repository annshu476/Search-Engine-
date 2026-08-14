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
        searchProperties.setMaxPageSize(50);
        searchProperties.setMaxQueryLength(200);

        searchService = new SearchService(elasticsearchClient, indexerProperties, searchProperties);
    }

    private co.elastic.clients.elasticsearch.core.SearchResponse<Map> createMockEsResponse(long hitsCount) {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockEsResponse = mock(co.elastic.clients.elasticsearch.core.SearchResponse.class);
        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = mock(TotalHits.class);

        given(totalHits.value()).willReturn(hitsCount);
        given(hitsMetadata.total()).willReturn(totalHits);

        if (hitsCount > 0) {
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
        } else {
            given(hitsMetadata.hits()).willReturn(List.of());
        }

        given(mockEsResponse.hits()).willReturn(hitsMetadata);
        return mockEsResponse;
    }

    @Test
    void search_defaultPageAndSize_appliesDefaults() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring");

        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.sort()).isEqualTo("relevance");
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void search_explicitPageAndSize_calculatesFromAndSizeCorrectly() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(25L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", 2, 10, "relevance");

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalHits()).isEqualTo(25L);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void search_totalPagesCalculation() throws IOException {
        // 0 hits -> 0 pages
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> res0 = createMockEsResponse(0L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(res0);
        assertThat(searchService.search("test", 0, 10, "relevance").totalPages()).isEqualTo(0);

        // 1 hit / size 10 -> 1 page
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> res1 = createMockEsResponse(1L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(res1);
        assertThat(searchService.search("test", 0, 10, "relevance").totalPages()).isEqualTo(1);

        // 10 hits / size 10 -> 1 page
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> res10 = createMockEsResponse(10L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(res10);
        assertThat(searchService.search("test", 0, 10, "relevance").totalPages()).isEqualTo(1);

        // 11 hits / size 10 -> 2 pages
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> res11 = createMockEsResponse(11L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(res11);
        assertThat(searchService.search("test", 0, 10, "relevance").totalPages()).isEqualTo(2);

        // 100 hits / size 25 -> 4 pages
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> res100 = createMockEsResponse(100L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(res100);
        assertThat(searchService.search("test", 0, 25, "relevance").totalPages()).isEqualTo(4);
    }

    @Test
    void search_page0Accepted() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        assertThat(searchService.search("test", 0, 10, "relevance").page()).isEqualTo(0);
    }

    @Test
    void search_negativePage_rejected() {
        assertThatThrownBy(() -> searchService.search("test", -1, 10, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page must be greater than or equal to 0");
    }

    @Test
    void search_size1Accepted() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        assertThat(searchService.search("test", 0, 1, "relevance").size()).isEqualTo(1);
    }

    @Test
    void search_size50Accepted() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        assertThat(searchService.search("test", 0, 50, "relevance").size()).isEqualTo(50);
    }

    @Test
    void search_sizeGreaterThanMaxPageSize_rejected() {
        assertThatThrownBy(() -> searchService.search("test", 0, 51, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page size must be between 1 and 50");
    }

    @Test
    void search_size0_rejected() {
        assertThatThrownBy(() -> searchService.search("test", 0, 0, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page size must be between 1 and 50");
    }

    @Test
    void search_relevanceSort_accepted() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("test", 0, 10, "relevance");
        assertThat(response.sort()).isEqualTo("relevance");
    }

    @Test
    void search_newestSort_accepted() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("test", 0, 10, "newest");
        assertThat(response.sort()).isEqualTo("newest");
    }

    @Test
    void search_unsupportedSort_rejected() {
        assertThatThrownBy(() -> searchService.search("test", 0, 10, "unsupported_sort"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported sort option: unsupported_sort");
    }

    @Test
    void search_elasticsearchFailure_throwsSearchQueryException() throws IOException {
        given(elasticsearchClient.search(any(Function.class), any(Class.class)))
                .willThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> searchService.search("java", 0, 10, "relevance"))
                .isInstanceOf(SearchQueryException.class)
                .hasMessageContaining("Elasticsearch search query failed for: java");
    }

    @Test
    void search_bodyTextNotReturnedInSearchResult() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", 0, 10, "relevance");
        SearchResult result = response.results().get(0);

        assertThat(result.url()).isEqualTo("https://spring.io");
        assertThat(result.title()).isEqualTo("Spring Framework");
        assertThat(SearchResult.class.getDeclaredFields()).noneMatch(field -> field.getName().equals("bodyText"));
    }
}
