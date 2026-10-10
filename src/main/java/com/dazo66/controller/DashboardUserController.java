package com.dazo66.controller;
import com.dazo66.security.*;
import com.dazo66.service.DashboardUserService;
import com.dazo66.util.ResultEntity;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class DashboardUserController {
    private final DashboardUserService users;
    public DashboardUserController(DashboardUserService users) {this.users=users;}
    @GetMapping("/api/users") @ACL(type=AclType.READ,point="op:user.list")
    public ResultEntity<Map<String,Object>> list(@RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="20") int size,@RequestParam(required=false) String keyword) {
        return ResultEntity.successWithData(users.list(page,size,keyword));
    }
    @GetMapping("/api/users/{id}") @ACL(type=AclType.READ,point="op:user.list")
    public ResultEntity<Map<String,Object>> detail(@PathVariable String id) {return ResultEntity.successWithData(users.detail(id));}
    @PostMapping("/api/users") @ACL(type=AclType.UPDATE,point="op:user.create")
    public ResponseEntity<ResultEntity<Map<String,Object>>> create(@RequestBody Map<String,Object> b) {
        return ResponseEntity.status(201).body(ResultEntity.successWithData(users.create(b)));
    }
    @PatchMapping("/api/users/{id}") @ACL(type=AclType.UPDATE,point="op:user.update")
    public ResultEntity<Boolean> profile(@PathVariable String id,@RequestBody Map<String,Object> b) {return update(id,"profile",b);}
    @PutMapping("/api/users/{id}/enabled") @ACL(type=AclType.UPDATE,point="op:user.status")
    public ResultEntity<Boolean> enabled(@PathVariable String id,@RequestBody Map<String,Object> b) {return update(id,"enabled",b);}
    @PutMapping("/api/users/{id}/permissions") @ACL(type=AclType.UPDATE,point="op:user.permissions")
    public ResultEntity<Boolean> permissions(@PathVariable String id,@RequestBody Map<String,Object> b) {return update(id,"permissions",b);}
    @PutMapping("/api/users/{id}/password") @ACL(type=AclType.UPDATE,point="op:user.password")
    public ResultEntity<Boolean> password(@PathVariable String id,@RequestBody Map<String,Object> b) {return update(id,"password",b);}
    @DeleteMapping("/api/users/{id}") @ACL(type=AclType.DELETE,point="op:user.delete")
    public ResultEntity<Boolean> delete(@PathVariable String id) {return update(id,"delete",Collections.emptyMap());}
    @PostMapping("/api/users/{id}/logout-all") @ACL(type=AclType.UPDATE,point="op:user.sessions")
    public ResultEntity<Boolean> sessions(@PathVariable String id) {return update(id,"sessions",Collections.emptyMap());}
    private ResultEntity<Boolean> update(String id,String action,Map<String,Object> b) {users.update(id,action,b);return ResultEntity.successWithData(true);}
    @GetMapping("/api/acl/defaults")
    public ResultEntity<Map<String,Object>> defaults() {
        CurrentUser.require();Map<String,Object> d=new LinkedHashMap<>();
        d.put("templateText","read:default_path");d.put("resolvedPermissions",users.defaults());
        d.put("administrator",Arrays.asList("read:*","update:*","delete:*"));return ResultEntity.successWithData(d);
    }
    @GetMapping("/api/acl/catalog")
    public ResultEntity<List<String>> catalog() {
        CurrentUser.require();
        return ResultEntity.successWithData(Arrays.asList("op:user.list","op:user.create","op:user.update","op:user.status","op:user.password",
            "op:user.permissions","op:user.delete","op:user.sessions","op:artist.create","op:artist.status","op:artist.compression",
            "op:freeGallery.create","op:freeGallery.update","op:freeGallery.delete","op:crawlHistory.list","op:crawlHistory.clean","op:dashboard.view"));
    }
    @PostMapping("/api/acl/validate")
    public ResultEntity<Map<String,Object>> validate(@RequestBody Map<String,Object> b) {return ResultEntity.successWithData(users.validate(b));}
}
