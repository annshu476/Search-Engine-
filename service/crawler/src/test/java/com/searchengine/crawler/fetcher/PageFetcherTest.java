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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

class PageFetcherTest {

    private MockWebServer mockWebServer;
    private PageFetcher pageFetcher;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        HttpClient httpClient = HttpClient.create().followRedirect(true);
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        pageFetcher = new PageFetcher(webClient, "SearchEngineBot/1.0", false);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchesSuccessful200HtmlResponse() throws Exception {
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
    void fetchesSuccessful201CreatedHtmlResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>Created Page</html>"));

        String url = mockWebServer.url("/created").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(result.body()).isEqualTo("<html>Created Page</html>");
    }

    @Test
    void rejects204NoContent() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(204)
                .setHeader("Content-Type", "text/html"));

        String url = mockWebServer.url("/no-content").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.statusCode()).isEqualTo(204);
        assertThat(result.failureReason()).contains("No content (204)");
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 307, 308})
    void followsRedirectsToFinalUrl(int redirectCode) {
        String targetPath = "/target-page";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(redirectCode)
                .setHeader("Location", targetPath));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>Final Destination</html>"));

        String initialUrl = mockWebServer.url("/redirect-start").toString();
        PageFetchResult result = pageFetcher.fetch(initialUrl).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).isEqualTo("<html>Final Destination</html>");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 410})
    void handlesClientErrorStatuses(int statusCode) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>Client Error</html>"));

        String url = mockWebServer.url("/client-error").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.statusCode()).isEqualTo(statusCode);
        assertThat(result.failureReason()).contains("HTTP status " + statusCode);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503})
    void handlesServerErrorStatuses(int statusCode) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>Server Error</html>"));

        String url = mockWebServer.url("/server-error").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.statusCode()).isEqualTo(statusCode);
        assertThat(result.failureReason()).contains("HTTP status " + statusCode);
    }

    @Test
    void rejectsMissingContentTypeHeader() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("<html>No Content Type Header</html>"));

        String url = mockWebServer.url("/no-header").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("Missing Content-Type header");
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/pdf", "image/png", "video/mp4", "application/zip", "application/octet-stream"})
    void rejectsUnsupportedContentTypes(String contentType) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", contentType)
                .setBody("binary content"));

        String url = mockWebServer.url("/binary").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("Unsupported content-type");
    }

    @Test
    void acceptsHtmlWithCharsetParameters() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=ISO-8859-1")
                .setBody("<html><body>ISO Encoded Content</body></html>"));

        String url = mockWebServer.url("/iso").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.contentType()).contains("ISO-8859-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t  "})
    void rejectsEmptyOrWhitespaceOnlyBody(String emptyBody) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody(emptyBody));

        String url = mockWebServer.url("/empty").toString();
        PageFetchResult result = pageFetcher.fetch(url).block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("Empty HTML response");
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
