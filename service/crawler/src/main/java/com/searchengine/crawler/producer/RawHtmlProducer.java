package com.searchengine.crawler.producer;

import com.searchengine.crawler.exception.RawHtmlPublishException;
import com.searchengine.crawler.model.kafka.RawHtmlDocument;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Producer for publishing RawHtmlDocument messages to Kafka raw-html-topic.
 */
@Component
public class RawHtmlProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RawHtmlProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;
    private final Duration publishTimeout;

    public RawHtmlProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${crawler.raw-html.topic:raw-html-topic}") String topic,
            @Value("${crawler.raw-html.publish-timeout:3s}") Duration publishTimeout
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeout = publishTimeout;
    }

    public void publish(RawHtmlDocument document) {
        if (document == null || document.urlHash() == null) {
            throw new RawHtmlPublishException(
                    document != null ? document.url() : "unknown",
                    "Invalid RawHtmlDocument: document or urlHash is null"
            );
        }

        LOGGER.info("RAW_HTML_PUBLISHING urlHash={} topic={}", document.urlHash(), topic);

        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, document.urlHash(), document);
            SendResult<String, Object> sendResult = future.get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);

            int partition = sendResult.getRecordMetadata().partition();
            long offset = sendResult.getRecordMetadata().offset();

            LOGGER.info("RAW_HTML_PUBLISHED urlHash={} topic={} partition={} offset={}",
                    document.urlHash(), topic, partition, offset);
        } catch (Exception e) {
            String failureReason = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            LOGGER.error("RAW_HTML_PUBLISH_FAILED urlHash={} topic={} failureReason=\"{}\"",
                    document.urlHash(), topic, failureReason, e);
            throw new RawHtmlPublishException(document.url(), "Kafka publish failed: " + failureReason, e);
        }
    }
}
