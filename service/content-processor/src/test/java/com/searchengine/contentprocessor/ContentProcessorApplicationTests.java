package com.searchengine.contentprocessor;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "management.health.kafka.enabled=false"
})
class ContentProcessorApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired(required = false)
    private NewTopic searchDocumentTopic;

    @Test
    void contextLoads() {
        assertThat(searchDocumentTopic).isNotNull();
        assertThat(searchDocumentTopic.name()).isEqualTo("search-document-topic");
        assertThat(searchDocumentTopic.numPartitions()).isEqualTo(1);
        assertThat(searchDocumentTopic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void actuatorHealthReturnsStatusUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
