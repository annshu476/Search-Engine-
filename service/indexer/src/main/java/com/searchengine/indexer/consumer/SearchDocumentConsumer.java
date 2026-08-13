package com.searchengine.indexer.consumer;

import com.searchengine.indexer.exception.SearchDocumentValidationException;
import com.searchengine.indexer.model.kafka.SearchDocument;
import com.searchengine.indexer.service.IndexerService;
import com.searchengine.indexer.validator.SearchDocumentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchDocumentConsumer {

    private final SearchDocumentValidator validator;
    private final IndexerService indexerService;

    @KafkaListener(
            topics = "${indexer.kafka.search-document-topic:search-document-topic}",
            groupId = "${spring.kafka.consumer.group-id:indexer}"
    )
    public void consume(ConsumerRecord<String, SearchDocument> record, Acknowledgment acknowledgment) {
        SearchDocument document = record.value();

        if (document == null) {
            log.error("SEARCH_DOCUMENT_INVALID reason=Null payload received topic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        log.info("SEARCH_DOCUMENT_RECEIVED urlHash={} url={} topic={} partition={} offset={}",
                document.urlHash(), document.url(), record.topic(), record.partition(), record.offset());

        try {
            validator.validate(document);
        } catch (SearchDocumentValidationException e) {
            log.error("SEARCH_DOCUMENT_INVALID reason={} urlHash={} topic={} partition={} offset={}",
                    e.getMessage(), document.urlHash(), record.topic(), record.partition(), record.offset());
            // Do NOT call acknowledgment.acknowledge() for invalid documents
            return;
        }

        try {
            indexerService.process(document);
            log.info("SEARCH_DOCUMENT_PROCESSED urlHash={} topic={} partition={} offset={}",
                    document.urlHash(), record.topic(), record.partition(), record.offset());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("SEARCH_DOCUMENT_PROCESSING_FAILED urlHash={} topic={} partition={} offset={} error={}",
                    document.urlHash(), record.topic(), record.partition(), record.offset(), e.getMessage(), e);
            throw e;
        }
    }
}
