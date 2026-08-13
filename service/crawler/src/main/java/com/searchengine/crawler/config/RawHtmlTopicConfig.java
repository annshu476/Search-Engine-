package com.searchengine.crawler.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuration bean for creating the raw-html-topic Kafka topic.
 */
@Configuration
public class RawHtmlTopicConfig {

    @Value("${crawler.raw-html.topic:raw-html-topic}")
    private String topicName;

    @Bean
    public NewTopic rawHtmlTopic() {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
