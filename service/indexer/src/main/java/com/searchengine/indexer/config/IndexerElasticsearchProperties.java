package com.searchengine.indexer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexer.elasticsearch")
public class IndexerElasticsearchProperties {

    private String indexName = "search-documents";
}
