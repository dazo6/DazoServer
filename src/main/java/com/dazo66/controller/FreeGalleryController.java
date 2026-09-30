package com.dazo66.controller;

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
        return ResultEntity.successWithData(service.list(keyword));
    }

    @PostMapping
    public ResultEntity<FreeGallery> create(@RequestBody FreeGalleryRequest request) {
        try {
            return ResultEntity.successWithData(service.create(request));
        } catch (IllegalArgumentException exception) {
            return ResultEntity.failed(exception.getMessage());
        }
    }

    @PutMapping("{id}")
    public ResultEntity<FreeGallery> update(@PathVariable int id,
                                            @RequestBody FreeGalleryRequest request) {
        try {
            return ResultEntity.successWithData(service.update(id, request));
        } catch (IllegalArgumentException exception) {
            return ResultEntity.failed(exception.getMessage());
        }
    }

    @DeleteMapping("{id}")
    public ResultEntity<Boolean> delete(@PathVariable int id) {
        if (!service.delete(id)) {
            return ResultEntity.failed("自由画廊不存在");
        }
        return ResultEntity.successWithData(true);
    }
}
