package com.searchengine.contentprocessor.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuration bean declaring the discovered-urls-topic output Kafka topic.
 */
@Configuration
public class DiscoveredUrlTopicConfig {

    @Value("${content-processor.kafka.discovered-urls-topic:discovered-urls-topic}")
    private String topicName;

    @Bean
    public NewTopic discoveredUrlsTopic() {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
