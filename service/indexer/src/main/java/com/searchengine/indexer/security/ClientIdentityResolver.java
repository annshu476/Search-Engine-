package com.searchengine.indexer.security;

import com.searchengine.indexer.analytics.SearchAnalyticsService;
import com.searchengine.indexer.config.SearchProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientIdentityResolver {

    private final SearchProperties searchProperties;

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        if (searchProperties.getRateLimit().isTrustForwardedHeaders()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] ips = xff.split(",");
                if (ips.length > 0 && !ips[0].isBlank()) {
                    return ips[0].trim();
                }
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "unknown";
    }

    public String resolveClientKey(HttpServletRequest request) {
        String rawIp = resolveClientIp(request);
        return SearchAnalyticsService.computeQueryHash("client_ip:" + rawIp);
    }
}
