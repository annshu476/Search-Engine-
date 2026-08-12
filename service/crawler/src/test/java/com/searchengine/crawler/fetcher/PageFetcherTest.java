package com.searchengine.crawler.fetcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.searchengine.crawler.model.dto.PageFetchResult;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class PageFetcherTest {

    private MockWebServer mockWebServer;
    private PageFetcher pageFetcher;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder().build();
        // Disable SSRF check during MockWebServer test calls so loopback calls to MockWebServer are permitted
        pageFetcher = new PageFetcher(webClient, "SearchEngineBot/1.0", false);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchesSuccessfulHtmlResponse() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body>Hello World</body></html>"));

        String url = mockWebServer.url("/test").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.contentType()).contains("text/html");
        assertThat(result.body()).isEqualTo("<html><body>Hello World</body></html>");
        assertThat(result.requestedUrl()).isEqualTo(url);
        assertThat(result.finalUrl()).isEqualTo(url);
        assertThat(result.fetchedAt()).isNotNull();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getHeader("User-Agent")).isEqualTo("SearchEngineBot/1.0");
    }

    @Test
    void rejectsUnsupportedContentType() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/pdf")
                .setBody("%PDF-1.4 dummy binary content"));

        String url = mockWebServer.url("/document.pdf").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("Unsupported content-type");
    }

    @Test
    void handles404NotFound() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>404 Not Found</html>"));

        String url = mockWebServer.url("/missing").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.statusCode()).isEqualTo(404);
        assertThat(result.failureReason()).contains("HTTP status 404");
    }

    @Test
    void handles500ServerError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>Internal Error</html>"));

        String url = mockWebServer.url("/error").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.statusCode()).isEqualTo(500);
        assertThat(result.failureReason()).contains("HTTP status 500");
    }

    @Test
    void blocksSsrfTargetHosts() {
        WebClient webClient = WebClient.builder().build();
        PageFetcher ssrfStrictFetcher = new PageFetcher(webClient, "SearchEngineBot/1.0", true);

        PageFetchResult resultLocalhost = ssrfStrictFetcher.fetch("http://localhost:8080/admin").block();
        assertThat(resultLocalhost).isNotNull();
        assertThat(resultLocalhost.success()).isFalse();
        assertThat(resultLocalhost.failureReason()).contains("SSRF protection blocked");

        PageFetchResult resultMetadata = ssrfStrictFetcher.fetch("http://169.254.169.254/latest/meta-data").block();
        assertThat(resultMetadata).isNotNull();
        assertThat(resultMetadata.success()).isFalse();
        assertThat(resultMetadata.failureReason()).contains("SSRF protection blocked");
    }
}
