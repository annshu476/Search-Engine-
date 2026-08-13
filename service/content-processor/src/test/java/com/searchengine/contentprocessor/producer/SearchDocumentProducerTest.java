package com.searchengine.contentprocessor.producer;

import com.searchengine.contentprocessor.exception.SearchDocumentPublishException;
import com.searchengine.contentprocessor.model.kafka.SearchDocument;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchDocumentProducerTest {

    @Mock
    private KafkaTemplate<String, SearchDocument> kafkaTemplate;

    private SearchDocumentProducer producer;

    private static final String TOPIC_NAME = "search-document-topic";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @BeforeEach
    void setUp() {
        producer = new SearchDocumentProducer(kafkaTemplate, TOPIC_NAME, TIMEOUT);
    }

    private SearchDocument createSampleSearchDocument(String urlHash) {
        return new SearchDocument(
                "https://example.com/test",
                "https://example.com/test",
                urlHash,
                "Test Title",
                "Test Description",
                List.of("Heading 1"),
                "Body content",
                "en",
                2,
                200,
                "text/html",
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void send_validDocument_publishesToKafkaUsingUrlHashAsKey() {
        SearchDocument doc = createSampleSearchDocument("hash123");
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.topic()).thenReturn(TOPIC_NAME);
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(0L);

        @SuppressWarnings("unchecked")
        SendResult<String, SearchDocument> sendResult = mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);

        CompletableFuture<SendResult<String, SearchDocument>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(TOPIC_NAME, "hash123", doc)).thenReturn(future);

        assertDoesNotThrow(() -> producer.send(doc));

        verify(kafkaTemplate, times(1)).send(TOPIC_NAME, "hash123", doc);
    }

    @Test
    void send_brokerThrowsExecutionException_convertsToSearchDocumentPublishException() {
        SearchDocument doc = createSampleSearchDocument("hashError");
        CompletableFuture<SendResult<String, SearchDocument>> future = new CompletableFuture<>();
        future.completeExceptionally(new ExecutionException("Kafka broker down", new RuntimeException("Connection refused")));

        when(kafkaTemplate.send(TOPIC_NAME, "hashError", doc)).thenReturn(future);

        SearchDocumentPublishException ex = assertThrows(
                SearchDocumentPublishException.class,
                () -> producer.send(doc)
        );

        assertThat(ex.getUrlHash()).isEqualTo("hashError");
        assertThat(ex.getTopic()).isEqualTo(TOPIC_NAME);
        verify(kafkaTemplate, times(1)).send(TOPIC_NAME, "hashError", doc);
    }

    @Test
    void send_timeout_convertsToSearchDocumentPublishException() {
        SearchDocument doc = createSampleSearchDocument("hashTimeout");
        CompletableFuture<SendResult<String, SearchDocument>> uncompletedFuture = new CompletableFuture<>();

        SearchDocumentProducer shortTimeoutProducer = new SearchDocumentProducer(kafkaTemplate, TOPIC_NAME, Duration.ofMillis(50));
        when(kafkaTemplate.send(TOPIC_NAME, "hashTimeout", doc)).thenReturn(uncompletedFuture);

        SearchDocumentPublishException ex = assertThrows(
                SearchDocumentPublishException.class,
                () -> shortTimeoutProducer.send(doc)
        );

        assertThat(ex.getUrlHash()).isEqualTo("hashTimeout");
        assertThat(ex.getTopic()).isEqualTo(TOPIC_NAME);
        assertThat(ex.getMessage()).contains("timed out");
    }

    @Test
    void send_nullDocument_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> producer.send(null));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void send_nullOrBlankUrlHash_throwsIllegalArgumentException() {
        SearchDocument docNullHash = createSampleSearchDocument(null);
        assertThrows(IllegalArgumentException.class, () -> producer.send(docNullHash));

        SearchDocument docBlankHash = createSampleSearchDocument("   ");
        assertThrows(IllegalArgumentException.class, () -> producer.send(docBlankHash));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
