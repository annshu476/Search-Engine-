package com.searchengine.indexer.health;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ElasticsearchHealthIndicatorTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    private ElasticsearchHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new ElasticsearchHealthIndicator(elasticsearchClient);
    }

    @Test
    void reportsUpWhenPingReturnsTrue() throws IOException {
        BooleanResponse booleanResponse = mock(BooleanResponse.class);
        given(booleanResponse.value()).willReturn(true);
        given(elasticsearchClient.ping()).willReturn(booleanResponse);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("elasticsearch", "Available");
    }

    @Test
    void reportsDownWhenPingReturnsFalse() throws IOException {
        BooleanResponse booleanResponse = mock(BooleanResponse.class);
        given(booleanResponse.value()).willReturn(false);
        given(elasticsearchClient.ping()).willReturn(booleanResponse);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("elasticsearch", "Ping returned false");
    }

    @Test
    void reportsDownWhenPingThrowsException() throws IOException {
        given(elasticsearchClient.ping()).willThrow(new RuntimeException("Connection refused"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("elasticsearch", "Unavailable");
        assertThat(health.getDetails()).containsEntry("error", "Connection refused");
    }
}
