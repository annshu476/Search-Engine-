package com.searchengine.crawler.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.searchengine.crawler.exception.RawHtmlPublishException;
import com.searchengine.crawler.model.kafka.RawHtmlDocument;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class RawHtmlProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private RawHtmlProducer rawHtmlProducer;

    private static final String TOPIC = "raw-html-topic";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final String HASH = "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28";

    @BeforeEach
    void setUp() {
        rawHtmlProducer = new RawHtmlProducer(kafkaTemplate, TOPIC, TIMEOUT);
    }

    @Test
    void publishesCorrectRawHtmlDocumentWithUrlHashKey() {
        RawHtmlDocument doc = new RawHtmlDocument(
                1, "https://spring.io", "https://spring.io", HASH, 200, "text/html", "<html>Content</html>", Instant.now()
        );

        RecordMetadata metadata = new RecordMetadata(new TopicPartition(TOPIC, 0), 0L, 0, 0L, 0, 0);
        SendResult<String, Object> sendResult = new SendResult<>(null, metadata);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(eq(TOPIC), eq(HASH), eq(doc))).thenReturn(future);

        rawHtmlProducer.publish(doc);

        verify(kafkaTemplate).send(TOPIC, HASH, doc);
    }

    @Test
    void convertsKafkaFailureIntoRawHtmlPublishException() {
        RawHtmlDocument doc = new RawHtmlDocument(
                1, "https://spring.io", "https://spring.io", HASH, 200, "text/html", "<html>Content</html>", Instant.now()
        );

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Broker unreachable"));

        when(kafkaTemplate.send(eq(TOPIC), eq(HASH), eq(doc))).thenReturn(future);

        assertThatThrownBy(() -> rawHtmlProducer.publish(doc))
                .isInstanceOf(RawHtmlPublishException.class)
                .hasMessageContaining("Kafka publish failed");
    }

    @Test
    void respectsPublishTimeout() {
        RawHtmlDocument doc = new RawHtmlDocument(
                1, "https://spring.io", "https://spring.io", HASH, 200, "text/html", "<html>Content</html>", Instant.now()
        );

        RawHtmlProducer shortTimeoutProducer = new RawHtmlProducer(kafkaTemplate, TOPIC, Duration.ofMillis(100));
        CompletableFuture<SendResult<String, Object>> uncompletedFuture = new CompletableFuture<>();

        when(kafkaTemplate.send(eq(TOPIC), eq(HASH), eq(doc))).thenReturn(uncompletedFuture);

        assertThatThrownBy(() -> shortTimeoutProducer.publish(doc))
                .isInstanceOf(RawHtmlPublishException.class);
    }

    @Test
    void throwsExceptionWhenDocumentOrUrlHashIsNull() {
        assertThatThrownBy(() -> rawHtmlProducer.publish(null))
                .isInstanceOf(RawHtmlPublishException.class);

        RawHtmlDocument docWithNullHash = new RawHtmlDocument(
                1, "https://spring.io", "https://spring.io", null, 200, "text/html", "<html>Content</html>", Instant.now()
        );

        assertThatThrownBy(() -> rawHtmlProducer.publish(docWithNullHash))
                .isInstanceOf(RawHtmlPublishException.class);
    }
}
