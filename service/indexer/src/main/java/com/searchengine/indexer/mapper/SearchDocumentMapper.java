package com.searchengine.indexer.mapper;

import com.searchengine.indexer.model.kafka.SearchDocument;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SearchDocumentMapper {

    public Map<String, Object> toMap(SearchDocument document) {
        if (document == null) {
            return null;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("url", document.url());
        map.put("canonicalUrl", document.canonicalUrl());
        map.put("urlHash", document.urlHash());
        map.put("title", document.title());
        map.put("metaDescription", document.metaDescription());
        map.put("headings", document.headings());
        map.put("bodyText", document.bodyText());
        map.put("language", document.language());
        map.put("wordCount", document.wordCount());
        map.put("statusCode", document.statusCode());
        map.put("contentType", document.contentType());
        map.put("fetchedAt", document.fetchedAt() != null ? document.fetchedAt().toString() : null);
        map.put("indexedAt", document.indexedAt() != null ? document.indexedAt().toString() : null);
        return map;
    }
}
