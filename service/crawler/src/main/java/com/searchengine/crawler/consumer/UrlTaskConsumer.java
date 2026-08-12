package com.searchengine.crawler.consumer;

import com.searchengine.crawler.exception.NonRetryableCrawlerException;
import com.searchengine.crawler.model.kafka.UrlTask;
import com.searchengine.crawler.service.CrawlerService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer with native Spring Kafka non-blocking retry topics and DLT routing.
 */
@Component
public class UrlTaskConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(UrlTaskConsumer.class);

    private final CrawlerService crawlerService;

    public UrlTaskConsumer(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @KafkaListener(
            topics = "${application.kafka.url-task.topic:url-topic}",
            groupId = "${spring.kafka.consumer.group-id:crawler-service}",
            concurrency = "1"
    )
    public void consume(ConsumerRecord<String, UrlTask> record, Acknowledgment ack) {
        if (record == null || record.value() == null) {
            LOGGER.error("URL_TASK_INVALID reason=\"Null payload received\"");
            throw new NonRetryableCrawlerException("unknown", "Null payload received", 0);
        }

        UrlTask task = record.value();

        if (task.schemaVersion() != UrlTask.SUPPORTED_SCHEMA_VERSION) {
            LOGGER.error("URL_TASK_UNSUPPORTED_SCHEMA schemaVersion={} expected={} urlHash={} topic={} partition={} offset={}",
                    task.schemaVersion(), UrlTask.SUPPORTED_SCHEMA_VERSION, task.urlHash(),
                    record.topic(), record.partition(), record.offset());
            throw new NonRetryableCrawlerException(task.url() != null ? task.url() : "unknown",
                    "Unsupported schema version: " + task.schemaVersion(), 0);
        }

        String validationError = validate(task);
        if (validationError != null) {
            LOGGER.error("URL_TASK_INVALID reason=\"{}\" urlHash={} priority={} topic={} partition={} offset={}",
                    validationError, task.urlHash(), task.priority(),
                    record.topic(), record.partition(), record.offset());
            throw new NonRetryableCrawlerException(task.url() != null ? task.url() : "unknown",
                    "Validation error: " + validationError, 0);
        }

        crawlerService.processUrlTask(task);
        LOGGER.info("URL_TASK_PROCESSED urlHash={} priority={} topic={} partition={} offset={}",
                task.urlHash(), task.priority(), record.topic(), record.partition(), record.offset());

        if (ack != null) {
            ack.acknowledge();
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, UrlTask> record,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, defaultValue = "unknown") String originalTopic,
            @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, defaultValue = "Unknown error") String exceptionMessage
    ) {
        UrlTask task = record != null ? record.value() : null;
        String urlHash = task != null ? task.urlHash() : "unknown";
        String url = task != null ? task.url() : "unknown";

        LOGGER.error("CRAWL_DLT urlHash={} url=\"{}\" originalTopic={} partition={} offset={} failureReason=\"{}\"",
                urlHash, url, originalTopic, record != null ? record.partition() : -1, record != null ? record.offset() : -1, exceptionMessage);
    }

    private String validate(UrlTask task) {
        if (task.url() == null || task.url().isBlank()) {
            return "URL must not be null or blank";
        }
        if (task.urlHash() == null || task.urlHash().isBlank()) {
            return "URL hash must not be null or blank";
        }
        if (task.priority() < 1 || task.priority() > 10) {
            return "Priority must be between 1 and 10";
        }
        if (task.discoveredAt() == null) {
            return "DiscoveredAt timestamp must not be null";
        }
        return null;
    }
}
