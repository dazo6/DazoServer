package com.dazo66.security;
import java.util.*;
public final class CurrentUser {
    private static final ThreadLocal<CurrentUser> LOCAL = new ThreadLocal<>();
    public final String userId, username, sessionId;
    public final boolean superAdmin;
    public final List<String> permissions;
    public CurrentUser(String id, String name, boolean root, List<String> points, String session) {
        userId=id; username=name; superAdmin=root;
        permissions=Collections.unmodifiableList(new ArrayList<>(points)); sessionId=session;
    }
    public static CurrentUser get() { return LOCAL.get(); }
    public static CurrentUser require() {
        CurrentUser u=get();
        if(u==null) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.UNAUTHORIZED,"请先登录");
        return u;
    }
    public static void set(CurrentUser u) { LOCAL.set(u); }
    public static void clear() { LOCAL.remove(); }
}
