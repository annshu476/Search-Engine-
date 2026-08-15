package com.searchengine.indexer.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchengine.indexer.config.SearchProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchCacheInvalidationPublisher {

    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    public void publishInvalidation() {
        if (!searchProperties.getRedis().isEnabled() || stringRedisTemplate == null) {
            return;
        }

        try {
            String channel = searchProperties.getRedis().getKeyPrefix() + "cache-invalidation";
            Map<String, Object> messageMap = Map.of(
                    "type", "SEARCH_CACHE_INVALIDATE_ALL",
                    "timestamp", Instant.now().toString(),
                    "sourceInstance", INSTANCE_ID
            );
            String jsonMessage = objectMapper.writeValueAsString(messageMap);

            stringRedisTemplate.convertAndSend(channel, jsonMessage);
            meterRegistry.counter("search.cache.invalidation.published").increment();
            log.info("REDIS_CACHE_INVALIDATED channel={}", channel);
        } catch (Exception e) {
            meterRegistry.counter("search.cache.invalidation.failed").increment();
            log.warn("REDIS_CACHE_INVALIDATION_FAILED reason=\"{}\"", e.getMessage());
        }
    }
}
