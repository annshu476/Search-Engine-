package com.searchengine.crawler.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.searchengine.crawler.model.dto.PageFetchResult;
import com.searchengine.crawler.service.CrawlerService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExceptionClassificationTest {

    private CrawlerService crawlerService;

    @BeforeEach
    void setUp() {
        crawlerService = new CrawlerService(null, null, null);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504, 429})
    void classifiesTransientHttpStatusesAsRetryable(int statusCode) {
        PageFetchResult result = PageFetchResult.failure("https://example.com", "https://example.com", statusCode, "text/html", "Server Error " + statusCode, Instant.now());
        assertThat(crawlerService.isRetryable(result)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP response timeout", "Connection reset by peer", "DNS resolution failed", "HTTP request failed: Connection refused"})
    void classifiesNetworkAndTimeoutFailuresAsRetryable(String failureReason) {
        PageFetchResult result = PageFetchResult.failure("https://example.com", "https://example.com", 0, null, failureReason, Instant.now());
        assertThat(crawlerService.isRetryable(result)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 410})
    void classifiesPermanentClientErrorStatusesAsNonRetryable(int statusCode) {
        PageFetchResult result = PageFetchResult.failure("https://example.com", "https://example.com", statusCode, "text/html", "Client Error " + statusCode, Instant.now());
        assertThat(crawlerService.isRetryable(result)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Unsupported content-type: application/pdf", "Missing Content-Type header", "Empty HTML response", "Response body exceeds maximum limit", "SSRF protection blocked target host: localhost"})
    void classifiesContentAndSecurityErrorsAsNonRetryable(String failureReason) {
        PageFetchResult result = PageFetchResult.failure("https://example.com", "https://example.com", 200, "application/pdf", failureReason, Instant.now());
        assertThat(crawlerService.isRetryable(result)).isFalse();
    }

    @Test
    void classifiesRobotsUnavailableExceptionAsRetryable() {
        RobotsUnavailableException ex = new RobotsUnavailableException("https://example.com", "HTTP 500");
        assertThat(ex).isInstanceOf(RetryableCrawlerException.class);
    }
}
