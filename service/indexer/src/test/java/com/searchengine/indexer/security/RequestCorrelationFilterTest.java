package com.searchengine.indexer.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestCorrelationFilterTest {

    private MeterRegistry meterRegistry;
    private RequestCorrelationFilter filter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new RequestCorrelationFilter(meterRegistry);
    }

    @Test
    void doFilter_validIncomingRequestId_preservesHeaderAndSetsSecurityHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "custom-id-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isEqualTo("custom-id-12345");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_missingRequestId_generatesUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        String generatedId = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertThat(generatedId).isNotNull();
        assertThat(generatedId).matches("^[a-f0-9-]{36}$");
    }

    @Test
    void doFilter_invalidRequestId_replacesWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "invalid<script>alert(1)</script>");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        String generatedId = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertThat(generatedId).isNotNull();
        assertThat(generatedId).doesNotContain("<script>");
        assertThat(meterRegistry.counter("search.security.invalid_request_id").count()).isEqualTo(1.0);
    }
}
