package com.searchengine.urlfrontier.consumer;

import com.searchengine.urlfrontier.model.dto.SubmitUrlRequest;
import com.searchengine.urlfrontier.model.dto.SubmitUrlResponse;
import com.searchengine.urlfrontier.model.kafka.DiscoveredUrl;
import com.searchengine.urlfrontier.service.UrlFrontierService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer listening to discovered-urls-topic and feeding discovered URLs into UrlFrontierService.
 */
@Component
public class DiscoveredUrlConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveredUrlConsumer.class);

    private final UrlFrontierService urlFrontierService;

    public DiscoveredUrlConsumer(UrlFrontierService urlFrontierService) {
        this.urlFrontierService = urlFrontierService;
    }

    @KafkaListener(
            topics = "${application.kafka.discovered-urls-topic:discovered-urls-topic}",
            groupId = "${spring.kafka.consumer.group-id:url-frontier-group}",
            containerFactory = "discoveredUrlKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, DiscoveredUrl> record, Acknowledgment ack) {
        if (record == null || record.value() == null) {
            LOGGER.error("DISCOVERED_URL_CONSUME_SKIPPED reason=\"Null payload received\"");
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }

        DiscoveredUrl discovered = record.value();

        if (discovered.url() == null || discovered.url().isBlank()) {
            LOGGER.error("DISCOVERED_URL_CONSUME_SKIPPED reason=\"Blank URL\" sourceUrl=\"{}\"", discovered.sourceUrl());
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }

        LOGGER.info("DISCOVERED_URL_CONSUMED url=\"{}\" sourceUrl=\"{}\" topic={} partition={} offset={}",
                discovered.url(), discovered.sourceUrl(), record.topic(), record.partition(), record.offset());

        try {
            SubmitUrlResponse response = urlFrontierService.submit(new SubmitUrlRequest(discovered.url()));
            if (response.accepted()) {
                LOGGER.info("URL_FRONTIER_DISCOVERED_URL accepted=true urlHash={} url=\"{}\" sourceUrl=\"{}\"",
                        response.urlHash(), response.normalizedUrl(), discovered.sourceUrl());
            } else {
                LOGGER.info("URL_FRONTIER_DUPLICATE_URL accepted=false urlHash={} url=\"{}\" sourceUrl=\"{}\"",
                        response.urlHash(), response.normalizedUrl(), discovered.sourceUrl());
            }
        } catch (Exception e) {
            LOGGER.error("DISCOVERED_URL_PROCESSING_ERROR url=\"{}\" sourceUrl=\"{}\" error=\"{}\"",
                    discovered.url(), discovered.sourceUrl(), e.getMessage(), e);
        }

        if (ack != null) {
            ack.acknowledge();
        }
    }
}
