package com.dazo66.service;

import com.dazo66.config.DashboardAuthProperties;
import com.dazo66.security.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import javax.annotation.PostConstruct;
import java.util.*;

@Service
public class DashboardUserService {
    private static final String COLUMNS="user_id,username,enabled,is_super_admin,permissions,created_at,updated_at";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DashboardAuthProperties auth;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.core.env.Environment environment;
    @Value("${dashboard.acl.default-path:}") private String defaultPath;
    @Value("${dashboard.acl.default-paths:}") private String defaultPaths;
    public DashboardUserService(JdbcTemplate jdbc,ObjectMapper json,DashboardAuthProperties auth) {
        this.jdbc=jdbc;this.json=json;this.auth=auth;
    }
    @PostConstruct
    public void init() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS t_dashboard_user (user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, username VARCHAR(64) NOT NULL,password_hash VARCHAR(255) CHARACTER SET ascii NULL,enabled TINYINT NOT NULL DEFAULT 1,permissions MEDIUMTEXT NOT NULL,avatar_base64 MEDIUMTEXT CHARACTER SET ascii NULL,is_super_admin TINYINT NOT NULL DEFAULT 0,created_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,deleted_at DATETIME(3) NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE IF NOT EXISTS t_dashboard_user_session (session_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,user_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,token_salt VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,csrf_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,created_at DATETIME(3) NOT NULL,KEY idx_session_user(user_id),CONSTRAINT fk_session_user FOREIGN KEY(user_id) REFERENCES t_dashboard_user(user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        String id=id(auth.getUserId());
        List<Map<String,Object>> roots=jdbc.queryForList("SELECT user_id FROM t_dashboard_user WHERE is_super_admin=1");
        if(!roots.isEmpty() && (roots.size()!=1 || !id.equals(roots.get(0).get("user_id"))))
            throw new IllegalStateException("超管配置ID发生变化，需要显式迁移");
        if(roots.isEmpty()) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM t_dashboard_user WHERE user_id=?",Long.class,id)>0)
                throw new IllegalStateException("配置超管ID已被使用");
            jdbc.update("INSERT INTO t_dashboard_user(user_id,username,enabled,permissions,is_super_admin,created_at,updated_at) VALUES(?,?,1,?,1,NOW(3),NOW(3))",
                id,id,encode(Arrays.asList("read:*","update:*","delete:*")));
        }
    }
    public static String id(String value) {
        if(value==null || !value.matches("[A-Za-z0-9_.-]{1,64}")) throw bad("用户ID仅支持1至64位字母数字及._-");
        return value.toLowerCase(Locale.ROOT);
    }
    public Map<String,Object> find(String id) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT "+COLUMNS+" FROM t_dashboard_user WHERE user_id=? AND deleted_at IS NULL",id(id));
        if(rows.isEmpty()) return null;
        Map<String,Object> r=rows.get(0),u=new LinkedHashMap<>();
        u.put("userId",r.get("user_id"));u.put("username",r.get("username"));
        u.put("enabled",((Number)r.get("enabled")).intValue()!=0);
        u.put("isSuperAdmin",((Number)r.get("is_super_admin")).intValue()!=0);
        u.put("permissions",decode((String)r.get("permissions")));
        u.put("createdAt",r.get("created_at"));u.put("updatedAt",r.get("updated_at"));
        return u;
    }
    public CurrentUser context(String userId,String session) {
        Map<String,Object> u=find(userId);
        if(u==null || !Boolean.TRUE.equals(u.get("enabled"))) return null;
        return new CurrentUser(userId,(String)u.get("username"),Boolean.TRUE.equals(u.get("isSuperAdmin")),points(u),session);
    }
    public boolean authenticate(String rawId,String password) {
        try {
            String id=id(rawId);Map<String,Object> u=find(id);
            if(u==null || !Boolean.TRUE.equals(u.get("enabled"))) return false;
            if(Boolean.TRUE.equals(u.get("isSuperAdmin"))) return java.security.MessageDigest.isEqual(
                auth.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                (password==null?"":password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String hash=jdbc.queryForObject("SELECT password_hash FROM t_dashboard_user WHERE user_id=?",String.class,id);
            return PasswordHashes.verify(password,hash);
        } catch(IllegalArgumentException e) {return false;}
    }
    @SuppressWarnings("unchecked")
    public List<String> points(Map<String,Object> u) {return (List<String>)u.get("permissions");}
    public boolean manageable(Map<String,Object> target) {
        CurrentUser a=CurrentUser.require();
        if(target==null || a.userId.equals(target.get("userId")) || Boolean.TRUE.equals(target.get("isSuperAdmin"))) return false;
        return a.superAdmin || GlobPermissions.strictSubset(points(target),a.permissions);
    }
    private Map<String,Object> requireTarget(String id) {
        Map<String,Object> u=find(id);
        if(u==null || !manageable(u)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"用户不存在或不可管理");
        return u;
    }
    public Map<String,Object> dto(Map<String,Object> u) {
        Map<String,Object> result=new LinkedHashMap<>(u),caps=new LinkedHashMap<>();
        boolean manage=manageable(u);
        caps.put("canUpdate",manage && AclAccess.can(AclType.UPDATE,"op:user.update"));
        caps.put("canPermissions",manage && AclAccess.can(AclType.UPDATE,"op:user.permissions"));
        caps.put("canStatus",manage && AclAccess.can(AclType.UPDATE,"op:user.status"));
        caps.put("canPassword",manage && AclAccess.can(AclType.UPDATE,"op:user.password"));
        caps.put("canDelete",manage && AclAccess.can(AclType.DELETE,"op:user.delete"));
        caps.put("canSessions",manage && AclAccess.can(AclType.UPDATE,"op:user.sessions"));
        result.put("capabilities",caps);return result;
    }
    public Map<String,Object> me() {
        CurrentUser a=CurrentUser.require();
        Map<String,Object> u=find(a.userId);
        u.put("permissions",a.permissions);
        u.put("capabilities",Collections.singletonMap("canManageUsers",AclAccess.can(AclType.READ,"op:user.list")));
        return u;
    }
    public Map<String,Object> list(int page,int size,String keyword) {
        CurrentUser a=CurrentUser.require(); List<Map<String,Object>> visible=new ArrayList<>();
        Map<String,Object> root=null;
        for(Map<String,Object> row:jdbc.queryForList("SELECT user_id FROM t_dashboard_user WHERE deleted_at IS NULL ORDER BY created_at,user_id")) {
            Map<String,Object> u=find((String)row.get("user_id"));
            if(Boolean.TRUE.equals(u.get("isSuperAdmin"))) {
                root=new LinkedHashMap<>();root.put("userId",u.get("userId"));root.put("username",u.get("username"));
                root.put("isSuperAdmin",true);root.put("enabled",true);root.put("permissions",Arrays.asList("read:*","update:*","delete:*"));
                root.put("capabilities",Collections.emptyMap());continue;
            }
            if((a.superAdmin || a.userId.equals(u.get("userId")) || manageable(u))
                && (keyword==null || keyword.isEmpty() || (u.get("userId")+" "+u.get("username")).contains(keyword)))
                visible.add(dto(u));
        }
        size=Math.max(1,Math.min(100,size));page=Math.max(1,page);
        int from=(int)Math.min((long)(page-1)*size,visible.size()),to=Math.min(from+size,visible.size());
        Map<String,Object> out=new LinkedHashMap<>();out.put("records",visible.subList(from,to));out.put("total",visible.size());out.put("superAdmin",root);return out;
    }
    public Map<String,Object> detail(String id) {
        Map<String,Object> u=find(id);
        if(u==null || !(CurrentUser.require().userId.equals(u.get("userId")) || manageable(u)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"用户不可见");
        return dto(u);
    }
    public List<String> defaults() {
        List<String> indexed=new ArrayList<>();
        if(environment!=null) for(int i=0;i<200;i++) {
            String p=environment.getProperty("dashboard.acl.default-paths["+i+"]");
            if(p==null) break;indexed.add(p);
        }
        String paths=!indexed.isEmpty()?String.join(",",indexed):((defaultPaths==null || defaultPaths.isEmpty())?defaultPath:defaultPaths);
        if(paths==null) paths="";
        if(paths.trim().isEmpty()) return Collections.emptyList();
        List<String> rules=new ArrayList<>();
        for(String p:paths.split("[,\\n]")) if(!p.trim().isEmpty()) rules.add("read:"+p.trim());
        return GlobPermissions.normalize(rules);
    }
    public List<String> requestPermissions(Map<String,Object> body,boolean creation) {
        Object raw=body.get("permissions"),text=body.get("permissionText");
        if(raw!=null && text!=null) throw bad("permissions与permissionText不能同时提交");
        List<String> rules=new ArrayList<>();
        if(raw!=null) {
            if(!(raw instanceof List)) throw bad("permissions必须是字符串数组");
            for(Object r:(List<?>)raw) {if(!(r instanceof String)) throw bad("权限必须是字符串");rules.add((String)r);}
        } else if(text!=null) {
            if(!(text instanceof String)) throw bad("权限文本无效");
            for(String line:((String)text).split("\\r?\\n")) {
                if(line.trim().isEmpty()) continue;
                if(line.trim().equals("read:default_path")) rules.addAll(defaults());else rules.add(line);
            }
        } else if(creation) rules.addAll(defaults());else throw bad("需要permissions或permissionText");
        List<String> normalized=GlobPermissions.normalize(rules);
        CurrentUser a=CurrentUser.require();
        if(!a.superAdmin && !GlobPermissions.subset(normalized,a.permissions))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"拟授权限超出自己的范围");
        return normalized;
    }
    public Map<String,Object> validate(Map<String,Object> b) {
        Object target=b.get("targetUserId");
        if(target!=null) requireTarget(target.toString());
        List<String> p=requestPermissions(b,true);
        Map<String,Object> out=new LinkedHashMap<>();out.put("valid",true);out.put("normalizedPermissions",p);
        out.put("willLoseManagement",!CurrentUser.require().superAdmin && !GlobPermissions.strictSubset(p,CurrentUser.require().permissions));
        return out;
    }
    @Transactional
    public Map<String,Object> create(Map<String,Object> body) {
        rejectUnknown(body,"userId","username","password","enabled","permissions","permissionText");
        String id=id(string(body,"userId"));List<String> p=requestPermissions(body,true);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM t_dashboard_user WHERE user_id=?",Long.class,id)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"用户ID已存在");
        String hash=PasswordHashes.hash(string(body,"password"));
        jdbc.update("INSERT INTO t_dashboard_user(user_id,username,password_hash,enabled,permissions,created_at,updated_at) VALUES(?,?,?,?,?,NOW(3),NOW(3))",
            id,name(body,id),hash,enabled(body,true)?1:0,encode(p));
        return dto(find(id));
    }
    @Transactional
    public void update(String target,String action,Map<String,Object> body) {
        Map<String,Object> u=requireTarget(target);String id=(String)u.get("userId");
        switch(action) {
            case "profile":
                rejectUnknown(body,"username");
                jdbc.update("UPDATE t_dashboard_user SET username=?,updated_at=NOW(3) WHERE user_id=?",name(body,id),id);break;
            case "permissions":
                rejectUnknown(body,"permissions","permissionText");
                jdbc.update("UPDATE t_dashboard_user SET permissions=?,updated_at=NOW(3) WHERE user_id=?",encode(requestPermissions(body,false)),id);break;
            case "enabled":
                rejectUnknown(body,"enabled");
                boolean on=enabled(body,false);
                jdbc.update("UPDATE t_dashboard_user SET enabled=?,updated_at=NOW(3) WHERE user_id=?",on?1:0,id);
                if(!on) logoutAll(id);break;
            case "password":
                rejectUnknown(body,"newPassword");
                jdbc.update("UPDATE t_dashboard_user SET password_hash=?,updated_at=NOW(3) WHERE user_id=?",PasswordHashes.hash(string(body,"newPassword")),id);break;
            case "delete":
                jdbc.update("UPDATE t_dashboard_user SET deleted_at=NOW(3),updated_at=NOW(3),enabled=0,password_hash=NULL,avatar_base64=NULL,permissions='[]' WHERE user_id=?",id);
                logoutAll(id);break;
            case "sessions": logoutAll(id);break;
            default: throw bad("未知操作");
        }
    }
    public void profile(Map<String,Object> b) {
        rejectUnknown(b,"username");String id=CurrentUser.require().userId;
        jdbc.update("UPDATE t_dashboard_user SET username=?,updated_at=NOW(3) WHERE user_id=?",name(b,id),id);
    }
    public void password(Map<String,Object> b) {
        String id=CurrentUser.require().userId;
        if(CurrentUser.require().superAdmin) throw bad("超管密码请修改配置");
        if(!authenticate(id,string(b,"oldPassword"))) throw bad("旧密码错误");
        jdbc.update("UPDATE t_dashboard_user SET password_hash=?,updated_at=NOW(3) WHERE user_id=?",PasswordHashes.hash(string(b,"newPassword")),id);
    }
    public void avatar(String data) {
        jdbc.update("UPDATE t_dashboard_user SET avatar_base64=?,updated_at=NOW(3) WHERE user_id=?",data,CurrentUser.require().userId);
    }
    public String avatarOf(String id) {
        List<String> r=jdbc.query("SELECT avatar_base64 FROM t_dashboard_user WHERE user_id=? AND deleted_at IS NULL",(rs,n)->rs.getString(1),id(id));
        return r.isEmpty()?null:r.get(0);
    }
    public void checkAvatar(String id) {
        Map<String,Object> u=find(id);
        if(u==null || !(CurrentUser.require().userId.equals(id) || Boolean.TRUE.equals(u.get("isSuperAdmin"))
            || (AclAccess.can(AclType.READ,"op:user.list") && manageable(u))))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"用户不可见");
    }
    public void logoutAll(String id) {jdbc.update("DELETE FROM t_dashboard_user_session WHERE user_id=?",id);}
    public List<String> decode(String value) {
        try { return GlobPermissions.normalize(json.readValue(value,new TypeReference<List<String>>(){})); }
        catch(Exception e) {throw new IllegalStateException("用户权限数据格式无效",e);}
    }
    private String encode(List<String> p) {try{return json.writeValueAsString(p);}catch(Exception e){throw new IllegalStateException(e);}}
    private static String name(Map<String,Object> b,String id) {
        String n=string(b,"username").trim();if(n.isEmpty()) n=id;
        if(n.length()>64) throw bad("用户名不能超过64字符");return n;
    }
    public static String string(Map<String,Object> b,String key) {
        Object v=b.get(key);if(v==null) return "";if(!(v instanceof String)) throw bad(key+"必须是字符串");return (String)v;
    }
    private static boolean enabled(Map<String,Object> b,boolean fallback) {
        Object v=b.get("enabled");if(v==null) {if(fallback)return true;throw bad("需要enabled");}
        if(!(v instanceof Boolean)) throw bad("enabled必须是布尔值");return (Boolean)v;
    }
    private static void rejectUnknown(Map<String,Object> b,String... keys) {
        Set<String> allowed=new HashSet<>(Arrays.asList(keys));
        for(String k:b.keySet()) if(!allowed.contains(k)) throw bad("不支持字段："+k);
    }
    private static ResponseStatusException bad(String msg) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
}
