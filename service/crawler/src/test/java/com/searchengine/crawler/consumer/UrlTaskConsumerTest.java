package com.searchengine.crawler.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.searchengine.crawler.exception.NonRetryableCrawlerException;
import com.searchengine.crawler.exception.RetryableCrawlerException;
import com.searchengine.crawler.model.kafka.UrlTask;
import com.searchengine.crawler.service.CrawlerService;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class UrlTaskConsumerTest {

    @Mock
    private CrawlerService crawlerService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private UrlTaskConsumer urlTaskConsumer;

    private static final String TOPIC = "url-topic";
    private static final int PARTITION = 0;
    private static final long OFFSET = 100L;
    private static final String HASH = "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28";

    @Test
    void acceptsAndAcknowledgesValidUrlTask() {
        UrlTask task = new UrlTask(1, "https://spring.io", HASH, 5, Instant.now());
        ConsumerRecord<String, UrlTask> record = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, HASH, task);

        urlTaskConsumer.consume(record, acknowledgment);

        verify(crawlerService).processUrlTask(task);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        UrlTask task = new UrlTask(2, "https://spring.io", HASH, 5, Instant.now());
        ConsumerRecord<String, UrlTask> record = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, HASH, task);

        assertThatThrownBy(() -> urlTaskConsumer.consume(record, acknowledgment))
                .isInstanceOf(NonRetryableCrawlerException.class)
                .hasMessageContaining("Unsupported schema version");

        verify(crawlerService, never()).processUrlTask(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsMissingOrBlankUrl(String invalidUrl) {
        UrlTask task = new UrlTask(1, invalidUrl, HASH, 5, Instant.now());
        ConsumerRecord<String, UrlTask> record = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, HASH, task);

        assertThatThrownBy(() -> urlTaskConsumer.consume(record, acknowledgment))
                .isInstanceOf(NonRetryableCrawlerException.class)
                .hasMessageContaining("Validation error");

        verify(crawlerService, never()).processUrlTask(any());
    }

    @Test
    void rejectsNullUrl() {
        UrlTask task = new UrlTask(1, null, HASH, 5, Instant.now());
        ConsumerRecord<String, UrlTask> record = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, HASH, task);

        assertThatThrownBy(() -> urlTaskConsumer.consume(record, acknowledgment))
                .isInstanceOf(NonRetryableCrawlerException.class);

        verify(crawlerService, never()).processUrlTask(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsMissingOrBlankUrlHash(String invalidHash) {
        UrlTask task = new UrlTask(1, "https://spring.io", invalidHash, 5, Instant.now());
        ConsumerRecord<String, UrlTask> record = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, invalidHash, task);

        assertThatThrownBy(() -> urlTaskConsumer.consume(record, acknowledgment))
                .isInstanceOf(NonRetryableCrawlerException.class);

        verify(crawlerService, never()).processUrlTask(any());
    }

    @Test
    void rejectsNullUrlHash() {
        UrlTask task = new UrlTask(1, "https://spring.io", null, 5, Instant.now());
        ConsumerRecord<String, UrlTask> record = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, null, task);

        assertThatThrownBy(() -> urlTaskConsumer.consume(record, acknowledgment))
                .isInstanceOf(NonRetryableCrawlerException.class);

        verify(crawlerService, never()).processUrlTask(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11, 99})
    void rejectsInvalidPriority(int invalidPriority) {
        UrlTask task = new UrlTask(1, "https://spring.io", HASH, invalidPriority, Instant.now());
        ConsumerRecord<String, UrlTask> record = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, HASH, task);

        assertThatThrownBy(() -> urlTaskConsumer.consume(record, acknowledgment))
                .isInstanceOf(NonRetryableCrawlerException.class);

        verify(crawlerService, never()).processUrlTask(any());
    }

    @Test
    void rejectsMissingDiscoveredAt() {
        UrlTask task = new UrlTask(1, "https://spring.io", HASH, 5, null);
        ConsumerRecord<String, UrlTask> record = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, HASH, task);

        assertThatThrownBy(() -> urlTaskConsumer.consume(record, acknowledgment))
                .isInstanceOf(NonRetryableCrawlerException.class);

        verify(crawlerService, never()).processUrlTask(any());
    }

    @Test
    void propagatesExceptionWhenServiceProcessingFails() {
        UrlTask task = new UrlTask(1, "https://spring.io", HASH, 5, Instant.now());
        ConsumerRecord<String, UrlTask> record = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, HASH, task);
        doThrow(new RetryableCrawlerException("https://spring.io", "HTTP 500", 500)).when(crawlerService).processUrlTask(task);

        assertThatThrownBy(() -> urlTaskConsumer.consume(record, acknowledgment))
                .isInstanceOf(RetryableCrawlerException.class);

        verify(crawlerService).processUrlTask(task);
    }
}
