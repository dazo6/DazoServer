package com.dazo66.service;
import com.dazo66.config.DashboardAuthProperties;
import com.dazo66.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
public class DashboardAuthTokenService {
    private final DashboardAuthProperties properties;
    private final DashboardUserService users;
    private final JdbcTemplate jdbc;
    private final SecureRandom random=new SecureRandom();
    public DashboardAuthTokenService(DashboardAuthProperties p,DashboardUserService users,JdbcTemplate jdbc) {
        properties=p;this.users=users;this.jdbc=jdbc;
    }
    public boolean authenticate(String id,String password) {return users.authenticate(id,password);}
    public String createToken(String rawId) {
        String id=DashboardUserService.id(rawId);
        String session=UUID.randomUUID().toString().replace("-","");
        byte[] salt=new byte[32];random.nextBytes(salt);
        String s=Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
        String csrf=sign("csrf:"+session+":"+s);
        jdbc.update("INSERT INTO t_dashboard_user_session(session_id,user_id,token_salt,csrf_token_hash,created_at) VALUES(?,?,?,?,NOW(3))",
            session,id,s,digest(csrf));
        return "v2."+session+"."+sign("v2:"+session+":"+s);
    }
    public CurrentUser resolve(String token) {
        if(token==null) return null;
        String[] p=token.split("\\.");
        if(p.length!=3 || !p[0].equals("v2") || !p[1].matches("[0-9a-f]{32}")) return null;
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT user_id,token_salt FROM t_dashboard_user_session WHERE session_id=?",p[1]);
        if(rows.isEmpty()) return null;
        Map<String,Object> row=rows.get(0);
        if(!constant(sign("v2:"+p[1]+":"+row.get("token_salt")),p[2])) return null;
        return users.context((String)row.get("user_id"),p[1]);
    }
    public boolean isValid(String token) {return resolve(token)!=null;}
    public String csrf(CurrentUser u) {
        String salt=jdbc.queryForObject("SELECT token_salt FROM t_dashboard_user_session WHERE session_id=?",String.class,u.sessionId);
        return sign("csrf:"+u.sessionId+":"+salt);
    }
    public boolean validCsrf(CurrentUser u,String supplied) {return supplied!=null && constant(csrf(u),supplied);}
    public void logout(String session) {jdbc.update("DELETE FROM t_dashboard_user_session WHERE session_id=?",session);}
    public String getCookieName() {return properties.getCookieName();}
    private String sign(String raw) {
        try {
            Mac m=Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(properties.getTokenSalt().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch(GeneralSecurityException e) {throw new IllegalStateException(e);}
    }
    private static boolean constant(String a,String b) {return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private static String digest(String raw) {
        try {
            byte[] h=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();for(byte x:h) out.append(String.format("%02x",x));return out.toString();
        } catch(GeneralSecurityException e) {throw new IllegalStateException(e);}
    }
}
