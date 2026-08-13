package com.searchengine.contentprocessor.producer;

import com.searchengine.contentprocessor.exception.SearchDocumentPublishException;
import com.searchengine.contentprocessor.model.kafka.SearchDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Producer component responsible for publishing SearchDocument records to search-document-topic.
 */
@Component
public class SearchDocumentProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchDocumentProducer.class);

    private final KafkaTemplate<String, SearchDocument> kafkaTemplate;
    private final String topicName;
    private final Duration publishTimeout;

    public SearchDocumentProducer(
            KafkaTemplate<String, SearchDocument> kafkaTemplate,
            @Value("${content-processor.kafka.search-document-topic:search-document-topic}") String topicName,
            @Value("${content-processor.kafka.publish-timeout:3s}") Duration publishTimeout
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
        this.publishTimeout = publishTimeout;
    }

    public void send(SearchDocument document) {
        if (document == null) {
            LOGGER.error("SEARCH_DOCUMENT_PUBLISH_FAILED reason=\"Null SearchDocument payload\"");
            throw new IllegalArgumentException("SearchDocument payload must not be null");
        }

        if (document.urlHash() == null || document.urlHash().isBlank()) {
            LOGGER.error("SEARCH_DOCUMENT_PUBLISH_FAILED reason=\"Null or blank urlHash\"");
            throw new IllegalArgumentException("SearchDocument urlHash must not be null or blank");
        }

        LOGGER.info("SEARCH_DOCUMENT_PUBLISHING urlHash={} topic={}", document.urlHash(), topicName);

        try {
            CompletableFuture<SendResult<String, SearchDocument>> future =
                    kafkaTemplate.send(topicName, document.urlHash(), document);

            SendResult<String, SearchDocument> result = future.get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);

            if (result != null && result.getRecordMetadata() != null) {
                LOGGER.info("SEARCH_DOCUMENT_PUBLISHED urlHash={} topic={} partition={} offset={}",
                        document.urlHash(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                LOGGER.info("SEARCH_DOCUMENT_PUBLISHED urlHash={} topic={}", document.urlHash(), topicName);
            }
        } catch (TimeoutException e) {
            LOGGER.error("SEARCH_DOCUMENT_PUBLISH_TIMEOUT urlHash={} topic={} timeoutMs={}",
                    document.urlHash(), topicName, publishTimeout.toMillis());
            throw new SearchDocumentPublishException(
                    document.urlHash(),
                    topicName,
                    "Publishing SearchDocument timed out after " + publishTimeout.toMillis() + "ms",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("SEARCH_DOCUMENT_PUBLISH_INTERRUPTED urlHash={} topic={}", document.urlHash(), topicName);
            throw new SearchDocumentPublishException(
                    document.urlHash(),
                    topicName,
                    "Publishing SearchDocument was interrupted",
                    e
            );
        } catch (Exception e) {
            LOGGER.error("SEARCH_DOCUMENT_PUBLISH_FAILED urlHash={} topic={} error=\"{}\"",
                    document.urlHash(), topicName, e.getMessage(), e);
            throw new SearchDocumentPublishException(
                    document.urlHash(),
                    topicName,
                    "Failed to publish SearchDocument to topic " + topicName + ": " + e.getMessage(),
                    e
            );
        }
    }

    public String getTopicName() {
        return topicName;
    }

    public Duration getPublishTimeout() {
        return publishTimeout;
    }
}
