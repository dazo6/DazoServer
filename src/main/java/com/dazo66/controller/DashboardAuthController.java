package com.dazo66.controller;
import com.dazo66.dashboard.LoginRequest;
import com.dazo66.service.*;
import com.dazo66.security.CurrentUser;
import com.dazo66.util.ResultEntity;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import java.util.*;

@RestController
@RequestMapping("api/auth")
public class DashboardAuthController {
    static final int PERMANENT_COOKIE_MAX_AGE=Integer.MAX_VALUE;
    private final DashboardAuthTokenService tokens;private final DashboardUserService users;
    private final java.util.concurrent.ConcurrentHashMap<String,long[]> failures=new java.util.concurrent.ConcurrentHashMap<>();
    public DashboardAuthController(DashboardAuthTokenService tokens,DashboardUserService users) {this.tokens=tokens;this.users=users;}
    @PostMapping("login")
    public ResponseEntity<ResultEntity<Map<String,Object>>> login(@RequestBody LoginRequest body,HttpServletRequest req,HttpServletResponse res) {
        String key=req.getRemoteAddr();long now=System.currentTimeMillis();
        if(failures.size()>10000) failures.entrySet().removeIf(e->now-e.getValue()[0]>300000);
        long[] f=failures.computeIfAbsent(key,k->new long[]{now,0});
        synchronized(f) {
            if(now-f[0]>300000) {f[0]=now;f[1]=0;}
            if(f[1]>=20) return ResponseEntity.status(429).body(ResultEntity.failed("登录尝试过多，请稍后重试"));
            if(body==null || !tokens.authenticate(body.getUserId(),body.getPassword())) {
                f[1]++;return ResponseEntity.status(401).body(ResultEntity.failed("用户ID或密码错误"));
            }
            failures.remove(key,f);
        }
        String token=tokens.createToken(body.getUserId());
        CurrentUser u=tokens.resolve(token);CurrentUser.set(u);
        cookie(res,req.isSecure(),token,PERMANENT_COOKIE_MAX_AGE);
        Map<String,Object> result=new LinkedHashMap<>();result.put("me",users.me());result.put("csrfToken",tokens.csrf(u));
        return ResponseEntity.ok(ResultEntity.successWithData(result));
    }
    @PostMapping("logout")
    public ResultEntity<Boolean> logout(HttpServletRequest req,HttpServletResponse res) {
        tokens.logout(CurrentUser.require().sessionId);cookie(res,req.isSecure(),"",0);return ResultEntity.successWithData(true);
    }
    @GetMapping("me")
    public ResultEntity<Map<String,Object>> me() {return ResultEntity.successWithData(users.me());}
    @GetMapping("csrf")
    public ResultEntity<String> csrf() {return ResultEntity.successWithData(tokens.csrf(CurrentUser.require()));}
    @PatchMapping("me")
    public ResultEntity<Boolean> profile(@RequestBody Map<String,Object> b) {users.profile(b);return ResultEntity.successWithData(true);}
    @PutMapping("me/password")
    public ResultEntity<Boolean> password(@RequestBody Map<String,Object> b) {users.password(b);return ResultEntity.successWithData(true);}
    @PostMapping("me/logout-all")
    public ResultEntity<Boolean> all() {users.logoutAll(CurrentUser.require().userId);return ResultEntity.successWithData(true);}
    private void cookie(HttpServletResponse r,boolean secure,String token,int age) {
        r.addHeader("Set-Cookie",tokens.getCookieName()+"="+token+"; Path=/; Max-Age="+age+"; HttpOnly; SameSite=Lax"+(secure?"; Secure":""));
    }
}
