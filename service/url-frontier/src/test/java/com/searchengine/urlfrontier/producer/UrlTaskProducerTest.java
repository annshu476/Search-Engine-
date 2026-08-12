package com.searchengine.urlfrontier.producer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.searchengine.urlfrontier.config.UrlTaskPublisherProperties;
import com.searchengine.urlfrontier.exception.KafkaPublishException;
import com.searchengine.urlfrontier.model.kafka.UrlTask;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class UrlTaskProducerTest {

    @Test
    void publishesToConfiguredTopicUsingUrlHashAsKey() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, UrlTask> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        UrlTask task = new UrlTask(UrlTask.SCHEMA_VERSION, "https://spring.io", "hash", 5,
                Instant.parse("2026-08-11T18:30:00Z"));
        CompletableFuture<SendResult<String, UrlTask>> future = CompletableFuture.completedFuture(new SendResult<>(
                new ProducerRecord<>("url-topic", "hash", task),
                new org.apache.kafka.clients.producer.RecordMetadata(
                        new TopicPartition("url-topic", 0), 0, 12, 0L, 0, 0)));
        when(kafkaTemplate.send("url-topic", "hash", task)).thenReturn(future);

        new UrlTaskProducer(kafkaTemplate, new UrlTaskPublisherProperties("url-topic", Duration.ofSeconds(3)))
                .publish(task);

        verify(kafkaTemplate).send("url-topic", "hash", task);
    }

    @Test
    void throwsWhenKafkaPublishFails() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, UrlTask> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        UrlTask task = new UrlTask(UrlTask.SCHEMA_VERSION, "https://spring.io", "hash", 5,
                Instant.parse("2026-08-11T18:30:00Z"));
        CompletableFuture<SendResult<String, UrlTask>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("kafka unavailable"));
        when(kafkaTemplate.send("url-topic", "hash", task)).thenReturn(future);

        assertThatThrownBy(() -> new UrlTaskProducer(kafkaTemplate,
                new UrlTaskPublisherProperties("url-topic", Duration.ofSeconds(3))).publish(task))
                .isInstanceOf(KafkaPublishException.class);
    }

    @Test
    void timesOutWhenKafkaAcknowledgementDoesNotArrive() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, UrlTask> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        UrlTask task = new UrlTask(UrlTask.SCHEMA_VERSION, "https://spring.io", "hash", 5,
                Instant.parse("2026-08-11T18:30:00Z"));
        when(kafkaTemplate.send("url-topic", "hash", task)).thenReturn(new CompletableFuture<>());

        assertThatThrownBy(() -> new UrlTaskProducer(kafkaTemplate,
                new UrlTaskPublisherProperties("url-topic", Duration.ofMillis(50))).publish(task))
                .isInstanceOf(KafkaPublishException.class);
    }
}
