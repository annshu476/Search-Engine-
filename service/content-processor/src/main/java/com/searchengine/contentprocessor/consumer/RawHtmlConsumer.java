package com.searchengine.contentprocessor.consumer;

import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import com.searchengine.contentprocessor.service.ContentProcessorService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer listener for receiving raw HTML documents from raw-html-topic.
 */
@Component
public class RawHtmlConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RawHtmlConsumer.class);

    private final ContentProcessorService contentProcessorService;

    public RawHtmlConsumer(ContentProcessorService contentProcessorService) {
        this.contentProcessorService = contentProcessorService;
    }

    @KafkaListener(
            topics = "${content-processor.kafka.raw-html-topic:raw-html-topic}",
            groupId = "${spring.kafka.consumer.group-id:content-processor}",
            concurrency = "1"
    )
    public void consume(ConsumerRecord<String, RawHtmlDocument> record, Acknowledgment ack) {
        if (record == null || record.value() == null) {
            LOGGER.error("RAW_HTML_DOCUMENT_INVALID reason=\"Null payload received\"");
            throw new IllegalArgumentException("Null payload received");
        }

        RawHtmlDocument document = record.value();
        LOGGER.info("RAW_HTML_CONSUMED urlHash={} topic={} partition={} offset={}",
                document.urlHash(), record.topic(), record.partition(), record.offset());

        contentProcessorService.process(document);

        if (ack != null) {
            ack.acknowledge();
        }
    }
}
