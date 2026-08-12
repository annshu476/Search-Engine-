package com.searchengine.crawler.service;

import static org.assertj.core.api.Assertions.assertThatNoException;

import com.searchengine.crawler.model.kafka.UrlTask;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CrawlerServiceTest {

    private final CrawlerService crawlerService = new CrawlerService();

    @Test
    void acceptsValidUrlTaskWithoutException() {
        UrlTask task = new UrlTask(
                1,
                "https://spring.io",
                "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28",
                5,
                Instant.now()
        );

        assertThatNoException().isThrownBy(() -> crawlerService.processUrlTask(task));
    }
}
