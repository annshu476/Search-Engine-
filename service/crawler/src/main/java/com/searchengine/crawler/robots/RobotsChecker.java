package com.searchengine.crawler.robots;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import com.searchengine.crawler.model.dto.RobotsCheckResult;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Service evaluating robots.txt rules for target URLs with in-memory origin caching.
 */
@Component
public class RobotsChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RobotsChecker.class);

    private final RobotsFetcher robotsFetcher;
    private final String userAgent;
    private final Cache<String, OriginPolicy> cache;

    public RobotsChecker(
            RobotsFetcher robotsFetcher,
            @Value("${crawler.http.user-agent:SearchEngineBot/1.0}") String userAgent,
            @Value("${crawler.robots.cache-ttl:1h}") Duration cacheTtl,
            @Value("${crawler.robots.cache-max-size:1000}") long cacheMaxSize
    ) {
        this.robotsFetcher = robotsFetcher;
        this.userAgent = userAgent;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(cacheMaxSize)
                .build();
    }

    private record OriginPolicy(
            String origin,
            BaseRobotRules rules,
            boolean available,
            String failureReason
    ) {}

    public Mono<RobotsCheckResult> check(String urlString) {
        String origin;
        try {
            origin = extractOrigin(urlString);
        } catch (Exception ex) {
            return Mono.just(RobotsCheckResult.unavailable(urlString, "Invalid URL origin: " + ex.getMessage()));
        }

        OriginPolicy cachedPolicy = cache.getIfPresent(origin);
        if (cachedPolicy != null) {
            return Mono.just(evaluate(cachedPolicy, origin, urlString));
        }

        String robotsUrl = origin + "/robots.txt";

        return robotsFetcher.fetchRobots(robotsUrl)
                .map(response -> {
                    if (!response.success()) {
                        LOGGER.warn("ROBOTS_UNAVAILABLE origin={} reason=\"{}\"", origin, response.failureReason());
                        return RobotsCheckResult.unavailable(origin, response.failureReason());
                    }

                    SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
                    BaseRobotRules rules;

                    if (response.statusCode() == 404) {
                        rules = parser.failedFetch(404);
                    } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                        rules = parser.failedFetch(response.statusCode());
                        OriginPolicy policy = new OriginPolicy(origin, rules, true, "Robots.txt HTTP " + response.statusCode());
                        cache.put(origin, policy);
                        return RobotsCheckResult.disallowed(origin, "Robots.txt access forbidden (HTTP " + response.statusCode() + ")");
                    } else {
                        byte[] content = response.content() != null ? response.content() : new byte[0];
                        String contentType = response.contentType() != null ? response.contentType() : "text/plain";
                        String botName = userAgent.contains("/") ? userAgent.substring(0, userAgent.indexOf("/")) : userAgent;
                        List<String> robotNames = List.of(
                                userAgent.toLowerCase(),
                                botName.toLowerCase(),
                                userAgent,
                                botName
                        );
                        rules = parser.parseContent(robotsUrl, content, contentType, robotNames);
                    }

                    OriginPolicy policy = new OriginPolicy(origin, rules, true, null);
                    cache.put(origin, policy);

                    LOGGER.info("ROBOTS_FETCHED origin={} statusCode={}", origin, response.statusCode());
                    return evaluate(policy, origin, urlString);
                });
    }

    private RobotsCheckResult evaluate(OriginPolicy policy, String origin, String urlString) {
        if (!policy.available()) {
            return RobotsCheckResult.unavailable(origin, policy.failureReason());
        }

        BaseRobotRules rules = policy.rules();
        boolean allowed = rules.isAllowed(urlString);
        long delay = rules.getCrawlDelay() == BaseRobotRules.UNSET_CRAWL_DELAY ? -1 : rules.getCrawlDelay();

        if (allowed) {
            String reason = rules.isAllowAll() ? "No robots.txt policy (404)" : "Allowed by robots.txt policy";
            return RobotsCheckResult.allowed(origin, delay, reason);
        } else {
            return RobotsCheckResult.disallowed(origin, "Disallowed by robots.txt policy");
        }
    }

    public String extractOrigin(String urlString) {
        URI uri = URI.create(urlString);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();

        if (scheme == null || host == null) {
            throw new IllegalArgumentException("URL missing scheme or host");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(scheme.toLowerCase()).append("://").append(host.toLowerCase());

        if (port != -1 && !isStandardPort(scheme, port)) {
            sb.append(":").append(port);
        }

        return sb.toString();
    }

    private boolean isStandardPort(String scheme, int port) {
        String lowerScheme = scheme.toLowerCase();
        return ("http".equals(lowerScheme) && port == 80) || ("https".equals(lowerScheme) && port == 443);
    }
}
