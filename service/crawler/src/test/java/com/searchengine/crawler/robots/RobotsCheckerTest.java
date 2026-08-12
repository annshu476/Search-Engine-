package com.searchengine.crawler.robots;

import static org.assertj.core.api.Assertions.assertThat;

import com.searchengine.crawler.model.dto.RobotsCheckResult;
import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

class RobotsCheckerTest {

    private MockWebServer mockWebServer;
    private RobotsChecker robotsChecker;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        HttpClient httpClient = HttpClient.create();
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        RobotsFetcher robotsFetcher = new RobotsFetcher(webClient, "SearchEngineBot/1.0", Duration.ofSeconds(5), false);
        robotsChecker = new RobotsChecker(robotsFetcher, "SearchEngineBot/1.0", Duration.ofHours(1), 1000);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void allowsCrawlWhenRobotsReturns404() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        String baseUrl = mockWebServer.url("/page").toString();
        RobotsCheckResult result = robotsChecker.check(baseUrl).block();

        assertThat(result).isNotNull();
        assertThat(result.robotsAvailable()).isTrue();
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).contains("404");
    }

    @Test
    void allowsCrawlWhenExplicitlyAllowedByRobotsTxt() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain")
                .setBody("User-agent: SearchEngineBot\nDisallow: /private\nAllow: /public\nCrawl-delay: 5\n"));

        String baseUrl = mockWebServer.url("/public/page").toString();
        RobotsCheckResult result = robotsChecker.check(baseUrl).block();

        assertThat(result).isNotNull();
        assertThat(result.robotsAvailable()).isTrue();
        assertThat(result.allowed()).isTrue();
        assertThat(result.crawlDelayMs()).isEqualTo(5000L);
    }

    @Test
    void disallowsCrawlWhenDisallowedByRobotsTxt() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain")
                .setBody("User-agent: SearchEngineBot\nDisallow: /private\n"));

        String baseUrl = mockWebServer.url("/private/secret").toString();
        RobotsCheckResult result = robotsChecker.check(baseUrl).block();

        assertThat(result).isNotNull();
        assertThat(result.robotsAvailable()).isTrue();
        assertThat(result.allowed()).isFalse();
    }

    @Test
    void respectsWildcardUserAgentRules() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain")
                .setBody("User-agent: *\nDisallow: /admin\n"));

        String baseUrl = mockWebServer.url("/admin/dashboard").toString();
        RobotsCheckResult result = robotsChecker.check(baseUrl).block();

        assertThat(result).isNotNull();
        assertThat(result.allowed()).isFalse();
    }

    @Test
    void disallowsCrawlWhenRobotsReturns403Or401() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(403));

        String baseUrl = mockWebServer.url("/page").toString();
        RobotsCheckResult result = robotsChecker.check(baseUrl).block();

        assertThat(result).isNotNull();
        assertThat(result.robotsAvailable()).isTrue();
        assertThat(result.allowed()).isFalse();
    }

    @Test
    void marksUnavailableWhenRobotsReturns500() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        String baseUrl = mockWebServer.url("/page").toString();
        RobotsCheckResult result = robotsChecker.check(baseUrl).block();

        assertThat(result).isNotNull();
        assertThat(result.robotsAvailable()).isFalse();
        assertThat(result.allowed()).isFalse();
    }

    @Test
    void cachesRobotsResultPerOrigin() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain")
                .setBody("User-agent: *\nDisallow: /admin\n"));

        String url1 = mockWebServer.url("/admin/1").toString();
        String url2 = mockWebServer.url("/admin/2").toString();

        RobotsCheckResult result1 = robotsChecker.check(url1).block();
        RobotsCheckResult result2 = robotsChecker.check(url2).block();

        assertThat(result1.allowed()).isFalse();
        assertThat(result2.allowed()).isFalse();
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }
}
