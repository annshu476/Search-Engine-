package com.searchengine.indexer.consumer;

import com.searchengine.indexer.model.kafka.SearchDocument;
import com.searchengine.indexer.service.IndexerService;
import com.searchengine.indexer.validator.SearchDocumentValidator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchDocumentConsumerTest {

    @Mock
    private IndexerService indexerService;

    @Mock
    private Acknowledgment acknowledgment;

    private SearchDocumentValidator validator;
    private SearchDocumentConsumer consumer;

    @BeforeEach
    void setUp() {
        validator = new SearchDocumentValidator();
        consumer = new SearchDocumentConsumer(validator, indexerService);
    }

    private SearchDocument createValidDocument() {
        Instant now = Instant.now();
        return new SearchDocument(
                "https://example.com/test",
                "https://example.com/test",
                "hash123",
                "Title",
                "Description",
                List.of("H1"),
                "Body content",
                "en",
                2,
                200,
                "text/html",
                now,
                now
        );
    }

    @Test
    void consume_validDocument_delegatesToServiceAndAcknowledges() {
        SearchDocument doc = createValidDocument();
        ConsumerRecord<String, SearchDocument> record = new ConsumerRecord<>("search-document-topic", 0, 10L, doc.urlHash(), doc);

        consumer.consume(record, acknowledgment);

        verify(indexerService, times(1)).process(doc);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void consume_nullPayload_doesNotCallServiceOrAcknowledge() {
        ConsumerRecord<String, SearchDocument> record = new ConsumerRecord<>("search-document-topic", 0, 10L, "key", null);

        consumer.consume(record, acknowledgment);

        verify(indexerService, never()).process(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consume_validationFailure_doesNotCallServiceOrAcknowledge() {
        Instant now = Instant.now();
        // Invalid document: url is blank
        SearchDocument invalidDoc = new SearchDocument(
                "   ",
                "https://example.com/test",
                "hash123",
                "Title",
                "Description",
                List.of("H1"),
                "Body content",
                "en",
                2,
                200,
                "text/html",
                now,
                now
        );
        ConsumerRecord<String, SearchDocument> record = new ConsumerRecord<>("search-document-topic", 0, 10L, invalidDoc.urlHash(), invalidDoc);

        consumer.consume(record, acknowledgment);

        verify(indexerService, never()).process(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consume_processingException_doesNotAcknowledge() {
        SearchDocument doc = createValidDocument();
        ConsumerRecord<String, SearchDocument> record = new ConsumerRecord<>("search-document-topic", 0, 10L, doc.urlHash(), doc);

        doThrow(new RuntimeException("Service failure")).when(indexerService).process(doc);

        assertThrows(RuntimeException.class, () -> consumer.consume(record, acknowledgment));

        verify(indexerService, times(1)).process(doc);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consume_optionalFieldsNull_delegatesAndAcknowledges() {
        Instant now = Instant.now();
        // title, metaDescription, language are null
        SearchDocument docWithNullOptionals = new SearchDocument(
                "https://example.com/test",
                "https://example.com/test",
                "hash123",
                null,
                null,
                List.of("H1"),
                "Body text",
                null,
                2,
                200,
                "text/html",
                now,
                now
        );
        ConsumerRecord<String, SearchDocument> record = new ConsumerRecord<>("search-document-topic", 0, 10L, docWithNullOptionals.urlHash(), docWithNullOptionals);

        consumer.consume(record, acknowledgment);

        verify(indexerService, times(1)).process(docWithNullOptionals);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void consume_invalidWordCount_doesNotCallServiceOrAcknowledge() {
        Instant now = Instant.now();
        // Negative wordCount
        SearchDocument doc = new SearchDocument(
                "https://example.com/test",
                "https://example.com/test",
                "hash123",
                "Title",
                "Desc",
                List.of(),
                "Text",
                "en",
                -5,
                200,
                "text/html",
                now,
                now
        );
        ConsumerRecord<String, SearchDocument> record = new ConsumerRecord<>("search-document-topic", 0, 10L, doc.urlHash(), doc);

        consumer.consume(record, acknowledgment);

        verify(indexerService, never()).process(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consume_invalidStatusCode_doesNotCallServiceOrAcknowledge() {
        Instant now = Instant.now();
        // StatusCode > 599
        SearchDocument doc = new SearchDocument(
                "https://example.com/test",
                "https://example.com/test",
                "hash123",
                "Title",
                "Desc",
                List.of(),
                "Text",
                "en",
                10,
                650,
                "text/html",
                now,
                now
        );
        ConsumerRecord<String, SearchDocument> record = new ConsumerRecord<>("search-document-topic", 0, 10L, doc.urlHash(), doc);

        consumer.consume(record, acknowledgment);

        verify(indexerService, never()).process(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consume_missingRequiredFields_rejected() {
        Instant now = Instant.now();
        // Null canonicalUrl
        SearchDocument doc = new SearchDocument(
                "https://example.com/test",
                null,
                "hash123",
                "Title",
                "Desc",
                List.of(),
                "Text",
                "en",
                10,
                200,
                "text/html",
                now,
                now
        );
        ConsumerRecord<String, SearchDocument> record = new ConsumerRecord<>("search-document-topic", 0, 10L, doc.urlHash(), doc);

        consumer.consume(record, acknowledgment);

        verify(indexerService, never()).process(any());
        verify(acknowledgment, never()).acknowledge();
    }
}
