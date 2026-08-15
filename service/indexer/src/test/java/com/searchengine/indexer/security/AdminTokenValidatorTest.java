package com.searchengine.indexer.security;

import com.searchengine.indexer.config.SearchProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTokenValidatorTest {

    private SearchProperties searchProperties;
    private MeterRegistry meterRegistry;
    private AdminTokenValidator adminTokenValidator;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.getAdmin().setEnabled(true);
        searchProperties.getAdmin().setToken("secret-admin-token");

        meterRegistry = new SimpleMeterRegistry();
        adminTokenValidator = new AdminTokenValidator(searchProperties, meterRegistry);
    }

    @Test
    void validateAdminToken_validToken_returnsTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Admin-Token", "secret-admin-token");

        boolean result = adminTokenValidator.validateAdminToken(request);
        assertThat(result).isTrue();
    }

    @Test
    void validateAdminToken_missingToken_returnsFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        boolean result = adminTokenValidator.validateAdminToken(request);
        assertThat(result).isFalse();
        assertThat(meterRegistry.counter("search.security.unauthorized").count()).isEqualTo(1.0);
    }

    @Test
    void validateAdminToken_invalidToken_returnsFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Admin-Token", "wrong-token");

        boolean result = adminTokenValidator.validateAdminToken(request);
        assertThat(result).isFalse();
    }
}
