package com.searchengine.indexer.controller;

import com.searchengine.indexer.exception.SearchQueryException;
import com.searchengine.indexer.exception.SearchSuggestionException;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.dto.SearchSuggestionResponse;
import com.searchengine.indexer.service.SearchService;
import com.searchengine.indexer.service.SearchSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final SearchSuggestionService searchSuggestionService;

    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "language", required = false) String language,
            @RequestParam(name = "contentType", required = false) String contentType,
            @RequestParam(name = "statusCode", required = false) Integer statusCode,
            @RequestParam(name = "fromDate", required = false) String fromDate,
            @RequestParam(name = "toDate", required = false) String toDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "sort", defaultValue = "relevance") String sort
    ) {
        SearchResponse response = searchService.search(query, language, contentType, statusCode, fromDate, toDate, page, size, sort);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggest")
    public ResponseEntity<SearchSuggestionResponse> suggest(
            @RequestParam(name = "q", required = false) String query
    ) {
        SearchSuggestionResponse response = searchSuggestionService.suggest(query);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Search request validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SearchQueryException.class)
    public ResponseEntity<Map<String, String>> handleSearchQueryException(SearchQueryException ex) {
        log.error("Search request execution failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Search service temporarily unavailable"));
    }

    @ExceptionHandler(SearchSuggestionException.class)
    public ResponseEntity<Map<String, String>> handleSearchSuggestionException(SearchSuggestionException ex) {
        log.error("Search suggestion execution failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Search suggestion service temporarily unavailable"));
    }
}
