package org.example.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;

class TenantInterceptorTest {

    private TenantInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new TenantInterceptor();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void preHandle_validHeader_returnsTrueAndSetsTenantContext() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant-a");
        var response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    void preHandle_validHeader_returns200NotAnErrorStatus() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant-a");
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void preHandle_headerWithWhitespace_trimsTenantId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "  tenant-b  ");
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-b");
    }

    @Test
    void preHandle_missingHeader_returnsFalseWith400() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void preHandle_missingHeader_doesNotSetTenantContext() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void preHandle_blankHeader_returnsFalseWith400() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "   ");
        var response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void preHandle_blankHeader_doesNotSetTenantContext() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "   ");
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void afterCompletion_clearsTenantContextToPreventThreadLeak() throws Exception {
        TenantContext.setTenantId("tenant-that-should-be-cleared");

        interceptor.afterCompletion(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new Object(),
                null
        );

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void afterCompletion_withException_stillClearsTenantContext() throws Exception {
        TenantContext.setTenantId("tenant-a");

        interceptor.afterCompletion(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new Object(),
                new RuntimeException("some error")
        );

        assertThat(TenantContext.getTenantId()).isNull();
    }
}
