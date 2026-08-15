package com.searchengine.indexer.service;

import com.searchengine.indexer.analytics.SearchAnalyticsService;
import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.exception.SearchQueryException;
import com.searchengine.indexer.model.analytics.SearchAnalyticsSnapshot;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.search.ElasticsearchSearchQueryBuilder;
import com.searchengine.indexer.search.SearchQueryEnhancer;
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
import java.time.Duration;
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

    @Mock
    private SearchSpellCorrectionService searchSpellCorrectionService;

    private IndexerElasticsearchProperties indexerProperties;
    private SearchProperties searchProperties;
    private SearchQueryEnhancer searchQueryEnhancer;
    private ElasticsearchSearchQueryBuilder searchQueryBuilder;
    private SearchQueryParser searchQueryParser;
    private MeterRegistry meterRegistry;
    private SearchCacheService searchCacheService;
    private SearchAnalyticsService searchAnalyticsService;
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
        searchProperties.getCache().setEnabled(true);
        searchProperties.getCache().setMaximumSize(100);
        searchProperties.getCache().setTtl(Duration.ofSeconds(10));
        searchProperties.getAnalytics().setEnabled(true);
        searchProperties.getAnalytics().setMaximumQueryEntries(100);
        searchProperties.getAnalytics().setTopQueryLimit(5);
        searchProperties.getAnalytics().setQueryRetention(Duration.ofHours(1));
        searchProperties.getSynonyms().setEnabled(true);
        searchProperties.getSpellCorrection().setEnabled(true);

        meterRegistry = new SimpleMeterRegistry();
        searchQueryEnhancer = new SearchQueryEnhancer(searchProperties, meterRegistry);
        searchQueryBuilder = new ElasticsearchSearchQueryBuilder(searchProperties, searchQueryEnhancer);
        searchQueryParser = new SearchQueryParser();

        searchCacheService = new SearchCacheService(searchProperties, meterRegistry);
        searchCacheService.init();

        searchAnalyticsService = new SearchAnalyticsService(searchProperties, meterRegistry);
        searchAnalyticsService.init();

        searchService = new SearchService(elasticsearchClient, indexerProperties, searchProperties, searchQueryBuilder, searchQueryParser, searchCacheService, searchAnalyticsService, searchSpellCorrectionService, meterRegistry);
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
    void search_hitsPresent_doesNotInvokeSpellCorrection() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> mockResponse = createMockEsResponse(1L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(mockResponse);

        SearchResponse response = searchService.search("spring", null, null, null, null, null, 0, 10, "relevance");
        assertThat(response.totalHits()).isEqualTo(1L);
        assertThat(response.correctedQuery()).isNull();
    }

    @Test
    void search_zeroHits_triggersSpellCorrectionAndFallbackSearch() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> zeroHitsResp = createMockEsResponse(0L, null);
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> correctedHitsResp = createMockEsResponse(2L, null);

        given(elasticsearchClient.search(any(Function.class), any(Class.class)))
                .willReturn(zeroHitsResp)
                .willReturn(correctedHitsResp);

        given(searchSpellCorrectionService.suggestCorrection(any(), any())).willReturn("spring boot");

        SearchResponse response = searchService.search("sprng boot", null, null, null, null, null, 0, 10, "relevance");

        assertThat(response.query()).isEqualTo("sprng boot");
        assertThat(response.correctedQuery()).isEqualTo("spring boot");
        assertThat(response.totalHits()).isEqualTo(2L);

        assertThat(meterRegistry.counter("search.correction.attempts").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("search.correction.applied").count()).isEqualTo(1.0);
    }

    @Test
    void search_zeroHits_correctionFailed_returnsOriginalZeroHitResponse() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> zeroHitsResp = createMockEsResponse(0L, null);
        given(elasticsearchClient.search(any(Function.class), any(Class.class))).willReturn(zeroHitsResp);

        given(searchSpellCorrectionService.suggestCorrection(any(), any())).willReturn(null);

        SearchResponse response = searchService.search("unknownxyz", null, null, null, null, null, 0, 10, "relevance");

        assertThat(response.query()).isEqualTo("unknownxyz");
        assertThat(response.correctedQuery()).isNull();
        assertThat(response.totalHits()).isEqualTo(0L);
    }
}
