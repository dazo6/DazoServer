package com.dazo66.config;
import com.dazo66.service.DashboardAuthTokenService;
import com.dazo66.security.CurrentUser;
import org.springframework.stereotype.Component;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public class SecurityFilter implements Filter {
    private static final Set<String> PUBLIC_PATHS=new HashSet<>(Arrays.asList(
        "/login.html","/api/auth/login","/error","/favicon.ico","/bot/napcat"));
    private final DashboardAuthTokenService tokens;
    public SecurityFilter(DashboardAuthTokenService tokens) {this.tokens=tokens;}
    @Override public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain) throws IOException,ServletException {
        HttpServletRequest r=(HttpServletRequest)req;HttpServletResponse out=(HttpServletResponse)res;
        CurrentUser.clear();
        try {
            String path=r.getRequestURI();
            if("/bot/napcat".equals(path) || "OPTIONS".equals(r.getMethod())) {chain.doFilter(req,res);return;}
            CurrentUser u=tokens.resolve(readToken(r));
            CurrentUser.set(u);
            if(!PUBLIC_PATHS.contains(path) && u==null) {
                if("GET".equals(r.getMethod()) && !path.startsWith("/api/") &&
                    (r.getHeader("Accept")==null || r.getHeader("Accept").contains("text/html"))) {
                    String target="/".equals(path)?"/dashboard/":path;
                    if(r.getQueryString()!=null) target+="?"+r.getQueryString();
                    out.sendRedirect("/login.html?redirect="+URLEncoder.encode(target,"UTF-8"));return;
                }
                error(out,401,"请先登录");return;
            }
            if(!Arrays.asList("GET","HEAD","OPTIONS").contains(r.getMethod()) && !"/error".equals(path)) {
                if(!"/api/auth/login".equals(path) && (u==null || !tokens.validCsrf(u,r.getHeader("X-CSRF-Token")))) {
                    error(out,403,"请刷新页面后重试");return;
                }
            }
            out.setHeader("Cache-Control","private, no-store");
            chain.doFilter(req,res);
        } finally {CurrentUser.clear();}
    }
    private String readToken(HttpServletRequest r) {
        if(r.getCookies()!=null) for(Cookie c:r.getCookies()) if(c.getName().equals(tokens.getCookieName())) return c.getValue();
        return null;
    }
    private void error(HttpServletResponse out,int code,String msg) throws IOException {
        out.setStatus(code);out.setCharacterEncoding(StandardCharsets.UTF_8.name());out.setContentType("application/json");
        out.getWriter().write("{\"result\":\"FAILED\",\"message\":\""+msg+"\",\"data\":null}");
    }
}
