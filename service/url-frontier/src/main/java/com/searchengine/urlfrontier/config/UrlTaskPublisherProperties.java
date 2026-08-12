package com.searchengine.urlfrontier.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized Kafka publishing settings for accepted URL tasks. */
@ConfigurationProperties(prefix = "application.kafka.url-task")
public record UrlTaskPublisherProperties(String topic, Duration publishTimeout) {
}
