package com.searchengine.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
@AutoConfigureMockMvc
class CrawlerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void healthEndpointIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void webClientConfigurationLoads() {
        assertThat(applicationContext.containsBean("webClientBuilder")).isTrue();
        assertThat(applicationContext.containsBean("webClient")).isTrue();

        WebClient.Builder builder = applicationContext.getBean(WebClient.Builder.class);
        assertThat(builder).isNotNull();

        WebClient webClient = applicationContext.getBean(WebClient.class);
        assertThat(webClient).isNotNull();
    }
}
