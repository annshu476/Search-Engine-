package com.searchengine.indexer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchengine.indexer.config.SearchProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class SearchSecurityFilter extends OncePerRequestFilter {

    private final SearchProperties searchProperties;
    private final SearchRateLimiter localRateLimiter;
    private final ClientIdentityResolver clientIdentityResolver;
    private final AdminTokenValidator adminTokenValidator;
    private final SearchRequestCostEvaluator costEvaluator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private RedisSearchRateLimiter redisSearchRateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String requestId = MDC.get(RequestCorrelationFilter.MDC_KEY);

        // 1. Admin Endpoint Authorization Check
        if (isAdminEndpoint(path, method)) {
            if (!adminTokenValidator.validateAdminToken(request)) {
                sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "Unauthorized");
                return;
            }
        }

        // 2. Endpoint-Specific Rate Limiting
        String endpointName = getEndpointName(path, method);
        if (endpointName != null) {
            int limit = getEndpointLimit(endpointName);
            String clientKey = clientIdentityResolver.resolveClientKey(request);

            SearchRateLimiter.RateLimitResult result;
            try {
                if (redisSearchRateLimiter != null && searchProperties.getRedis().isEnabled()) {
                    result = redisSearchRateLimiter.checkRateLimit(endpointName, clientKey, limit);
                } else {
                    result = localRateLimiter.checkRateLimit(endpointName, clientKey, limit);
                }
            } catch (RedisSearchRateLimiter.RedisRateLimitUnavailableException e) {
                sendErrorResponse(response, HttpStatus.SERVICE_UNAVAILABLE, "Rate limiting service temporarily unavailable");
                return;
            }

            if (result != null) {
                response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
                response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetUnixTimestamp()));

                if (!result.allowed()) {
                    response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
                    log.warn("SEARCH_RATE_LIMIT_REJECTED requestId={} endpoint={}", requestId, endpointName);

                    Map<String, Object> errorBody = new LinkedHashMap<>();
                    errorBody.put("error", "Too many requests");
                    errorBody.put("retryAfterSeconds", result.retryAfterSeconds());

                    sendJsonResponse(response, HttpStatus.TOO_MANY_REQUESTS, errorBody);
                    return;
                }

                log.info("SEARCH_RATE_LIMIT_ALLOWED requestId={} endpoint={} remaining={}", requestId, endpointName, result.remaining());
            }
        }

        // 3. Search Query Cost Protection for GET /api/search
        if ("/api/search".equals(path) && "GET".equalsIgnoreCase(method)) {
            try {
                String query = request.getParameter("q");
                String language = request.getParameter("language");
                String contentType = request.getParameter("contentType");
                Integer statusCode = parseInteger(request.getParameter("statusCode"));
                String fromDate = request.getParameter("fromDate");
                String toDate = request.getParameter("toDate");
                int page = parseInt(request.getParameter("page"), 0);
                int size = parseInt(request.getParameter("size"), searchProperties.getMaxResults());

                costEvaluator.evaluateCost(query, language, contentType, statusCode, fromDate, toDate, page, size);
            } catch (IllegalArgumentException e) {
                if ("Search request is too expensive".equals(e.getMessage())) {
                    sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Search request is too expensive");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAdminEndpoint(String path, String method) {
        if ("/api/search/analytics/reset".equals(path) && "POST".equalsIgnoreCase(method)) {
            return true;
        }
        if ("/api/search/evaluation/run".equals(path) && "POST".equalsIgnoreCase(method)) {
            return true;
        }
        return false;
    }

    private String getEndpointName(String path, String method) {
        if ("/api/search".equals(path) && "GET".equalsIgnoreCase(method)) {
            return "search";
        }
        if ("/api/search/suggest".equals(path) && "GET".equalsIgnoreCase(method)) {
            return "suggest";
        }
        if (path != null && path.startsWith("/api/search/analytics") && !"POST".equalsIgnoreCase(method)) {
            return "analytics";
        }
        if ("/api/search/evaluation/run".equals(path)) {
            return "evaluation";
        }
        if ("/api/search/analytics/reset".equals(path) && "POST".equalsIgnoreCase(method)) {
            return "analyticsReset";
        }
        return null;
    }

    private int getEndpointLimit(String endpointName) {
        SearchProperties.RateLimit rl = searchProperties.getRateLimit();
        return switch (endpointName) {
            case "search" -> rl.getSearch().getRequestsPerMinute();
            case "suggest" -> rl.getSuggest().getRequestsPerMinute();
            case "analytics" -> rl.getAnalytics().getRequestsPerMinute();
            case "evaluation" -> rl.getEvaluation().getRequestsPerMinute();
            case "analyticsReset" -> rl.getAnalyticsReset().getRequestsPerMinute();
            default -> 60;
        };
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        sendJsonResponse(response, status, Map.of("error", message));
    }

    private void sendJsonResponse(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }

    private Integer parseInteger(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseInt(String val, int defaultVal) {
        if (val == null || val.isBlank()) return defaultVal;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
