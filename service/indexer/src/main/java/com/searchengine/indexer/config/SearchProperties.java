package com.searchengine.indexer.config;

import jakarta.annotation.PostConstruct;
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
    private int maxPageSize = 50;
    private int maxQueryLength = 200;

    private Relevance relevance = new Relevance();

    @Getter
    @Setter
    public static class Relevance {
        private double titleBoost = 4.0;
        private double headingsBoost = 3.0;
        private double metaDescriptionBoost = 2.0;
        private double bodyBoost = 1.0;
    }

    @PostConstruct
    public void validateProperties() {
        if (relevance.getTitleBoost() <= 0) {
            throw new IllegalStateException("Title boost must be greater than 0");
        }
        if (relevance.getHeadingsBoost() <= 0) {
            throw new IllegalStateException("Headings boost must be greater than 0");
        }
        if (relevance.getMetaDescriptionBoost() <= 0) {
            throw new IllegalStateException("Meta description boost must be greater than 0");
        }
        if (relevance.getBodyBoost() <= 0) {
            throw new IllegalStateException("Body boost must be greater than 0");
        }
    }
}
