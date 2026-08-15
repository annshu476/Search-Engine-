package com.searchengine.indexer.health;

import com.searchengine.indexer.config.SearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final SearchProperties searchProperties;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Override
    public Health health() {
        SearchProperties.Redis redisProps = searchProperties.getRedis();

        if (!redisProps.isEnabled()) {
            return Health.up().withDetail("redis", "disabled").build();
        }

        if (redisConnectionFactory == null) {
            return Health.down().withDetail("redis", "Connection factory null").build();
        }

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pingResponse = connection.ping();
            if ("PONG".equalsIgnoreCase(pingResponse)) {
                return Health.up()
                        .withDetail("host", redisProps.getHost())
                        .withDetail("port", redisProps.getPort())
                        .build();
            } else {
                return Health.down().withDetail("reason", "Unexpected PING response: " + pingResponse).build();
            }
        } catch (Exception e) {
            log.warn("REDIS_CONNECTION_FAILED health check ping failed reason=\"{}\"", e.getMessage());
            if (redisProps.isFailOpen()) {
                return Health.up()
                        .withDetail("redis", "DEGRADED")
                        .withDetail("reason", "Redis connection failed, fail-open active")
                        .build();
            } else {
                return Health.down().withDetail("reason", e.getMessage()).build();
            }
        }
    }
}
