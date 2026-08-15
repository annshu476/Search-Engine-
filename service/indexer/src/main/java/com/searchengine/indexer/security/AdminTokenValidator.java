package com.searchengine.indexer.security;

import com.searchengine.indexer.config.SearchProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTokenValidator {

    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;

    public boolean validateAdminToken(HttpServletRequest request) {
        if (!searchProperties.getAdmin().isEnabled()) {
            return true;
        }

        String headerToken = request.getHeader("X-Admin-Token");
        String configuredToken = searchProperties.getAdmin().getToken();

        if (headerToken == null || headerToken.isBlank() || configuredToken == null || configuredToken.isBlank()) {
            meterRegistry.counter("search.security.unauthorized").increment();
            log.warn("SEARCH_ADMIN_UNAUTHORIZED reason=\"Missing admin token\"");
            return false;
        }

        byte[] headerBytes = headerToken.getBytes(StandardCharsets.UTF_8);
        byte[] configBytes = configuredToken.getBytes(StandardCharsets.UTF_8);

        boolean valid = MessageDigest.isEqual(headerBytes, configBytes);
        if (!valid) {
            meterRegistry.counter("search.security.unauthorized").increment();
            log.warn("SEARCH_ADMIN_UNAUTHORIZED reason=\"Invalid admin token\"");
        }
        return valid;
    }
}
