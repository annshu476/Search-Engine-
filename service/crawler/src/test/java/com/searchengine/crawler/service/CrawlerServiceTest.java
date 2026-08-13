package com.searchengine.crawler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.searchengine.crawler.exception.NonRetryableCrawlerException;
import com.searchengine.crawler.exception.RawHtmlPublishException;
import com.searchengine.crawler.exception.RetryableCrawlerException;
import com.searchengine.crawler.exception.RobotsUnavailableException;
import com.searchengine.crawler.fetcher.PageFetcher;
import com.searchengine.crawler.model.dto.PageFetchResult;
import com.searchengine.crawler.model.dto.RobotsCheckResult;
import com.searchengine.crawler.model.kafka.RawHtmlDocument;
import com.searchengine.crawler.model.kafka.UrlTask;
import com.searchengine.crawler.producer.RawHtmlProducer;
import com.searchengine.crawler.ratelimit.DomainRateLimiter;
import com.searchengine.crawler.robots.RobotsChecker;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CrawlerServiceTest {

    @Mock
    private RobotsChecker robotsChecker;

    @Mock
    private DomainRateLimiter domainRateLimiter;

    @Mock
    private PageFetcher pageFetcher;

    @Mock
    private RawHtmlProducer rawHtmlProducer;

    @InjectMocks
    private CrawlerService crawlerService;

    private static final String HASH = "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28";

    @Test
    void acceptsFetchesAndPublishesWhenRobotsAllowedAndRateLimited() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io",
                HASH,
                5,
                Instant.now()
        );

        when(robotsChecker.check(anyString()))
                .thenReturn(Mono.just(RobotsCheckResult.allowed("https://spring.io", -1, "Allowed")));
        when(pageFetcher.fetch(anyString()))
                .thenReturn(Mono.just(PageFetchResult.success(task.url(), task.url(), 200, "text/html", "<html>Content</html>", Instant.now())));

        assertThatNoException().isThrownBy(() -> crawlerService.processUrlTask(task));

        verify(domainRateLimiter).acquire("https://spring.io", -1);
        verify(pageFetcher).fetch(task.url());
        verify(domainRateLimiter).release("https://spring.io");

        ArgumentCaptor<RawHtmlDocument> captor = ArgumentCaptor.forClass(RawHtmlDocument.class);
        verify(rawHtmlProducer).publish(captor.capture());

        RawHtmlDocument publishedDoc = captor.getValue();
        assertThat(publishedDoc.schemaVersion()).isEqualTo(1);
        assertThat(publishedDoc.url()).isEqualTo("https://spring.io");
        assertThat(publishedDoc.finalUrl()).isEqualTo("https://spring.io");
        assertThat(publishedDoc.urlHash()).isEqualTo(HASH);
        assertThat(publishedDoc.statusCode()).isEqualTo(200);
        assertThat(publishedDoc.contentType()).isEqualTo("text/html");
        assertThat(publishedDoc.html()).isEqualTo("<html>Content</html>");
        assertThat(publishedDoc.fetchedAt()).isNotNull();
    }

    @Test
    void skipsFetchingAndPublishingWhenRobotsDisallowed() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io/private",
                HASH,
                5,
                Instant.now()
        );

        when(robotsChecker.check(anyString()))
                .thenReturn(Mono.just(RobotsCheckResult.disallowed("https://spring.io", "Disallowed")));

        assertThatNoException().isThrownBy(() -> crawlerService.processUrlTask(task));

        verify(domainRateLimiter, never()).acquire(anyString(), anyLong());
        verify(pageFetcher, never()).fetch(anyString());
        verify(domainRateLimiter, never()).release(anyString());
        verify(rawHtmlProducer, never()).publish(any());
    }

    @Test
    void throwsRobotsUnavailableExceptionWithoutRateLimitingOrPublishing() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io",
                HASH,
                5,
                Instant.now()
        );

        when(robotsChecker.check(anyString()))
                .thenReturn(Mono.just(RobotsCheckResult.unavailable("https://spring.io", "HTTP 500")));

        assertThatThrownBy(() -> crawlerService.processUrlTask(task))
                .isInstanceOf(RobotsUnavailableException.class)
                .hasMessageContaining("HTTP 500");

        verify(domainRateLimiter, never()).acquire(anyString(), anyLong());
        verify(pageFetcher, never()).fetch(anyString());
        verify(rawHtmlProducer, never()).publish(any());
    }

    @Test
    void throwsNonRetryableExceptionOn404WithoutPublishing() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io",
                HASH,
                5,
                Instant.now()
        );

        when(robotsChecker.check(anyString()))
                .thenReturn(Mono.just(RobotsCheckResult.allowed("https://spring.io", 5000L, "Allowed")));
        when(pageFetcher.fetch(anyString()))
                .thenReturn(Mono.just(PageFetchResult.failure(task.url(), task.url(), 404, "text/html", "HTTP status 404", Instant.now())));

        assertThatThrownBy(() -> crawlerService.processUrlTask(task))
                .isInstanceOf(NonRetryableCrawlerException.class)
                .hasMessageContaining("HTTP status 404");

        verify(domainRateLimiter).acquire("https://spring.io", 5000L);
        verify(domainRateLimiter).release("https://spring.io");
        verify(rawHtmlProducer, never()).publish(any());
    }

    @Test
    void throwsRetryableExceptionOn500WithoutPublishing() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io",
                HASH,
                5,
                Instant.now()
        );

        when(robotsChecker.check(anyString()))
                .thenReturn(Mono.just(RobotsCheckResult.allowed("https://spring.io", 5000L, "Allowed")));
        when(pageFetcher.fetch(anyString()))
                .thenReturn(Mono.just(PageFetchResult.failure(task.url(), task.url(), 500, "text/html", "HTTP status 500", Instant.now())));

        assertThatThrownBy(() -> crawlerService.processUrlTask(task))
                .isInstanceOf(RetryableCrawlerException.class)
                .hasMessageContaining("HTTP status 500");

        verify(domainRateLimiter).acquire("https://spring.io", 5000L);
        verify(domainRateLimiter).release("https://spring.io");
        verify(rawHtmlProducer, never()).publish(any());
    }

    @Test
    void releasesRateLimitPermitAndThrowsRetryableExceptionWhenPublishingFails() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io",
                HASH,
                5,
                Instant.now()
        );

        when(robotsChecker.check(anyString()))
                .thenReturn(Mono.just(RobotsCheckResult.allowed("https://spring.io", 5000L, "Allowed")));
        when(pageFetcher.fetch(anyString()))
                .thenReturn(Mono.just(PageFetchResult.success(task.url(), task.url(), 200, "text/html", "<html>Content</html>", Instant.now())));
        doThrow(new RawHtmlPublishException(task.url(), "Kafka broker unreachable"))
                .when(rawHtmlProducer).publish(any());

        assertThatThrownBy(() -> crawlerService.processUrlTask(task))
                .isInstanceOf(RawHtmlPublishException.class)
                .isInstanceOf(RetryableCrawlerException.class);

        verify(domainRateLimiter).acquire("https://spring.io", 5000L);
        verify(domainRateLimiter).release("https://spring.io");
    }
}
