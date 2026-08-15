package com.searchengine.indexer.service;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.exception.SearchQueryException;
import com.searchengine.indexer.exception.SearchQuerySyntaxException;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.search.ElasticsearchSearchQueryBuilder;
import com.searchengine.indexer.search.SearchQueryParser;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.SocketTimeoutException;
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
    private SearchQueryParser searchQueryParser;
    private MeterRegistry meterRegistry;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        indexerProperties = new IndexerElasticsearchProperties();
        indexerProperties.setIndexName("test-search-documents");

        searchProperties = new SearchProperties();
        searchProperties.setMaxResults(10);
        searchProperties.setMaxPageSize(50);
        searchProperties.setMaxQueryLength(200);
        searchProperties.setMaxPageDepth(10000);
        searchProperties.setMaxQueryTerms(5);
        searchProperties.setMaxQueryPhrases(2);
        searchProperties.setSlowQueryThresholdMs(50L);

        searchQueryBuilder = new ElasticsearchSearchQueryBuilder(searchProperties);
        searchQueryParser = new SearchQueryParser();
        meterRegistry = new SimpleMeterRegistry();
        searchService = new SearchService(elasticsearchClient, indexerProperties, searchProperties, searchQueryBuilder, searchQueryParser, meterRegistry);
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
    void search_noFilters_executesSuccessfullyAndRecordsMetrics() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", null, null, null, null, null, 0, 10, "relevance");

        assertThat(response.query()).isEqualTo("spring");
        assertThat(response.totalHits()).isEqualTo(1L);
        assertThat(meterRegistry.counter("search.requests").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("search.success").count()).isEqualTo(1.0);
    }

    @Test
    void search_deepPage_rejected() {
        assertThatThrownBy(() -> searchService.search("spring", null, null, null, null, null, 1001, 10, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Requested page is too deep");

        assertThat(meterRegistry.counter("search.validation.errors").count()).isEqualTo(1.0);
    }

    @Test
    void search_integerOverflow_protected() {
        assertThatThrownBy(() -> searchService.search("spring", null, null, null, null, null, Integer.MAX_VALUE, 50, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Requested page is too deep");

        assertThat(meterRegistry.counter("search.validation.errors").count()).isEqualTo(1.0);
    }

    @Test
    void search_maxAllowedPageOffset_succeeds() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", null, null, null, null, null, 200, 50, "relevance");

        assertThat(response.page()).isEqualTo(200);
        assertThat(response.size()).isEqualTo(50);
    }

    @Test
    void search_queryTermLimitExceeded_rejected() {
        assertThatThrownBy(() -> searchService.search("one two three four five six", null, null, null, null, null, 0, 10, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query is too complex");

        assertThat(meterRegistry.counter("search.validation.errors").count()).isEqualTo(1.0);
    }

    @Test
    void search_queryPhraseLimitExceeded_rejected() {
        assertThatThrownBy(() -> searchService.search("\"one two\" \"three four\" \"five six\"", null, null, null, null, null, 0, 10, "relevance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query is too complex");

        assertThat(meterRegistry.counter("search.validation.errors").count()).isEqualTo(1.0);
    }

    @Test
    void search_socketTimeout_throwsSearchQueryExceptionAndRecordsTimeoutMetric() throws IOException {
        given(elasticsearchClient.search(any(Function.class), any(Class.class)))
                .willThrow(new IOException("SocketTimeoutException: Read timed out", new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> searchService.search("spring", null, null, null, null, null, 0, 10, "relevance"))
                .isInstanceOf(SearchQueryException.class)
                .hasMessageContaining("Search query timed out");

        assertThat(meterRegistry.counter("search.timeouts").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("search.errors").count()).isEqualTo(1.0);
    }

    @Test
    void search_elasticsearchFailure_throwsSearchQueryExceptionAndRecordsErrorMetric() throws IOException {
        given(elasticsearchClient.search(any(Function.class), any(Class.class)))
                .willThrow(new IOException("Elasticsearch node unreachable"));

        assertThatThrownBy(() -> searchService.search("spring", null, null, null, null, null, 0, 10, "relevance"))
                .isInstanceOf(SearchQueryException.class)
                .hasMessageContaining("Elasticsearch search query failed");

        assertThat(meterRegistry.counter("search.errors").count()).isEqualTo(1.0);
    }
}
