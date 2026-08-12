package com.searchengine.crawler.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.searchengine.crawler.exception.PageFetchException;
import com.searchengine.crawler.fetcher.PageFetcher;
import com.searchengine.crawler.model.dto.PageFetchResult;
import com.searchengine.crawler.model.kafka.UrlTask;
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
    private PageFetcher pageFetcher;

    @InjectMocks
    private CrawlerService crawlerService;

    @Test
    void acceptsValidUrlTaskWhenFetchSucceeds() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io",
                "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28",
                5,
                Instant.now()
        );

        PageFetchResult successResult = PageFetchResult.success(
                task.url(), task.url(), 200, "text/html", "<html>Content</html>", Instant.now()
        );

        when(pageFetcher.fetch(anyString())).thenReturn(Mono.just(successResult));

        assertThatNoException().isThrownBy(() -> crawlerService.processUrlTask(task));
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

        PageFetchResult failureResult = PageFetchResult.failure(
                task.url(), task.url(), 404, "text/html", "HTTP status 404", Instant.now()
        );

        when(pageFetcher.fetch(anyString())).thenReturn(Mono.just(failureResult));

        assertThatThrownBy(() -> crawlerService.processUrlTask(task))
                .isInstanceOf(PageFetchException.class)
                .hasMessageContaining("HTTP status 404");
    }
}
