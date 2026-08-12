package com.searchengine.crawler.fetcher;

import com.searchengine.crawler.model.dto.PageFetchResult;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Component responsible for performing HTTP GET requests and classifying response outcomes.
 */
@Component
public class PageFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(PageFetcher.class);

    private final WebClient webClient;
    private final String userAgent;
    private final boolean ssrfProtectionEnabled;

    public PageFetcher(
            WebClient webClient,
            @Value("${crawler.http.user-agent:SearchEngineBot/1.0}") String userAgent,
            @Value("${crawler.http.ssrf-protection-enabled:true}") boolean ssrfProtectionEnabled
    ) {
        this.webClient = webClient;
        this.userAgent = userAgent;
        this.ssrfProtectionEnabled = ssrfProtectionEnabled;
    }

    public Mono<PageFetchResult> fetch(String urlString) {
        Instant startTime = Instant.now();

        if (ssrfProtectionEnabled) {
            String ssrfError = checkSsrf(urlString);
            if (ssrfError != null) {
                LOGGER.warn("SSRF_BLOCKED url={} reason=\"{}\"", urlString, ssrfError);
                return Mono.just(PageFetchResult.failure(urlString, urlString, 0, null, ssrfError, startTime));
            }
        }

        return webClient.get()
                .uri(urlString)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .exchangeToMono(response -> {
                    int statusCode = response.statusCode().value();
                    String finalUrl = (response.request() != null && response.request().getURI() != null)
                            ? response.request().getURI().toString()
                            : urlString;
                    String contentType = response.headers().contentType()
                            .map(MediaType::toString)
                            .orElse("");

                    if (contentType.isBlank()) {
                        return Mono.just(PageFetchResult.failure(urlString, finalUrl, statusCode, contentType, "Missing Content-Type header", Instant.now()));
                    }

                    if (!isSupportedHtmlContentType(contentType)) {
                        return Mono.just(PageFetchResult.failure(urlString, finalUrl, statusCode, contentType, "Unsupported content-type: " + contentType, Instant.now()));
                    }

                    if (statusCode == 204) {
                        return Mono.just(PageFetchResult.failure(urlString, finalUrl, 204, contentType, "No content (204)", Instant.now()));
                    }

                    if (statusCode >= 300 && statusCode < 400) {
                        String location = response.headers().header(HttpHeaders.LOCATION).stream().findFirst().orElse(finalUrl);
                        return Mono.just(PageFetchResult.failure(urlString, location, statusCode, contentType, "Redirect limit exceeded", Instant.now()));
                    }

                    if (statusCode < 200 || statusCode >= 300) {
                        return Mono.just(PageFetchResult.failure(urlString, finalUrl, statusCode, contentType, "HTTP status " + statusCode, Instant.now()));
                    }

                    return response.bodyToMono(String.class)
                            .flatMap(body -> {
                                if (body == null || body.isBlank()) {
                                    return Mono.just(PageFetchResult.failure(urlString, finalUrl, statusCode, contentType, "Empty HTML response", Instant.now()));
                                }
                                return Mono.just(PageFetchResult.success(urlString, finalUrl, statusCode, contentType, body, Instant.now()));
                            })
                            .defaultIfEmpty(PageFetchResult.failure(urlString, finalUrl, statusCode, contentType, "Empty HTML response", Instant.now()));
                })
                .onErrorResume(ex -> {
                    String reason = "HTTP request failed: " + ex.getMessage();
                    if (ex instanceof DataBufferLimitException || (ex.getCause() != null && ex.getCause() instanceof DataBufferLimitException)) {
                        reason = "Response body exceeds maximum limit";
                    } else if (ex instanceof TimeoutException || (ex.getCause() != null && ex.getCause() instanceof TimeoutException) || ex.getMessage().contains("Timeout")) {
                        reason = "HTTP response timeout";
                    } else if (ex instanceof UnsupportedCharsetException || (ex.getCause() != null && ex.getCause() instanceof UnsupportedCharsetException)) {
                        reason = "Invalid or unsupported character encoding: " + ex.getMessage();
                    } else if (ex.getMessage() != null && (ex.getMessage().contains("redirect") || ex.getMessage().contains("Redirect"))) {
                        reason = "Redirect limit exceeded";
                    }
                    return Mono.just(PageFetchResult.failure(urlString, urlString, 0, null, reason, startTime));
                });
    }

    private String checkSsrf(String urlString) {
        try {
            URI uri = URI.create(urlString);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "Invalid URL host";
            }
            String lowerHost = host.toLowerCase();
            if (lowerHost.equals("localhost") || lowerHost.endsWith(".local") || lowerHost.equals("127.0.0.1") || lowerHost.equals("::1") || lowerHost.equals("0.0.0.0")) {
                return "SSRF protection blocked target host: " + host;
            }
            if ("169.254.169.254".equals(lowerHost)) {
                return "SSRF protection blocked cloud metadata IP: " + host;
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                return "SSRF protection blocked private/loopback IP address: " + address.getHostAddress();
            }
            if ("169.254.169.254".equals(address.getHostAddress())) {
                return "SSRF protection blocked cloud metadata IP";
            }
        } catch (Exception ex) {
            return "Invalid URL or host resolution failed: " + ex.getMessage();
        }
        return null;
    }

    private boolean isSupportedHtmlContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String lower = contentType.toLowerCase().trim();
        return lower.startsWith("text/html")
                || lower.startsWith("application/xhtml+xml")
                || lower.contains("text/html")
                || lower.contains("application/xhtml+xml");
    }
}
