package com.searchengine.indexer.mapper;

import com.searchengine.indexer.model.kafka.SearchDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchDocumentMapperTest {

    private SearchDocumentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SearchDocumentMapper();
    }

    @Test
    void toMap_mapsAllFieldsCorrectly() {
        Instant fetched = Instant.parse("2026-08-13T10:00:00Z");
        Instant indexed = Instant.parse("2026-08-13T10:05:00Z");
        SearchDocument doc = new SearchDocument(
                "https://example.com/test",
                "https://example.com/test",
                "hash123",
                "Sample Title",
                "Sample Description",
                List.of("Heading 1", "Heading 2"),
                "Body paragraph text content",
                "en",
                150,
                200,
                "text/html",
                fetched,
                indexed
        );

        Map<String, Object> map = mapper.toMap(doc);

        assertThat(map).isNotNull();
        assertThat(map).containsEntry("url", "https://example.com/test");
        assertThat(map).containsEntry("canonicalUrl", "https://example.com/test");
        assertThat(map).containsEntry("urlHash", "hash123");
        assertThat(map).containsEntry("title", "Sample Title");
        assertThat(map).containsEntry("metaDescription", "Sample Description");
        assertThat(map).containsEntry("headings", List.of("Heading 1", "Heading 2"));
        assertThat(map).containsEntry("bodyText", "Body paragraph text content");
        assertThat(map).containsEntry("language", "en");
        assertThat(map).containsEntry("wordCount", 150);
        assertThat(map).containsEntry("statusCode", 200);
        assertThat(map).containsEntry("contentType", "text/html");
        assertThat(map).containsEntry("fetchedAt", "2026-08-13T10:00:00Z");
        assertThat(map).containsEntry("indexedAt", "2026-08-13T10:05:00Z");
    }

    @Test
    void toMap_handlesNullOptionalFields() {
        Instant now = Instant.now();
        SearchDocument doc = new SearchDocument(
                "https://example.com/test",
                "https://example.com/test",
                "hash123",
                null,
                null,
                List.of(),
                "Body text",
                null,
                10,
                200,
                "text/html",
                now,
                now
        );

        Map<String, Object> map = mapper.toMap(doc);

        assertThat(map).isNotNull();
        assertThat(map).containsEntry("title", null);
        assertThat(map).containsEntry("metaDescription", null);
        assertThat(map).containsEntry("language", null);
    }

    @Test
    void toMap_returnsNullWhenDocumentIsNull() {
        assertThat(mapper.toMap(null)).isNull();
    }
}
