package com.searchengine.indexer.health;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchHealthIndicator implements HealthIndicator {

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public Health health() {
        try {
            boolean pingSuccess = elasticsearchClient.ping().value();
            if (pingSuccess) {
                return Health.up()
                        .withDetail("elasticsearch", "Available")
                        .build();
            } else {
                return Health.down()
                        .withDetail("elasticsearch", "Ping returned false")
                        .build();
            }
        } catch (Exception e) {
            log.debug("Elasticsearch health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("elasticsearch", "Unavailable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
