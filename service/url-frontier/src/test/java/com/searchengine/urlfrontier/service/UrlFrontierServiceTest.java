package com.searchengine.urlfrontier.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.searchengine.urlfrontier.exception.KafkaPublishException;
import com.searchengine.urlfrontier.exception.RedisUnavailableException;
import com.searchengine.urlfrontier.hasher.Sha256UrlHasher;
import com.searchengine.urlfrontier.model.dto.SubmitUrlRequest;
import com.searchengine.urlfrontier.model.dto.SubmitUrlResponse;
import com.searchengine.urlfrontier.model.kafka.UrlTask;
import com.searchengine.urlfrontier.normalizer.UrlNormalizer;
import com.searchengine.urlfrontier.priority.UrlPriorityAssigner;
import com.searchengine.urlfrontier.producer.UrlTaskProducer;
import com.searchengine.urlfrontier.repository.VisitedUrlRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class UrlFrontierServiceTest {
    private static final Instant TIMESTAMP = Instant.parse("2026-07-31T10:00:00Z");

    private VisitedUrlRepository repository;
    private UrlPriorityAssigner priorityAssigner;
    private UrlTaskProducer urlTaskProducer;
    private UrlFrontierService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(VisitedUrlRepository.class);
        priorityAssigner = Mockito.mock(UrlPriorityAssigner.class);
        urlTaskProducer = Mockito.mock(UrlTaskProducer.class);
        service = new UrlFrontierService(Clock.fixed(TIMESTAMP, ZoneOffset.UTC),
                new Sha256UrlHasher(), new UrlNormalizer(), repository, priorityAssigner, urlTaskProducer);
    }

    @Test
    void returnsOriginalAndNormalizedUrlsWithCurrentTimestamp() {
        when(repository.storeIfAbsent(anyString(), eq(TIMESTAMP))).thenReturn(true);
        when(priorityAssigner.assign()).thenReturn(UrlPriorityAssigner.DEFAULT_PRIORITY);

        SubmitUrlResponse response = service.submit(new SubmitUrlRequest(" HTTPS://SPRING.IO/ "));
        ArgumentCaptor<UrlTask> taskCaptor = ArgumentCaptor.forClass(UrlTask.class);

        assertThat(response.accepted()).isTrue();
        assertThat(response.originalUrl()).isEqualTo(" HTTPS://SPRING.IO/ ");
        assertThat(response.normalizedUrl()).isEqualTo("https://spring.io");
        assertThat(response.urlHash()).isEqualTo("007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28");
        assertThat(response.priority()).isEqualTo(UrlPriorityAssigner.DEFAULT_PRIORITY);
        assertThat(response.priority()).isBetween(UrlPriorityAssigner.MIN_PRIORITY, UrlPriorityAssigner.MAX_PRIORITY);
        assertThat(response.message()).isEqualTo("URL accepted");
        assertThat(response.timestamp()).isEqualTo(TIMESTAMP);
        verify(repository).storeIfAbsent("007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28", TIMESTAMP);
        verify(priorityAssigner).assign();
        verify(urlTaskProducer).publish(taskCaptor.capture());
        UrlTask task = taskCaptor.getValue();
        assertThat(task.schemaVersion()).isEqualTo(UrlTask.SCHEMA_VERSION);
        assertThat(task.url()).isEqualTo("https://spring.io");
        assertThat(task.urlHash()).isEqualTo("007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28");
        assertThat(task.priority()).isEqualTo(UrlPriorityAssigner.DEFAULT_PRIORITY);
        assertThat(task.discoveredAt()).isEqualTo(TIMESTAMP);
    }

    @Test
    void returnsDuplicateResponseWhenRepositoryRejectsHash() {
        when(repository.storeIfAbsent(anyString(), eq(TIMESTAMP))).thenReturn(false);

        SubmitUrlResponse response = service.submit(new SubmitUrlRequest("https://spring.io"));

        assertThat(response.accepted()).isFalse();
        assertThat(response.normalizedUrl()).isEqualTo("https://spring.io");
        assertThat(response.urlHash()).isEqualTo("007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28");
        assertThat(response.priority()).isNull();
        assertThat(response.message()).isEqualTo("URL has already been seen");
        assertThat(response.timestamp()).isEqualTo(TIMESTAMP);
        verify(repository).storeIfAbsent("007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28", TIMESTAMP);
        verify(priorityAssigner, never()).assign();
        verify(urlTaskProducer, never()).publish(Mockito.any());
    }

    @Test
    void doesNotPublishWhenRedisFails() {
        when(repository.storeIfAbsent(anyString(), eq(TIMESTAMP))).thenThrow(new RedisUnavailableException(null));

        assertThatThrownBy(() -> service.submit(new SubmitUrlRequest("https://spring.io")))
                .isInstanceOf(RedisUnavailableException.class);

        verify(priorityAssigner, never()).assign();
        verify(urlTaskProducer, never()).publish(Mockito.any());
    }

    @Test
    void throwsWhenKafkaPublishingFails() {
        when(repository.storeIfAbsent(anyString(), eq(TIMESTAMP))).thenReturn(true);
        when(priorityAssigner.assign()).thenReturn(UrlPriorityAssigner.DEFAULT_PRIORITY);
        Mockito.doThrow(new KafkaPublishException(new RuntimeException("kafka unavailable")))
                .when(urlTaskProducer).publish(Mockito.any(UrlTask.class));

        assertThatThrownBy(() -> service.submit(new SubmitUrlRequest("https://spring.io")))
                .isInstanceOf(KafkaPublishException.class);

        verify(priorityAssigner).assign();
    }
}
