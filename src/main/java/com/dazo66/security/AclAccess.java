package com.dazo66.security;
import java.nio.file.*;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public final class AclAccess {
    private AclAccess() {}
    public static boolean can(AclType t,String point) {
        CurrentUser u=CurrentUser.get();
        return u!=null && (u.superAdmin || GlobPermissions.allows(u.permissions,t,point));
    }
    public static void check(AclType t,String p) {
        CurrentUser.require();
        if(!can(t,p)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"没有"+t.name()+"权限");
    }
    public static String path(String raw) {
        try {
            if(raw==null || raw.indexOf('\0')>=0) throw new IllegalArgumentException();
            String plain=raw.replace('\\','/');
            if(plain.startsWith("//") || plain.matches("(?i)^[a-z]:/.*:.*")) throw new IllegalArgumentException();
            Path p=Paths.get(raw).toAbsolutePath().normalize();
            if(Files.exists(p)) return GlobPermissions.canonical(p.toRealPath().toString());
            Path parent=p.getParent();
            if(parent!=null && Files.exists(parent)) p=parent.toRealPath().resolve(p.getFileName());
            return GlobPermissions.canonical(p.toString());
        } catch(IOException | RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件路径无效");
        }
    }
    public static boolean canPath(AclType t,String raw) {
        return raw!=null && can(t,path(raw));
    }
    public static void checkPath(AclType t,String raw) { check(t,path(raw)); }
}
