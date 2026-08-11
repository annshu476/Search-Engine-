package com.searchengine.urlfrontier.repository;

import com.searchengine.urlfrontier.exception.RedisUnavailableException;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Stores hashes of visited URLs for the bounded Redis deduplication window. */
@Repository
public class VisitedUrlRepository {
    static final Duration VISITED_URL_TTL = Duration.ofDays(7);
    private static final Logger LOGGER = LoggerFactory.getLogger(VisitedUrlRepository.class);
    private static final String KEY_PREFIX = "visited:";
    private final StringRedisTemplate stringRedisTemplate;
    public VisitedUrlRepository(StringRedisTemplate stringRedisTemplate) { this.stringRedisTemplate = stringRedisTemplate; }
    public boolean storeIfAbsent(String urlHash, Instant discoveredAt) {
        try {
            Boolean stored = stringRedisTemplate.opsForValue().setIfAbsent(buildKey(urlHash), discoveredAt.toString(), VISITED_URL_TTL);
            if (stored == null) { LOGGER.error("REDIS_ERROR operation=store_visited_url"); throw new RedisUnavailableException(null); }
            return stored;
        } catch (DataAccessException exception) {
            LOGGER.error("REDIS_ERROR operation=store_visited_url");
            throw new RedisUnavailableException(exception);
        }
    }
    String buildKey(String urlHash) { return KEY_PREFIX + urlHash; }
}
