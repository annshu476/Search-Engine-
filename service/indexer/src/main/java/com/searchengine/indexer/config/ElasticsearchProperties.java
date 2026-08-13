package com.searchengine.indexer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {

    private String url = "http://localhost:9200";
    private String username;
    private String password;
    private int connectTimeoutMs = 5000;
    private int socketTimeoutMs = 30000;
}
