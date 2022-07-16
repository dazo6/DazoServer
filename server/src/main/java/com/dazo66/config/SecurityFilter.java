package com.dazo66.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class SecurityFilter implements Filter {

    @Value("#{'${allow.config.remote.addr}'.split(',')}")
    private List<String> whiteAddr;
    @Value("#{'${allow.access.uri}'.split(',')}")
    private List<String> uris;


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        if (isAddrInWhiteList(uris, "/", httpServletRequest.getRequestURI())) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else if (isAddrInWhiteList(whiteAddr, ".", httpServletRequest.getRemoteAddr())) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            log.error("非白名单用户不许配置：{}, {}", httpServletRequest.getRequestURI(),
                    httpServletRequest.getRemoteAddr());
            throw new RuntimeException("权限不足");
        }
    }

    private boolean isAddrInWhiteList(List<String> matchList, String splitStr, String remoteAddr) {
        AntPathMatcher antPathMatcher = new AntPathMatcher(splitStr);
        for (String s : matchList) {
            boolean match = antPathMatcher.match(s, remoteAddr);
            if (match) {
                return true;
            }
        }
        return false;
    }

}
