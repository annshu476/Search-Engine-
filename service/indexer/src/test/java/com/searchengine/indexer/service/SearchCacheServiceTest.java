package com.searchengine.indexer.service;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.search.SearchCacheKey;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCacheServiceTest {

    private SearchProperties searchProperties;
    private MeterRegistry meterRegistry;
    private SearchCacheService searchCacheService;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.getCache().setEnabled(true);
        searchProperties.getCache().setMaximumSize(100);
        searchProperties.getCache().setTtl(Duration.ofSeconds(10));

        meterRegistry = new SimpleMeterRegistry();
        searchCacheService = new SearchCacheService(searchProperties, meterRegistry);
        searchCacheService.init();
    }

    @Test
    void cacheHit_returnsCachedResponseAndIncrementsHitMetric() {
        SearchCacheKey key = new SearchCacheKey("spring", 0, 10, "relevance", null, null, null, null, null, searchCacheService.getConfigVersion());
        SearchResponse response = new SearchResponse("spring", 1L, 0, 10, 1, "relevance", List.of());

        searchCacheService.put(key, response);
        SearchResponse cached = searchCacheService.get(key);

        assertThat(cached).isNotNull();
        assertThat(cached.query()).isEqualTo("spring");
        assertThat(meterRegistry.counter("search.cache.hit", "operation", "search").count()).isEqualTo(1.0);
    }

    @Test
    void cacheMiss_returnsNullAndIncrementsMissMetric() {
        SearchCacheKey key = new SearchCacheKey("nonexistent", 0, 10, "relevance", null, null, null, null, null, searchCacheService.getConfigVersion());
        SearchResponse cached = searchCacheService.get(key);

        assertThat(cached).isNull();
        assertThat(meterRegistry.counter("search.cache.miss", "operation", "search").count()).isEqualTo(1.0);
    }

    @Test
    void disabledCache_doesNotStoreOrReturnEntries() {
        searchProperties.getCache().setEnabled(false);
        SearchCacheService disabledService = new SearchCacheService(searchProperties, meterRegistry);
        disabledService.init();

        SearchCacheKey key = new SearchCacheKey("spring", 0, 10, "relevance", null, null, null, null, null, disabledService.getConfigVersion());
        SearchResponse response = new SearchResponse("spring", 1L, 0, 10, 1, "relevance", List.of());

        disabledService.put(key, response);
        assertThat(disabledService.get(key)).isNull();
    }

    @Test
    void invalidateAll_clearsCache() {
        SearchCacheKey key = new SearchCacheKey("spring", 0, 10, "relevance", null, null, null, null, null, searchCacheService.getConfigVersion());
        SearchResponse response = new SearchResponse("spring", 1L, 0, 10, 1, "relevance", List.of());

        searchCacheService.put(key, response);
        assertThat(searchCacheService.get(key)).isNotNull();

        searchCacheService.invalidateAll();
        assertThat(searchCacheService.get(key)).isNull();
    }
}
