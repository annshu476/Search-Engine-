package com.searchengine.indexer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexer.search")
public class SearchProperties {

    private int maxResults = 10;
    private int maxQueryLength = 200;
}
