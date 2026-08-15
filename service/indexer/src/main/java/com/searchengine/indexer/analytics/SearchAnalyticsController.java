package com.searchengine.indexer.analytics;

import com.searchengine.indexer.model.analytics.SearchAnalyticsSnapshot;
import com.searchengine.indexer.model.analytics.ZeroResultsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search/analytics")
@RequiredArgsConstructor
public class SearchAnalyticsController {

    private final SearchAnalyticsService searchAnalyticsService;

    @GetMapping
    public ResponseEntity<SearchAnalyticsSnapshot> getAnalytics() {
        return ResponseEntity.ok(searchAnalyticsService.getSnapshot());
    }

    @GetMapping("/zero-results")
    public ResponseEntity<ZeroResultsResponse> getZeroResults() {
        return ResponseEntity.ok(searchAnalyticsService.getZeroResults());
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> resetAnalytics() {
        searchAnalyticsService.reset();
        return ResponseEntity.noContent().build();
    }
}
