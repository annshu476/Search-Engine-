package com.searchengine.contentprocessor.service;

import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ContentProcessorServiceTest {

    private final ContentProcessorService contentProcessorService = new ContentProcessorService();

    @Test
    void process_validDocument_executesWithoutException() {
        RawHtmlDocument document = new RawHtmlDocument(
                1,
                "https://example.com/test",
                "https://example.com/test",
                "hash123",
                200,
                "text/html; charset=utf-8",
                "<html><head><title>Test</title></head><body><p>Hello World</p></body></html>",
                Instant.now()
        );

        assertDoesNotThrow(() -> contentProcessorService.process(document));
    }

    @Test
    void process_nullDocument_handlesGracefully() {
        assertDoesNotThrow(() -> contentProcessorService.process(null));
    }
}
