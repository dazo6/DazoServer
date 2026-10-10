package com.dazo66.config;
import com.dazo66.security.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import javax.servlet.http.Cookie;
import static org.assertj.core.api.Assertions.*;
class SecurityFilterTest {
    UserTestSupport s;SecurityFilter filter;
    @BeforeEach void setup(){s=new UserTestSupport();filter=new SecurityFilter(s.tokens);CurrentUser.clear();}
    @AfterEach void done(){s.close();}
    @Test void allowsPublicLoginAndAnonymousIcon() throws Exception {
        for(String path:new String[]{"/api/auth/login","/favicon.ico"}) {
            MockHttpServletRequest r=new MockHttpServletRequest(path.contains("login")?"POST":"GET",path);
            MockFilterChain chain=new MockFilterChain();filter.doFilter(r,new MockHttpServletResponse(),chain);
            assertThat(chain.getRequest()).isSameAs(r);
        }
    }
    @Test void protectsPagesAndApi() throws Exception {
        MockHttpServletRequest r=new MockHttpServletRequest("GET","/dashboard/");
        MockHttpServletResponse out=new MockHttpServletResponse();filter.doFilter(r,out,new MockFilterChain());
        assertThat(out.getStatus()).isEqualTo(302);
        r=new MockHttpServletRequest("POST","/api/fanbox/1/status");out=new MockHttpServletResponse();filter.doFilter(r,out,new MockFilterChain());
        assertThat(out.getStatus()).isEqualTo(401);
    }
    @Test void enforcesCsrfAndClearsThreadContext() throws Exception {
        String token=s.tokens.createToken("admin");
        MockHttpServletRequest r=new MockHttpServletRequest("POST","/api/users");
        r.setCookies(new Cookie(s.tokens.getCookieName(),token));
        MockHttpServletResponse out=new MockHttpServletResponse();filter.doFilter(r,out,new MockFilterChain());
        assertThat(out.getStatus()).isEqualTo(403);
        r.addHeader("X-CSRF-Token",s.tokens.csrf(s.tokens.resolve(token)));
        MockFilterChain chain=new MockFilterChain();out=new MockHttpServletResponse();filter.doFilter(r,out,chain);
        assertThat(chain.getRequest()).isSameAs(r);assertThat(CurrentUser.get()).isNull();
        r.addHeader("Origin","https://cboard.dazo66.com");r.addHeader("Sec-Fetch-Site","cross-site");
        out=new MockHttpServletResponse();chain=new MockFilterChain();filter.doFilter(r,out,chain);
        assertThat(chain.getRequest()).isSameAs(r);assertThat(out.getStatus()).isEqualTo(200);
    }
}
