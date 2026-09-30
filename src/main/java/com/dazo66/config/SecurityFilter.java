package com.dazo66.config;

import com.dazo66.service.DashboardAuthTokenService;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class SecurityFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = new HashSet<>(Arrays.asList(
            "/login.html", "/api/auth/login", "/error", "/favicon.ico", "/bot/napcat"
    ));

    private final DashboardAuthTokenService tokenService;

    public SecurityFilter(DashboardAuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || PUBLIC_PATHS.contains(path)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        if (tokenService.isValid(readToken(request))) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        if (isHtmlPageRequest(request)) {
            String target = "/".equals(path) ? "/dashboard/" : path;
            if (request.getQueryString() != null) {
                target += "?" + request.getQueryString();
            }
            response.sendRedirect("/login.html?redirect="
                    + URLEncoder.encode(target, StandardCharsets.UTF_8.name()));
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"result\":\"FAILED\",\"message\":\"请先登录\",\"data\":null}");
    }

    private String readToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (tokenService.getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private boolean isHtmlPageRequest(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod()) || request.getRequestURI().startsWith("/api/")) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return accept == null || accept.contains("text/html");
    }
}
