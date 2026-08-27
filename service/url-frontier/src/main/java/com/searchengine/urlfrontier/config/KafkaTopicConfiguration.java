package com.searchengine.urlfrontier.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Kafka infrastructure configuration for URL frontier topics. */
@Configuration
public class KafkaTopicConfiguration {

    @Bean
    public NewTopic urlTaskTopic(UrlTaskPublisherProperties properties) {
        return TopicBuilder.name(properties.topic())
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic discoveredUrlsTopic(
            @Value("${application.kafka.discovered-urls-topic:discovered-urls-topic}") String topicName
    ) {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
