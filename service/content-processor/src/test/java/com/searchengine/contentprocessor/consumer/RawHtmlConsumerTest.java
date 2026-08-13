package com.searchengine.contentprocessor.consumer;

import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import com.searchengine.contentprocessor.model.kafka.SearchDocument;
import com.searchengine.contentprocessor.service.ContentProcessorService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RawHtmlConsumerTest {

    @Mock
    private ContentProcessorService contentProcessorService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private RawHtmlConsumer rawHtmlConsumer;

    @Test
    void consume_validDocument_acknowledgesMessage() {
        RawHtmlDocument document = new RawHtmlDocument(
                1,
                "https://example.com",
                "https://example.com",
                "abc123hash",
                200,
                "text/html",
                "<html><body><h1>Test Page</h1></body></html>",
                Instant.now()
        );
        ConsumerRecord<String, RawHtmlDocument> record = new ConsumerRecord<>("raw-html-topic", 0, 0L, "key", document);

        SearchDocument searchDocument = new SearchDocument(
                "https://example.com",
                "https://example.com",
                "abc123hash",
                "Test Page",
                null,
                List.of("Test Page"),
                "Test Page",
                "en",
                2,
                200,
                "text/html",
                document.fetchedAt(),
                Instant.now()
        );

        when(contentProcessorService.process(document)).thenReturn(searchDocument);

        rawHtmlConsumer.consume(record, acknowledgment);

        verify(contentProcessorService, times(1)).process(document);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void consume_serviceThrowsException_doesNotAcknowledgeMessage() {
        RawHtmlDocument document = new RawHtmlDocument(
                1,
                "https://example.com",
                "https://example.com",
                "abc123hash",
                500,
                "text/html",
                "<html><body>Error</body></html>",
                Instant.now()
        );
        ConsumerRecord<String, RawHtmlDocument> record = new ConsumerRecord<>("raw-html-topic", 0, 0L, "key", document);

        doThrow(new RuntimeException("Service processing failure")).when(contentProcessorService).process(document);

        assertThrows(RuntimeException.class, () -> rawHtmlConsumer.consume(record, acknowledgment));

        verify(contentProcessorService, times(1)).process(document);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consume_nullRecordPayload_throwsExceptionAndDoesNotAcknowledge() {
        ConsumerRecord<String, RawHtmlDocument> record = new ConsumerRecord<>("raw-html-topic", 0, 0L, "key", null);

        assertThrows(IllegalArgumentException.class, () -> rawHtmlConsumer.consume(record, acknowledgment));

        verify(contentProcessorService, never()).process(any());
        verify(acknowledgment, never()).acknowledge();
    }
}
