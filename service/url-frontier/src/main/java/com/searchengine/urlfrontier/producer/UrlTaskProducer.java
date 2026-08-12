package com.searchengine.urlfrontier.producer;

import com.searchengine.urlfrontier.config.UrlTaskPublisherProperties;
import com.searchengine.urlfrontier.exception.KafkaPublishException;
import com.searchengine.urlfrontier.model.kafka.UrlTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/** Publishes accepted URL crawl tasks to Kafka. */
@Component
public class UrlTaskProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlTaskProducer.class);

    private final KafkaTemplate<String, UrlTask> kafkaTemplate;
    private final UrlTaskPublisherProperties properties;

    public UrlTaskProducer(KafkaTemplate<String, UrlTask> kafkaTemplate, UrlTaskPublisherProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publish(UrlTask task) {
        try {
            SendResult<String, UrlTask> result = kafkaTemplate
                    .send(properties.topic(), task.urlHash(), task)
                    .get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
            LOGGER.info("KAFKA_PUBLISH_SUCCESS urlHash={} topic={} partition={} offset={}",
                    task.urlHash(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure(task);
            throw new KafkaPublishException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            logFailure(task);
            throw new KafkaPublishException(exception);
        }
    }

    private void logFailure(UrlTask task) {
        LOGGER.error("KAFKA_PUBLISH_FAILURE urlHash={} topic={}", task.urlHash(), properties.topic());
    }
}
