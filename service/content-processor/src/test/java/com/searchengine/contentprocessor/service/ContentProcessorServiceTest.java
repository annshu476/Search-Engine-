package com.searchengine.contentprocessor.service;

import com.searchengine.contentprocessor.exception.SearchDocumentPublishException;
import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import com.searchengine.contentprocessor.model.kafka.SearchDocument;
import com.searchengine.contentprocessor.parser.HtmlDocumentParser;
import com.searchengine.contentprocessor.producer.DiscoveredUrlProducer;
import com.searchengine.contentprocessor.producer.SearchDocumentProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentProcessorServiceTest {

    @Mock
    private HtmlDocumentParser htmlDocumentParser;

    @Mock
    private SearchDocumentProducer searchDocumentProducer;

    @Mock
    private DiscoveredUrlProducer discoveredUrlProducer;

    @InjectMocks
    private ContentProcessorService contentProcessorService;

    @Test
    void process_validDocument_callsParserAndProducerThenReturnsSearchDocument() {
        RawHtmlDocument rawDoc = new RawHtmlDocument(
                1,
                "https://example.com/test",
                "https://example.com/test",
                "hash123",
                200,
                "text/html",
                "<html><head><title>Test Title</title></head><body><p>Hello World</p></body></html>",
                Instant.now()
        );

        SearchDocument expectedSearchDoc = new SearchDocument(
                "https://example.com/test",
                "https://example.com/test",
                "hash123",
                "Test Title",
                null,
                List.of(),
                "Hello World",
                "en",
                2,
                200,
                "text/html",
                rawDoc.fetchedAt(),
                Instant.now()
        );

        when(htmlDocumentParser.parse(rawDoc)).thenReturn(expectedSearchDoc);
        doNothing().when(searchDocumentProducer).send(expectedSearchDoc);

        SearchDocument result = contentProcessorService.process(rawDoc);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedSearchDoc);
        verify(htmlDocumentParser, times(1)).parse(rawDoc);
        verify(searchDocumentProducer, times(1)).send(expectedSearchDoc);
    }

    @Test
    void process_nullDocument_returnsNullWithoutCallingParserOrProducer() {
        SearchDocument result = contentProcessorService.process(null);

        assertThat(result).isNull();
        verify(htmlDocumentParser, never()).parse(any());
        verify(searchDocumentProducer, never()).send(any());
    }

    @Test
    void process_parserThrowsException_doesNotCallProducer() {
        RawHtmlDocument rawDoc = new RawHtmlDocument(
                1,
                "https://example.com/error",
                "https://example.com/error",
                "hashError",
                500,
                "text/html",
                "<html><body>Error</body></html>",
                Instant.now()
        );

        when(htmlDocumentParser.parse(rawDoc)).thenThrow(new RuntimeException("Parsing failure"));

        assertThrows(RuntimeException.class, () -> contentProcessorService.process(rawDoc));
        verify(htmlDocumentParser, times(1)).parse(rawDoc);
        verify(searchDocumentProducer, never()).send(any());
    }

    @Test
    void process_producerThrowsException_propagatesException() {
        RawHtmlDocument rawDoc = new RawHtmlDocument(
                1,
                "https://example.com/test",
                "https://example.com/test",
                "hashPublishError",
                200,
                "text/html",
                "<html><body>Hello</body></html>",
                Instant.now()
        );

        SearchDocument parsedDoc = new SearchDocument(
                "https://example.com/test",
                "https://example.com/test",
                "hashPublishError",
                "Title",
                null,
                List.of(),
                "Hello",
                "en",
                1,
                200,
                "text/html",
                rawDoc.fetchedAt(),
                Instant.now()
        );

        when(htmlDocumentParser.parse(rawDoc)).thenReturn(parsedDoc);
        doThrow(new SearchDocumentPublishException("hashPublishError", "search-document-topic", "Broker timeout"))
                .when(searchDocumentProducer).send(parsedDoc);

        assertThrows(SearchDocumentPublishException.class, () -> contentProcessorService.process(rawDoc));

        verify(htmlDocumentParser, times(1)).parse(rawDoc);
        verify(searchDocumentProducer, times(1)).send(parsedDoc);
    }
}
