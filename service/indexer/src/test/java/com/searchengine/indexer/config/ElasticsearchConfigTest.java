package com.searchengine.indexer.config;

import com.searchengine.indexer.exception.ElasticsearchConfigurationException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchConfigTest {

    private ElasticsearchConfig elasticsearchConfig;

    @BeforeEach
    void setUp() {
        elasticsearchConfig = new ElasticsearchConfig();
    }

    @Test
    void restClientCreatedSuccessfullyWithDefaultProperties() {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        RestClient restClient = elasticsearchConfig.restClient(properties);
        assertThat(restClient).isNotNull();

        ElasticsearchTransport transport = elasticsearchConfig.elasticsearchTransport(restClient);
        assertThat(transport).isNotNull();

        ElasticsearchClient client = elasticsearchConfig.elasticsearchClient(transport);
        assertThat(client).isNotNull();
    }

    @Test
    void restClientCreatedWithCustomUrlAndCredentials() {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setUrl("http://es-host:9200");
        properties.setUsername("elastic");
        properties.setPassword("secret");
        properties.setConnectTimeoutMs(2000);
        properties.setSocketTimeoutMs(10000);

        RestClient restClient = elasticsearchConfig.restClient(properties);
        assertThat(restClient).isNotNull();
    }

    @Test
    void throwsExceptionWhenUrlIsNull() {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setUrl(null);

        assertThatThrownBy(() -> elasticsearchConfig.restClient(properties))
                .isInstanceOf(ElasticsearchConfigurationException.class)
                .hasMessageContaining("Elasticsearch URL must not be empty or null");
    }

    @Test
    void throwsExceptionWhenUrlIsEmpty() {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setUrl("   ");

        assertThatThrownBy(() -> elasticsearchConfig.restClient(properties))
                .isInstanceOf(ElasticsearchConfigurationException.class)
                .hasMessageContaining("Elasticsearch URL must not be empty or null");
    }

    @Test
    void throwsExceptionWhenUrlIsInvalid() {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setUrl("http:// invalid url :80");

        assertThatThrownBy(() -> elasticsearchConfig.restClient(properties))
                .isInstanceOf(ElasticsearchConfigurationException.class)
                .hasMessageContaining("Invalid Elasticsearch URL configured");
    }
}
