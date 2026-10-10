package com.dazo66.controller;
import com.dazo66.security.*;
import com.dazo66.dashboard.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
class DashboardAuthControllerTest {
    @Test void loginWritesPermanentCookieAndReturnsNoAvatar() {
        UserTestSupport s=new UserTestSupport();
        try {
            DashboardAuthController controller=new DashboardAuthController(s.tokens,s.users);
            LoginRequest b=new LoginRequest();b.setUserId("admin");b.setPassword("secret-password");
            MockHttpServletRequest req=new MockHttpServletRequest();req.setSecure(true);
            MockHttpServletResponse out=new MockHttpServletResponse();
            Object data=controller.login(b,req,out).getBody().getData();
            String cookie=out.getHeader("Set-Cookie");
            assertThat(cookie).contains("Max-Age="+Integer.MAX_VALUE,"HttpOnly","SameSite=Lax","Secure");
            assertThat(s.tokens.isValid(cookie.substring(cookie.indexOf('=')+1,cookie.indexOf(';')))).isTrue();
            assertThat(data.toString()).doesNotContain("avatarBase64","password_hash");
        }finally{s.close();}
    }
}
