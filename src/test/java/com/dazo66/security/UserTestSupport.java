package com.dazo66.security;
import com.dazo66.service.*;
import com.dazo66.config.DashboardAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.*;
public class UserTestSupport {
    public final JdbcTemplate jdbc;
    public final DashboardAuthProperties properties;
    public final DashboardUserService users;
    public final DashboardAuthTokenService tokens;
    public UserTestSupport() {
        jdbc=new JdbcTemplate(new SingleConnectionDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","",true));
        jdbc.execute("CREATE TABLE t_dashboard_user(user_id VARCHAR(64) PRIMARY KEY,username VARCHAR(64),password_hash VARCHAR(255),enabled INT,permissions CLOB,avatar_base64 CLOB,is_super_admin INT DEFAULT 0,created_at TIMESTAMP,updated_at TIMESTAMP,deleted_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE t_dashboard_user_session(session_id VARCHAR(32) PRIMARY KEY,user_id VARCHAR(64),token_salt VARCHAR(64),csrf_token_hash VARCHAR(64),created_at TIMESTAMP)");
        properties=new DashboardAuthProperties();properties.setUserId("admin");properties.setPassword("secret-password");
        properties.setTokenSalt("unit-test-signing-key-32-characters");properties.validate();
        users=new DashboardUserService(jdbc,new ObjectMapper(),properties);
        jdbc.update("INSERT INTO t_dashboard_user(user_id,username,enabled,permissions,is_super_admin,created_at,updated_at) VALUES('admin','admin',1,?,1,NOW(),NOW())","[\"read:*\",\"update:*\",\"delete:*\"]");
        tokens=new DashboardAuthTokenService(properties,users,jdbc);
        root();
    }
    public void root() {CurrentUser.set(users.context("admin",null));}
    public void close() {CurrentUser.clear();}
    public Map<String,Object> create(String id,String...rules) {
        root();Map<String,Object> b=new LinkedHashMap<>();
        b.put("userId",id);b.put("username","");b.put("password","a-long-test-password");b.put("permissions",Arrays.asList(rules));return users.create(b);
    }
}
