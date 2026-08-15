package com.searchengine.indexer.model.search;

import java.util.List;

public record SearchSynonym(
        List<String> terms
) {}
