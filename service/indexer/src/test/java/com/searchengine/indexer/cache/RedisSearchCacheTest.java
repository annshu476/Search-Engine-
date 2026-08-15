package com.searchengine.indexer.cache;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.search.SearchCacheKey;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSearchCacheTest {

    private SearchProperties searchProperties;
    private MeterRegistry meterRegistry;
    private RedisSearchCache redisSearchCache;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.getRedis().setEnabled(true);

        meterRegistry = new SimpleMeterRegistry();
        // Pass null searchResponseRedisTemplate to test fallback
        redisSearchCache = new RedisSearchCache(searchProperties, meterRegistry);
    }

    @Test
    void get_nullTemplate_returnsNull() {
        SearchCacheKey key = new SearchCacheKey("spring", 0, 10, "relevance", "en", "text/html", 200, null, null, "v2");
        SearchResponse response = redisSearchCache.get(key);

        assertThat(response).isNull();
    }

    @Test
    void put_nullTemplate_doesNotThrow() {
        SearchCacheKey key = new SearchCacheKey("spring", 0, 10, "relevance", "en", "text/html", 200, null, null, "v2");
        SearchResponse response = new SearchResponse("spring", 1L, 0, 10, 1, "relevance", List.of());

        redisSearchCache.put(key, response);
        // Should complete silently without throwing
    }
}
