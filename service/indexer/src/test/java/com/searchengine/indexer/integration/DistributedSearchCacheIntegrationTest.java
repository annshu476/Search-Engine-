package com.searchengine.indexer.integration;

import com.searchengine.indexer.cache.RedisSearchCache;
import com.searchengine.indexer.cache.SearchCacheInvalidationPublisher;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.search.SearchCacheKey;
import com.searchengine.indexer.service.SearchCacheService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
class DistributedSearchCacheIntegrationTest {

    @Autowired
    private SearchCacheService searchCacheService;

    @Autowired
    private RedisSearchCache redisSearchCache;

    @Autowired
    private SearchCacheInvalidationPublisher invalidationPublisher;

    @Test
    void distributedL1L2CacheAndPubSubInvalidation_functionsCorrectly() throws Exception {
        String query = "dist_test_" + UUID.randomUUID();
        SearchCacheKey key = new SearchCacheKey(query, 0, 10, "relevance", null, null, null, null, null, "v2");
        SearchResponse response = new SearchResponse(query, 1L, 0, 10, 1, "relevance", List.of());

        // 1. Put into cache (L1 & L2)
        searchCacheService.put(key, response);

        // 2. Clear local L1 Caffeine cache manually to simulate Instance B checking L2 Redis
        searchCacheService.clearLocalCache();
        assertThat(searchCacheService.get(key)).isNotNull(); // L2 hit populates L1 again!

        // 3. Trigger Pub/Sub Invalidation
        searchCacheService.invalidateAll();
        Thread.sleep(100);

        // 4. Verify local cache cleared
        assertThat(searchCacheService.size()).isEqualTo(0L);
    }
}
