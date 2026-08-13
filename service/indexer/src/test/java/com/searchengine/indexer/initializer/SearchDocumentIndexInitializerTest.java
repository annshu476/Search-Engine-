package com.searchengine.indexer.initializer;

import com.searchengine.indexer.config.IndexerElasticsearchProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SearchDocumentIndexInitializerTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    private IndexerElasticsearchProperties properties;
    private SearchDocumentIndexInitializer initializer;

    @BeforeEach
    void setUp() {
        given(elasticsearchClient.indices()).willReturn(indicesClient);
        properties = new IndexerElasticsearchProperties();
        properties.setIndexName("test-search-documents");
        initializer = new SearchDocumentIndexInitializer(elasticsearchClient, properties);
    }

    @Test
    void initializeIndex_createsMissingIndexWhenNotExists() throws IOException {
        BooleanResponse existsResponse = mock(BooleanResponse.class);
        given(existsResponse.value()).willReturn(false);
        given(indicesClient.exists(any(Function.class))).willReturn(existsResponse);

        CreateIndexResponse createResponse = mock(CreateIndexResponse.class);
        given(indicesClient.create(any(Function.class))).willReturn(createResponse);

        assertDoesNotThrow(() -> initializer.initializeIndex());
    }

    @Test
    void initializeIndex_doesNotRecreateWhenIndexAlreadyExists() throws IOException {
        BooleanResponse existsResponse = mock(BooleanResponse.class);
        given(existsResponse.value()).willReturn(true);
        given(indicesClient.exists(any(Function.class))).willReturn(existsResponse);

        assertDoesNotThrow(() -> initializer.initializeIndex());
    }

    @Test
    void initializeIndex_failsClearlyWhenIndexCreationFails() throws IOException {
        BooleanResponse existsResponse = mock(BooleanResponse.class);
        given(existsResponse.value()).willReturn(false);
        given(indicesClient.exists(any(Function.class))).willReturn(existsResponse);
        given(indicesClient.create(any(Function.class))).willThrow(new RuntimeException("Elasticsearch cluster unavailable"));

        assertThatThrownBy(() -> initializer.initializeIndex())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to initialize Elasticsearch index: test-search-documents");
    }
}
