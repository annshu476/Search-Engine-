package com.searchengine.indexer.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.service.SearchCacheService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchCacheInvalidationSubscriber implements MessageListener {

    private final SearchProperties searchProperties;
    private final SearchCacheService searchCacheService;
    private final MeterRegistry meterRegistry;
    private final RedisConnectionFactory redisConnectionFactory;

    @PostConstruct
    public void init() {
        if (!searchProperties.getRedis().isEnabled() || redisConnectionFactory == null) {
            return;
        }

        try {
            String channel = searchProperties.getRedis().getKeyPrefix() + "cache-invalidation";
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(redisConnectionFactory);
            container.addMessageListener(this, new ChannelTopic(channel));
            container.afterPropertiesSet();
            container.start();
            log.info("REDIS_CACHE_INVALIDATION_SUBSCRIBER_STARTED channel={}", channel);
        } catch (Exception e) {
            log.warn("REDIS_CONNECTION_FAILED subscriber init error reason=\"{}\"", e.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            if (body.contains("SEARCH_CACHE_INVALIDATE_ALL")) {
                searchCacheService.clearLocalCache();
                meterRegistry.counter("search.cache.invalidation.received").increment();
                log.info("REDIS_CACHE_INVALIDATED local Caffeine cache cleared");
            }
        } catch (Exception e) {
            log.warn("REDIS_CACHE_INVALIDATION_FAILED malformed subscriber message reason=\"{}\"", e.getMessage());
        }
    }
}
