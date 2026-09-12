package com.dazo66.service;

import com.dazo66.config.DashboardAuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardAuthTokenServiceTest {

    @Test
    void authenticatesConfiguredCredentialsAndEncryptsPermanentToken() {
        DashboardAuthTokenService service = service("secret-password");

        assertThat(service.authenticate("admin", "secret-password")).isTrue();
        assertThat(service.authenticate("admin", "wrong-password")).isFalse();

        String first = service.createToken();
        String second = service.createToken();
        assertThat(first).isNotEqualTo(second);
        assertThat(service.isValid(first)).isTrue();
        assertThat(service.isValid(second)).isTrue();
    }

    @Test
    void rejectsTamperedTokenAndTokenEncryptedWithAnotherPassword() {
        DashboardAuthTokenService service = service("secret-password");
        String token = service.createToken();
        int tamperIndex = token.length() / 2;
        char tamperValue = token.charAt(tamperIndex) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, tamperIndex) + tamperValue
                + token.substring(tamperIndex + 1);

        assertThat(service.isValid(tampered)).isFalse();
        assertThat(service("different-password").isValid(token)).isFalse();
    }

    static DashboardAuthTokenService service(String password) {
        DashboardAuthProperties properties = new DashboardAuthProperties();
        properties.setUserId("admin");
        properties.setPassword(password);
        properties.setTokenSalt("unit-test-dashboard-auth-salt");
        properties.setCookieName("DAZO_AUTH_TOKEN");
        properties.validate();
        return new DashboardAuthTokenService(properties);
    }
}
