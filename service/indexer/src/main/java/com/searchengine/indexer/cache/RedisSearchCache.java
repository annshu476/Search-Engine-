package com.searchengine.indexer.cache;

import com.searchengine.indexer.analytics.SearchAnalyticsService;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.search.SearchCacheKey;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSearchCache {

    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;

    @Autowired(required = false)
    private RedisTemplate<String, SearchResponse> searchResponseRedisTemplate;

    public SearchResponse get(SearchCacheKey key) {
        if (!searchProperties.getRedis().isEnabled() || searchResponseRedisTemplate == null) {
            return null;
        }

        try {
            String redisKey = buildRedisKey(key);
            SearchResponse response = searchResponseRedisTemplate.opsForValue().get(redisKey);
            if (response != null) {
                meterRegistry.counter("search.cache.redis.hit").increment();
                log.info("REDIS_CACHE_HIT key={}", redisKey);
                return response;
            } else {
                meterRegistry.counter("search.cache.redis.miss").increment();
                log.debug("REDIS_CACHE_MISS key={}", redisKey);
                return null;
            }
        } catch (Exception e) {
            meterRegistry.counter("search.cache.redis.error").increment();
            log.warn("REDIS_CONNECTION_FAILED cache get error reason=\"{}\"", e.getMessage());
            return null;
        }
    }

    public void put(SearchCacheKey key, SearchResponse response) {
        if (!searchProperties.getRedis().isEnabled() || searchResponseRedisTemplate == null || response == null) {
            return;
        }

        try {
            String redisKey = buildRedisKey(key);
            searchResponseRedisTemplate.opsForValue().set(
                    redisKey,
                    response,
                    searchProperties.getCache().getTtl()
            );
            meterRegistry.counter("search.cache.redis.put").increment();
            log.info("REDIS_CACHE_PUT key={} ttl={}", redisKey, searchProperties.getCache().getTtl());
        } catch (Exception e) {
            meterRegistry.counter("search.cache.redis.error").increment();
            log.warn("REDIS_CONNECTION_FAILED cache put error reason=\"{}\"", e.getMessage());
        }
    }

    public void clear() {
        if (!searchProperties.getRedis().isEnabled() || searchResponseRedisTemplate == null) {
            return;
        }

        try {
            String pattern = searchProperties.getRedis().getKeyPrefix() + "search:*";
            Set<String> keys = searchResponseRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                searchResponseRedisTemplate.delete(keys);
                log.info("REDIS_CACHE_CLEARED deletedKeys={}", keys.size());
            }
        } catch (Exception e) {
            meterRegistry.counter("search.cache.redis.error").increment();
            log.warn("REDIS_CONNECTION_FAILED cache clear error reason=\"{}\"", e.getMessage());
        }
    }

    private String buildRedisKey(SearchCacheKey key) {
        String canonicalString = key.toString();
        String hash = SearchAnalyticsService.computeQueryHash(canonicalString);
        return searchProperties.getRedis().getKeyPrefix() + "search:" + hash;
    }
}
