package com.searchengine.crawler.consumer;

import com.searchengine.crawler.model.kafka.UrlTask;
import com.searchengine.crawler.service.CrawlerService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka consumer receiving UrlTask messages from url-topic.
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
            return;
        }

        UrlTask task = record.value();

        if (task.schemaVersion() != UrlTask.SUPPORTED_SCHEMA_VERSION) {
            LOGGER.error("URL_TASK_UNSUPPORTED_SCHEMA schemaVersion={} expected={} urlHash={} topic={} partition={} offset={}",
                    task.schemaVersion(), UrlTask.SUPPORTED_SCHEMA_VERSION, task.urlHash(),
                    record.topic(), record.partition(), record.offset());
            return;
        }

        String validationError = validate(task);
        if (validationError != null) {
            LOGGER.error("URL_TASK_INVALID reason=\"{}\" urlHash={} priority={} topic={} partition={} offset={}",
                    validationError, task.urlHash(), task.priority(),
                    record.topic(), record.partition(), record.offset());
            return;
        }

        try {
            crawlerService.processUrlTask(task);
            LOGGER.info("URL_TASK_RECEIVED urlHash={} priority={} topic={} partition={} offset={}",
                    task.urlHash(), task.priority(), record.topic(), record.partition(), record.offset());
            ack.acknowledge();
        } catch (Exception ex) {
            LOGGER.error("URL_TASK_PROCESSING_FAILED urlHash={} topic={} partition={} offset={}",
                    task.urlHash(), record.topic(), record.partition(), record.offset(), ex);
        }
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
