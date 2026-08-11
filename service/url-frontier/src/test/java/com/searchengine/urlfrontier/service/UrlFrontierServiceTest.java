package com.searchengine.urlfrontier.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.searchengine.urlfrontier.hasher.Sha256UrlHasher;
import com.searchengine.urlfrontier.model.dto.SubmitUrlRequest;
import com.searchengine.urlfrontier.model.dto.SubmitUrlResponse;
import com.searchengine.urlfrontier.normalizer.UrlNormalizer;
import com.searchengine.urlfrontier.priority.UrlPriorityAssigner;
import com.searchengine.urlfrontier.repository.VisitedUrlRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UrlFrontierServiceTest {

    @Test
    void returnsOriginalAndNormalizedUrlsWithCurrentTimestamp() {
        Instant timestamp = Instant.parse("2026-07-31T10:00:00Z");
        VisitedUrlRepository repository = Mockito.mock(VisitedUrlRepository.class);
        when(repository.storeIfAbsent(anyString(), eq(timestamp))).thenReturn(true);
        UrlPriorityAssigner priorityAssigner = Mockito.mock(UrlPriorityAssigner.class);
        when(priorityAssigner.assign()).thenReturn(UrlPriorityAssigner.DEFAULT_PRIORITY);
        UrlFrontierService service = new UrlFrontierService(Clock.fixed(timestamp, ZoneOffset.UTC),
                new Sha256UrlHasher(), new UrlNormalizer(), repository, priorityAssigner);

        SubmitUrlResponse response = service.submit(new SubmitUrlRequest(" HTTPS://SPRING.IO/ "));

        assertThat(response.accepted()).isTrue();
        assertThat(response.originalUrl()).isEqualTo(" HTTPS://SPRING.IO/ ");
        assertThat(response.normalizedUrl()).isEqualTo("https://spring.io");
        assertThat(response.urlHash()).isEqualTo("007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28");
        assertThat(response.priority()).isEqualTo(UrlPriorityAssigner.DEFAULT_PRIORITY);
        assertThat(response.priority()).isBetween(UrlPriorityAssigner.MIN_PRIORITY, UrlPriorityAssigner.MAX_PRIORITY);
        assertThat(response.message()).isEqualTo("URL accepted");
        assertThat(response.timestamp()).isEqualTo(timestamp);
        verify(repository).storeIfAbsent("007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28", timestamp);
        verify(priorityAssigner).assign();
    }

    @Test
    void returnsDuplicateResponseWhenRepositoryRejectsHash() {
        Instant timestamp = Instant.parse("2026-07-31T10:00:00Z");
        VisitedUrlRepository repository = Mockito.mock(VisitedUrlRepository.class);
        when(repository.storeIfAbsent(anyString(), eq(timestamp))).thenReturn(false);
        UrlPriorityAssigner priorityAssigner = Mockito.mock(UrlPriorityAssigner.class);
        UrlFrontierService service = new UrlFrontierService(Clock.fixed(timestamp, ZoneOffset.UTC),
                new Sha256UrlHasher(), new UrlNormalizer(), repository, priorityAssigner);

        SubmitUrlResponse response = service.submit(new SubmitUrlRequest("https://spring.io"));

        assertThat(response.accepted()).isFalse();
        assertThat(response.normalizedUrl()).isEqualTo("https://spring.io");
        assertThat(response.urlHash()).isEqualTo("007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28");
        assertThat(response.priority()).isNull();
        assertThat(response.message()).isEqualTo("URL has already been seen");
        assertThat(response.timestamp()).isEqualTo(timestamp);
        verify(repository).storeIfAbsent("007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28", timestamp);
        verify(priorityAssigner, Mockito.never()).assign();
    }
}
