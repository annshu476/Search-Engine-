package com.searchengine.indexer.config;

import com.searchengine.indexer.exception.ElasticsearchConfigurationException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchConfig {

    @Bean
    public RestClient restClient(ElasticsearchProperties properties) {
        if (properties.getUrl() == null || properties.getUrl().isBlank()) {
            throw new ElasticsearchConfigurationException("Elasticsearch URL must not be empty or null");
        }

        URI uri;
        try {
            uri = URI.create(properties.getUrl());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid URL scheme or host");
            }
        } catch (Exception e) {
            throw new ElasticsearchConfigurationException(
                    "Invalid Elasticsearch URL configured: " + properties.getUrl(), e);
        }

        HttpHost host = new HttpHost(
                uri.getHost(),
                uri.getPort() != -1 ? uri.getPort() : (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80),
                uri.getScheme()
        );

        RestClientBuilder builder = RestClient.builder(host)
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(properties.getConnectTimeoutMs())
                        .setSocketTimeout(properties.getSocketTimeoutMs()));

        if (properties.getUsername() != null && !properties.getUsername().isBlank()
                && properties.getPassword() != null && !properties.getPassword().isBlank()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword())
            );

            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        return builder.build();
    }

    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
