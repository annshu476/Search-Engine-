package com.searchengine.urlfrontier.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

@Tag("integration")
@SpringBootTest
class VisitedUrlRepositoryRedisIntegrationTest {

    @Autowired
    private VisitedUrlRepository visitedUrlRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private String redisKey;

    @BeforeEach
    void requireRedis() {
        try {
            String ping = stringRedisTemplate.getConnectionFactory().getConnection().ping();
            Assumptions.assumeTrue("PONG".equalsIgnoreCase(ping), "Local Redis is not available");
        } catch (RedisConnectionFailureException exception) {
            Assumptions.abort("Local Redis is not available");
        }
    }

    @AfterEach
    void cleanUp() {
        if (redisKey != null) {
            try {
                stringRedisTemplate.delete(redisKey);
            } catch (RedisConnectionFailureException ignored) {
                // Redis is optional for this explicit integration test; cleanup is best-effort.
            }
        }
    }

    @Test
    void storesOnceWithSevenDayTtlAndDoesNotRefreshTtlForDuplicates() {
        String urlHash = "integration-" + UUID.randomUUID();
        redisKey = "visited:" + urlHash;
        Instant firstTimestamp = Instant.parse("2026-07-31T10:00:00Z");
        Instant duplicateTimestamp = firstTimestamp.plusSeconds(60);

        boolean firstStored = visitedUrlRepository.storeIfAbsent(urlHash, firstTimestamp);
        Duration ttlAfterFirstStore = Duration.ofSeconds(stringRedisTemplate.getExpire(redisKey));
        String persistedValue = stringRedisTemplate.opsForValue().get(redisKey);

        boolean duplicateStored = visitedUrlRepository.storeIfAbsent(urlHash, duplicateTimestamp);
        Duration ttlAfterDuplicate = Duration.ofSeconds(stringRedisTemplate.getExpire(redisKey));

        assertThat(firstStored).isTrue();
        assertThat(duplicateStored).isFalse();
        assertThat(persistedValue).isEqualTo(firstTimestamp.toString());
        assertThat(ttlAfterFirstStore).isBetween(Duration.ofDays(6), Duration.ofDays(7));
        assertThat(ttlAfterDuplicate).isLessThanOrEqualTo(ttlAfterFirstStore);
        assertThat(stringRedisTemplate.opsForValue().get(redisKey)).isEqualTo(firstTimestamp.toString());
    }
}
