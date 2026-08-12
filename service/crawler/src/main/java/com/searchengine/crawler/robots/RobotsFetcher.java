package com.searchengine.crawler.robots;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
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
 * Low-level HTTP fetcher for retrieving robots.txt policy files.
 */
@Component
public class RobotsFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(RobotsFetcher.class);

    private final WebClient webClient;
    private final String userAgent;
    private final Duration timeout;
    private final boolean ssrfProtectionEnabled;

    public RobotsFetcher(
            WebClient webClient,
            @Value("${crawler.http.user-agent:SearchEngineBot/1.0}") String userAgent,
            @Value("${crawler.robots.timeout:5s}") Duration timeout,
            @Value("${crawler.http.ssrf-protection-enabled:true}") boolean ssrfProtectionEnabled
    ) {
        this.webClient = webClient;
        this.userAgent = userAgent;
        this.timeout = timeout;
        this.ssrfProtectionEnabled = ssrfProtectionEnabled;
    }

    public record RobotsFetchResponse(
            int statusCode,
            byte[] content,
            String contentType,
            boolean success,
            String failureReason
    ) {}

    public Mono<RobotsFetchResponse> fetchRobots(String robotsUrl) {
        if (ssrfProtectionEnabled) {
            String ssrfError = checkSsrf(robotsUrl);
            if (ssrfError != null) {
                LOGGER.warn("ROBOTS_SSRF_BLOCKED url={} reason=\"{}\"", robotsUrl, ssrfError);
                return Mono.just(new RobotsFetchResponse(0, null, null, false, ssrfError));
            }
        }

        return webClient.get()
                .uri(robotsUrl)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .exchangeToMono(response -> {
                    int statusCode = response.statusCode().value();
                    String contentType = response.headers().contentType()
                            .map(MediaType::toString)
                            .orElse("text/plain");

                    if (statusCode == 404) {
                        return Mono.just(new RobotsFetchResponse(404, null, contentType, true, "Robots.txt 404 Not Found"));
                    }
                    if (statusCode == 401 || statusCode == 403) {
                        return Mono.just(new RobotsFetchResponse(statusCode, null, contentType, true, "Robots.txt HTTP " + statusCode));
                    }
                    if (statusCode < 200 || statusCode >= 300) {
                        return Mono.just(new RobotsFetchResponse(statusCode, null, contentType, false, "Robots.txt HTTP " + statusCode));
                    }

                    return response.bodyToMono(byte[].class)
                            .map(bytes -> new RobotsFetchResponse(statusCode, bytes, contentType, true, null))
                            .defaultIfEmpty(new RobotsFetchResponse(statusCode, new byte[0], contentType, true, null));
                })
                .timeout(timeout)
                .onErrorResume(ex -> {
                    String reason = "Robots fetch failed: " + ex.getMessage();
                    if (ex instanceof DataBufferLimitException || (ex.getCause() != null && ex.getCause() instanceof DataBufferLimitException)) {
                        reason = "Robots response body exceeds maximum limit";
                    } else if (ex instanceof TimeoutException || (ex.getCause() != null && ex.getCause() instanceof TimeoutException) || (ex.getMessage() != null && ex.getMessage().contains("Timeout"))) {
                        reason = "Robots HTTP response timeout";
                    }
                    LOGGER.warn("ROBOTS_FETCH_ERROR url={} reason=\"{}\"", robotsUrl, reason);
                    return Mono.just(new RobotsFetchResponse(0, null, null, false, reason));
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
}
