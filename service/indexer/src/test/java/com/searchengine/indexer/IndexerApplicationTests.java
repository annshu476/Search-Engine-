package com.searchengine.indexer;

import com.searchengine.indexer.health.ElasticsearchHealthIndicator;
import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IndexerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ElasticsearchHealthIndicator elasticsearchHealthIndicator;

    @MockitoBean
    private SearchDocumentIndexInitializer searchDocumentIndexInitializer;

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointReturnsOk() throws Exception {
        given(elasticsearchHealthIndicator.health())
                .willReturn(Health.up().withDetail("elasticsearch", "Mocked UP").build());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheusEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }
}
