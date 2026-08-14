package com.searchengine.indexer.service;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.exception.SearchQueryException;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchResult;
import com.searchengine.indexer.search.ElasticsearchSearchQueryBuilder;
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
    private ElasticsearchSearchQueryBuilder searchQueryBuilder;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        indexerProperties = new IndexerElasticsearchProperties();
        indexerProperties.setIndexName("test-search-documents");

        searchProperties = new SearchProperties();
        searchProperties.setMaxResults(10);
        searchProperties.setMaxPageSize(50);
        searchProperties.setMaxQueryLength(200);

        searchQueryBuilder = new ElasticsearchSearchQueryBuilder(searchProperties);
        searchService = new SearchService(elasticsearchClient, indexerProperties, searchProperties, searchQueryBuilder);
    }

    private co.elastic.clients.elasticsearch.core.SearchResponse<Map> createMockEsResponse(long hitsCount, Map<String, List<String>> highlights) {
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
            given(hit.highlight()).willReturn(highlights != null ? highlights : Map.of());
            given(hitsMetadata.hits()).willReturn(List.of(hit));
        } else {
            given(hitsMetadata.hits()).willReturn(List.of());
        }

        given(mockEsResponse.hits()).willReturn(hitsMetadata);
        return mockEsResponse;
    }

    @Test
    void search_noFilters_executesSuccessfully() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", null, null, null, null, null, 0, 10, "relevance");

        assertThat(response.query()).isEqualTo("spring");
        assertThat(response.totalHits()).isEqualTo(1L);
    }

    @Test
    void search_languageFilter_executesSuccessfully() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", "en", null, null, null, null, 0, 10, "relevance");

        assertThat(response.query()).isEqualTo("spring");
        assertThat(response.results()).hasSize(1);
    }

    @Test
    void search_contentTypeFilter_executesSuccessfully() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", null, "text/html", null, null, null, 0, 10, "relevance");

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void search_statusCodeFilter_executesSuccessfully() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", null, null, 200, null, null, 0, 10, "relevance");

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void search_fromDateFilter_executesSuccessfully() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", null, null, null, "2026-08-01T00:00:00Z", null, 0, 10, "relevance");

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void search_toDateFilter_executesSuccessfully() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", null, null, null, null, "2026-08-14T23:59:59Z", 0, 10, "relevance");

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void search_bothDateFilters_executesSuccessfully() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", null, null, null, "2026-08-01T00:00:00Z", "2026-08-14T23:59:59Z", 0, 10, "relevance");

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void search_multipleFiltersTogether_executesSuccessfully() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", "en", "text/html", 200, "2026-08-01T00:00:00Z", "2026-08-14T23:59:59Z", 0, 10, "relevance");

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void search_filtersAndPagination_executesSuccessfully() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(25L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", "en", null, 200, null, null, 1, 10, "relevance");

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalHits()).isEqualTo(25L);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void search_filtersAndRelevanceSorting_executesSuccessfully() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", "en", null, 200, null, null, 0, 10, "relevance");

        assertThat(response.sort()).isEqualTo("relevance");
    }

    @Test
    void search_filtersAndNewestSorting_executesSuccessfully() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", "en", null, 200, null, null, 0, 10, "newest");

        assertThat(response.sort()).isEqualTo("newest");
    }

    @Test
    void search_filtersAndFuzzySearch_executesSuccessfully() throws IOException {
        searchProperties.getFuzzy().setEnabled(true);
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("sprng boot", "en", null, null, null, null, 0, 10, "relevance");

        assertThat(response.query()).isEqualTo("sprng boot");
    }

    @Test
    void search_filtersAndHighlighting_executesSuccessfully() throws IOException {
        Map<String, List<String>> highlights = Map.of("title", List.of("<em>Spring</em>"));
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, highlights);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", "en", null, null, null, null, 0, 10, "relevance");

        assertThat(response.results().get(0).highlights()).containsEntry("title", List.of("<em>Spring</em>"));
    }

    @Test
    void search_fromDateAfterToDate_rejected() {
        assertThatThrownBy(() -> searchService.search("spring", null, null, null, "2026-08-15T00:00:00Z", "2026-08-14T00:00:00Z", 0, 10, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromDate must not be after toDate");
    }

    @Test
    void search_invalidStatusCode_rejected() {
        assertThatThrownBy(() -> searchService.search("spring", null, null, 99, null, null, 0, 10, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Status code must be between 100 and 599");

        assertThatThrownBy(() -> searchService.search("spring", null, null, 600, null, null, 0, 10, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Status code must be between 100 and 599");
    }

    @Test
    void search_blankLanguage_rejected() {
        assertThatThrownBy(() -> searchService.search("spring", "   ", null, null, null, null, 0, 10, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Language filter must not be blank");
    }

    @Test
    void search_blankContentType_rejected() {
        assertThatThrownBy(() -> searchService.search("spring", null, "   ", null, null, null, 0, 10, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Content-Type filter must not be blank");
    }

    @Test
    void search_invalidDateFormat_rejected() {
        assertThatThrownBy(() -> searchService.search("spring", null, null, null, "invalid-date", null, 0, 10, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid date format for fromDate");
    }
}
