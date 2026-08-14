package com.searchengine.indexer.model.dto;

import java.util.List;

public record SearchSuggestionResponse(
    String query,
    List<String> suggestions
) {}
