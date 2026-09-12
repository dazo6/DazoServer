package com.dazo66.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "dashboard.auth")
public class DashboardAuthProperties {

    private String userId;
    private String password;
    private String tokenSalt;
    private String cookieName = "DAZO_AUTH_TOKEN";

    @PostConstruct
    public void validate() {
        if (StringUtils.isBlank(userId)) {
            throw new IllegalStateException("dashboard.auth.user-id must not be blank");
        }
        if (StringUtils.isBlank(password)) {
            throw new IllegalStateException("dashboard.auth.password must not be blank");
        }
        if (StringUtils.length(tokenSalt) < 16) {
            throw new IllegalStateException("dashboard.auth.token-salt must contain at least 16 characters");
        }
        if (StringUtils.isBlank(cookieName)) {
            throw new IllegalStateException("dashboard.auth.cookie-name must not be blank");
        }
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTokenSalt() {
        return tokenSalt;
    }

    public void setTokenSalt(String tokenSalt) {
        this.tokenSalt = tokenSalt;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }
}
