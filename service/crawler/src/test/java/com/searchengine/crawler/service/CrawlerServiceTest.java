package com.searchengine.crawler.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.searchengine.crawler.exception.PageFetchException;
import com.searchengine.crawler.exception.RobotsUnavailableException;
import com.searchengine.crawler.fetcher.PageFetcher;
import com.searchengine.crawler.model.dto.PageFetchResult;
import com.searchengine.crawler.model.dto.RobotsCheckResult;
import com.searchengine.crawler.model.kafka.UrlTask;
import com.searchengine.crawler.robots.RobotsChecker;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CrawlerServiceTest {

    @Mock
    private RobotsChecker robotsChecker;

    @Mock
    private PageFetcher pageFetcher;

    @InjectMocks
    private CrawlerService crawlerService;

    @Test
    void acceptsAndFetchesWhenRobotsAllowed() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io",
                "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28",
                5,
                Instant.now()
        );

        when(robotsChecker.check(anyString()))
                .thenReturn(Mono.just(RobotsCheckResult.allowed("https://spring.io", -1, "Allowed")));
        when(pageFetcher.fetch(anyString()))
                .thenReturn(Mono.just(PageFetchResult.success(task.url(), task.url(), 200, "text/html", "<html>Content</html>", Instant.now())));

        assertThatNoException().isThrownBy(() -> crawlerService.processUrlTask(task));
        verify(pageFetcher).fetch(task.url());
    }

    @Test
    void skipsFetchingAndReturnsNormallyWhenRobotsDisallowed() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io/private",
                "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28",
                5,
                Instant.now()
        );

        when(robotsChecker.check(anyString()))
                .thenReturn(Mono.just(RobotsCheckResult.disallowed("https://spring.io", "Disallowed")));

        assertThatNoException().isThrownBy(() -> crawlerService.processUrlTask(task));
        verify(pageFetcher, never()).fetch(anyString());
    }

    @Test
    void throwsRobotsUnavailableExceptionWhenRobotsFails() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io",
                "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28",
                5,
                Instant.now()
        );

        when(robotsChecker.check(anyString()))
                .thenReturn(Mono.just(RobotsCheckResult.unavailable("https://spring.io", "HTTP 500")));

        assertThatThrownBy(() -> crawlerService.processUrlTask(task))
                .isInstanceOf(RobotsUnavailableException.class)
                .hasMessageContaining("HTTP 500");

        verify(pageFetcher, never()).fetch(anyString());
    }

    @Test
    void throwsPageFetchExceptionWhenFetchFails() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io",
                "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28",
                5,
                Instant.now()
        );

        when(robotsChecker.check(anyString()))
                .thenReturn(Mono.just(RobotsCheckResult.allowed("https://spring.io", -1, "Allowed")));
        when(pageFetcher.fetch(anyString()))
                .thenReturn(Mono.just(PageFetchResult.failure(task.url(), task.url(), 404, "text/html", "HTTP status 404", Instant.now())));

        assertThatThrownBy(() -> crawlerService.processUrlTask(task))
                .isInstanceOf(PageFetchException.class)
                .hasMessageContaining("HTTP status 404");
    }
}
