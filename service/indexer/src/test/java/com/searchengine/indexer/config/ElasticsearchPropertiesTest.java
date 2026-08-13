package com.searchengine.indexer.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchPropertiesTest {

    @Test
    void defaultValuesAreSetCorrectly() {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        assertThat(properties.getUrl()).isEqualTo("http://localhost:9200");
        assertThat(properties.getConnectTimeoutMs()).isEqualTo(5000);
        assertThat(properties.getSocketTimeoutMs()).isEqualTo(30000);
        assertThat(properties.getUsername()).isNull();
        assertThat(properties.getPassword()).isNull();
    }

    @Test
    void settersAndGettersWorkAsExpected() {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setUrl("http://es.internal:9200");
        properties.setUsername("admin");
        properties.setPassword("pass");
        properties.setConnectTimeoutMs(3000);
        properties.setSocketTimeoutMs(15000);

        assertThat(properties.getUrl()).isEqualTo("http://es.internal:9200");
        assertThat(properties.getUsername()).isEqualTo("admin");
        assertThat(properties.getPassword()).isEqualTo("pass");
        assertThat(properties.getConnectTimeoutMs()).isEqualTo(3000);
        assertThat(properties.getSocketTimeoutMs()).isEqualTo(15000);
    }
}
