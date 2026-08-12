package com.searchengine.urlfrontier.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class KafkaTopicConfigurationTest {

    @Autowired
    private NewTopic urlTaskTopic;

    @Autowired
    private UrlTaskPublisherProperties properties;

    @Autowired
    private Environment environment;

    @Test
    void exposesUrlTopicConfigurationAndBoundedRetries() {
        assertThat(urlTaskTopic.name()).isEqualTo("url-topic");
        assertThat(urlTaskTopic.numPartitions()).isEqualTo(1);
        assertThat(urlTaskTopic.replicationFactor()).isEqualTo((short) 1);
        assertThat(properties.topic()).isEqualTo("url-topic");
        assertThat(properties.publishTimeout()).isEqualTo(java.time.Duration.ofSeconds(3));
        assertThat(environment.getProperty("spring.kafka.producer.retries", Integer.class)).isEqualTo(3);
    }
}
