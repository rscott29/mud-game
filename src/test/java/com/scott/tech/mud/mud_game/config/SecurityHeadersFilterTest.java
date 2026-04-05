package com.scott.tech.mud.mud_game.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void addsCommonSecurityHeadersToResponses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'self'");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Permissions-Policy")).isEqualTo("camera=(), microphone=(), geolocation=()");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void addsHstsWhenProxyMarksRequestAsHttps() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Strict-Transport-Security")).isEqualTo("max-age=31536000");
    }

    @Test
    void skipsHstsForPlainHttpRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }

    @Test
    void addsImmutableCacheHeadersToFingerprintAssets() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/main-27CIILOK.js");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Cache-Control")).isEqualTo("public, max-age=31536000, immutable");
    }

    @Test
    void marksAppShellResourcesAsNoCache() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app-init.js");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
    }
}
