package com.dazo66.config;

import com.dazo66.service.DashboardAuthTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityFilterTest {

    private final DashboardAuthTokenService tokenService = tokenService();
    private final SecurityFilter filter = new SecurityFilter(tokenService);

    @Test
    void letsTheLoginEndpointThroughWithoutACookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void redirectsUnauthenticatedPagesToLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login.html?redirect=%2Fdashboard%2F");
    }

    @Test
    void returnsJson401ForUnauthenticatedApiRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/fanbox/1/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("请先登录");
    }

    @Test
    void acceptsEncryptedCookieForEveryProtectedMethod() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/fanbox/1/status");
        request.setCookies(new Cookie(tokenService.getCookieName(), tokenService.createToken()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private DashboardAuthTokenService tokenService() {
        DashboardAuthProperties properties = new DashboardAuthProperties();
        properties.setUserId("admin");
        properties.setPassword("secret-password");
        properties.setTokenSalt("unit-test-dashboard-auth-salt");
        properties.setCookieName("DAZO_AUTH_TOKEN");
        properties.validate();
        return new DashboardAuthTokenService(properties);
    }
}
