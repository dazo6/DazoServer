package com.dazo66.controller;

import com.dazo66.security.*;
import com.dazo66.dashboard.FreeGalleryRequest;
import com.dazo66.entity.FreeGallery;
import com.dazo66.service.FreeGalleryService;
import com.dazo66.util.ResultEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/free-galleries")
public class FreeGalleryController {

    private final FreeGalleryService service;

    public FreeGalleryController(FreeGalleryService service) {
        this.service = service;
    }

    @GetMapping
    public ResultEntity<List<FreeGallery>> list(@RequestParam(required = false) String keyword) {
        CurrentUser.require();
        return ResultEntity.successWithData(service.list(keyword));
    }

    @PostMapping
    @ACL(type=AclType.UPDATE,point="op:freeGallery.create")
    public ResultEntity<FreeGallery> create(@RequestBody FreeGalleryRequest request) {
        try {
            AclAccess.checkPath(AclType.UPDATE,request.getFolderPath());
            return ResultEntity.successWithData(service.create(request));
        } catch (IllegalArgumentException exception) {
            return ResultEntity.failed(exception.getMessage());
        }
    }

    @PutMapping("{id}")
    @ACL(type=AclType.UPDATE,point="op:freeGallery.update")
    public ResultEntity<FreeGallery> update(@PathVariable int id,
                                            @RequestBody FreeGalleryRequest request) {
        try {
            checkExisting(id,AclType.UPDATE);
            AclAccess.checkPath(AclType.UPDATE,request.getFolderPath());
            return ResultEntity.successWithData(service.update(id, request));
        } catch (IllegalArgumentException exception) {
            return ResultEntity.failed(exception.getMessage());
        }
    }

    private void checkExisting(int id,AclType t) {
        FreeGallery g=service.getById(id);
        if(g==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"自由画廊不存在");
        AclAccess.checkPath(t,g.getFolderPath());
    }

    @DeleteMapping("{id}")
    @ACL(type=AclType.DELETE,point="op:freeGallery.delete")
    public ResultEntity<Boolean> delete(@PathVariable int id) {
        checkExisting(id,AclType.DELETE);
        if (!service.delete(id)) {
            return ResultEntity.failed("自由画廊不存在");
        }
        return ResultEntity.successWithData(true);
    }
}
