package com.dazo66.controller;

import com.dazo66.config.DashboardAuthProperties;
import com.dazo66.dashboard.LoginRequest;
import com.dazo66.service.DashboardAuthTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardAuthControllerTest {

    @Test
    void successfulLoginWritesHttpOnlyPermanentEncryptedCookie() {
        DashboardAuthProperties properties = new DashboardAuthProperties();
        properties.setUserId("admin");
        properties.setPassword("secret-password");
        properties.setTokenSalt("unit-test-dashboard-auth-salt");
        properties.setCookieName("DAZO_AUTH_TOKEN");
        properties.validate();
        DashboardAuthTokenService tokenService = new DashboardAuthTokenService(properties);
        DashboardAuthController controller = new DashboardAuthController(tokenService);
        LoginRequest login = new LoginRequest();
        login.setUserId("admin");
        login.setPassword("secret-password");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login(login, request, response);

        String cookie = response.getHeader("Set-Cookie");
        assertThat(cookie).contains("DAZO_AUTH_TOKEN=")
                .contains("Max-Age=" + Integer.MAX_VALUE)
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Secure");
        String token = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
        assertThat(tokenService.isValid(token)).isTrue();
    }
}
