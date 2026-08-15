package com.searchengine.indexer.analytics;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.analytics.SearchAnalyticsSummary;
import com.searchengine.indexer.model.analytics.SearchQueryStats;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search/analytics")
@RequiredArgsConstructor
public class SearchAnalyticsController {

    private final SearchAnalyticsService analyticsService;
    private final SearchProperties searchProperties;

    @GetMapping("/summary")
    public ResponseEntity<SearchAnalyticsSummary> getSummary() {
        if (!searchProperties.getAnalytics().isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        SearchAnalyticsSummary summary = analyticsService.getSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/top-queries")
    public ResponseEntity<Map<String, Object>> getTopQueries() {
        if (!searchProperties.getAnalytics().isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        List<SearchQueryStats> queries = analyticsService.getTopQueries();
        return ResponseEntity.ok(Map.of("queries", queries));
    }

    @GetMapping("/zero-results")
    public ResponseEntity<Map<String, Object>> getZeroResults() {
        if (!searchProperties.getAnalytics().isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        List<SearchQueryStats> queries = analyticsService.getZeroResultQueries();
        return ResponseEntity.ok(Map.of("queries", queries));
    }

    @GetMapping
    public ResponseEntity<SearchAnalyticsSummary> getAnalyticsSnapshot() {
        return getSummary();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> resetAnalytics() {
        analyticsService.reset();
        return ResponseEntity.noContent().build();
    }
}
