package com.dazo66.controller;

import com.dazo66.dashboard.LoginRequest;
import com.dazo66.service.DashboardAuthTokenService;
import com.dazo66.util.ResultEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("api/auth")
public class DashboardAuthController {

    static final int PERMANENT_COOKIE_MAX_AGE = Integer.MAX_VALUE;

    private final DashboardAuthTokenService tokenService;

    public DashboardAuthController(DashboardAuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("login")
    public ResponseEntity<ResultEntity<Boolean>> login(@RequestBody LoginRequest request,
                                                       HttpServletRequest servletRequest,
                                                       HttpServletResponse servletResponse) {
        if (request == null || !tokenService.authenticate(request.getUserId(), request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ResultEntity.failed("用户 ID 或密码错误"));
        }
        addCookie(servletResponse, servletRequest.isSecure(), tokenService.createToken(),
                PERMANENT_COOKIE_MAX_AGE);
        return ResponseEntity.ok(ResultEntity.successWithData(true));
    }

    @PostMapping("logout")
    public ResultEntity<Boolean> logout(HttpServletRequest servletRequest,
                                        HttpServletResponse servletResponse) {
        addCookie(servletResponse, servletRequest.isSecure(), "", 0);
        return ResultEntity.successWithData(true);
    }

    private void addCookie(HttpServletResponse response, boolean secure, String value, int maxAge) {
        StringBuilder cookie = new StringBuilder(tokenService.getCookieName())
                .append('=').append(value)
                .append("; Path=/; Max-Age=").append(maxAge)
                .append("; HttpOnly; SameSite=Lax");
        if (secure) {
            cookie.append("; Secure");
        }
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
