package com.dazo66.controller;

import com.dazo66.security.CurrentUser;
import com.dazo66.service.ScheduledTaskRegistry;
import com.dazo66.util.ResultEntity;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/scheduled-tasks")
public class ScheduledTaskController {
    private final ScheduledTaskRegistry registry;
    public ScheduledTaskController(ScheduledTaskRegistry registry) { this.registry=registry; }
    private void requireSuperAdmin() {
        if (!CurrentUser.require().superAdmin) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超管可访问");
    }
    @GetMapping public ResultEntity<List<ScheduledTaskRegistry.TaskView>> list() {
        requireSuperAdmin(); return ResultEntity.successWithData(registry.list());
    }
    @PostMapping("/{id}/run") public ResponseEntity<ResultEntity<Void>> run(@PathVariable String id) {
        requireSuperAdmin(); registry.runManually(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResultEntity.successWithoutData());
    }
}
