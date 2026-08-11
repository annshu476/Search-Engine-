package com.searchengine.urlfrontier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.searchengine.urlfrontier.exception.RedisUnavailableException;
import com.searchengine.urlfrontier.repository.VisitedUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UrlFrontierApplicationTests {

    private final MockMvc mockMvc;

    @MockitoBean
    private VisitedUrlRepository visitedUrlRepository;

    UrlFrontierApplicationTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void healthEndpointIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void acceptsValidUrl() throws Exception {
        when(visitedUrlRepository.storeIfAbsent(anyString(), any())).thenReturn(true);

        mockMvc.perform(post("/urls")
                        .contentType(APPLICATION_JSON)
                        .content("{\"url\":\" HTTPS://SPRING.IO/ \"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.originalUrl").value(" HTTPS://SPRING.IO/ "))
                .andExpect(jsonPath("$.normalizedUrl").value("https://spring.io"))
                .andExpect(jsonPath("$.urlHash").value("007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28"))
                .andExpect(jsonPath("$.message").value("URL accepted"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void returnsConflictForDuplicateUrl() throws Exception {
        when(visitedUrlRepository.storeIfAbsent(anyString(), any())).thenReturn(false);

        mockMvc.perform(post("/urls")
                        .contentType(APPLICATION_JSON)
                        .content("{\"url\":\"https://spring.io\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.message").value("URL has already been seen"));
    }

    @Test
    void returnsServiceUnavailableWhenRedisIsUnavailable() throws Exception {
        when(visitedUrlRepository.storeIfAbsent(anyString(), any()))
                .thenThrow(new RedisUnavailableException(null));

        mockMvc.perform(post("/urls")
                        .contentType(APPLICATION_JSON)
                        .content("{\"url\":\"https://spring.io\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Service unavailable"));
    }

    @Test
    void rejectsInvalidUrl() throws Exception {
        mockMvc.perform(post("/urls")
                        .contentType(APPLICATION_JSON)
                        .content("{\"url\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[0]").value("url: url must be a valid HTTP or HTTPS URL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"url\":null}", "{\"url\":\"\"}", "{\"url\":\"   \"}"})
    void rejectsMissingOrBlankUrl(String requestBody) throws Exception {
        mockMvc.perform(post("/urls")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("url: url must not be blank"));
    }
}

