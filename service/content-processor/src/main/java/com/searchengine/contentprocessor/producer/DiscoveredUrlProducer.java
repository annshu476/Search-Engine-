package com.searchengine.contentprocessor.producer;

import com.searchengine.contentprocessor.model.kafka.DiscoveredUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Producer component responsible for publishing DiscoveredUrl records to discovered-urls-topic.
 */
@Component
public class DiscoveredUrlProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveredUrlProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicName;
    private final Duration publishTimeout;

    public DiscoveredUrlProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${content-processor.kafka.discovered-urls-topic:discovered-urls-topic}") String topicName,
            @Value("${content-processor.kafka.publish-timeout:3s}") Duration publishTimeout
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
        this.publishTimeout = publishTimeout;
    }

    public void send(DiscoveredUrl discoveredUrl) {
        if (discoveredUrl == null || discoveredUrl.url() == null || discoveredUrl.url().isBlank()) {
            LOGGER.warn("DISCOVERED_URL_PUBLISH_SKIPPED reason=\"Null or blank discovered URL payload\"");
            return;
        }

        LOGGER.info("DISCOVERED_URL_PUBLISHING url=\"{}\" sourceUrl=\"{}\" topic={}",
                discoveredUrl.url(), discoveredUrl.sourceUrl(), topicName);

        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topicName, discoveredUrl.url(), discoveredUrl);

            SendResult<String, Object> result = future.get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);

            if (result != null && result.getRecordMetadata() != null) {
                LOGGER.info("DISCOVERED_URL_PUBLISHED url=\"{}\" sourceUrl=\"{}\" topic={} partition={} offset={}",
                        discoveredUrl.url(),
                        discoveredUrl.sourceUrl(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        } catch (Exception e) {
            // Failure to publish a discovered URL must NOT crash or corrupt the SearchDocument pipeline
            LOGGER.error("DISCOVERED_URL_PUBLISH_FAILED url=\"{}\" sourceUrl=\"{}\" topic={} error=\"{}\"",
                    discoveredUrl.url(), discoveredUrl.sourceUrl(), topicName, e.getMessage());
        }
    }

    public String getTopicName() {
        return topicName;
    }
}
