package com.dazo66.controller;

import com.dazo66.dashboard.DashboardData;
import com.dazo66.service.DashboardService;
import com.dazo66.util.ResultEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"/dashboard", "/"})
    public String dashboard() {
        return "forward:/dashboard/index.html";
    }

    @ResponseBody
    @GetMapping("/api/dashboard")
    public ResultEntity<DashboardData> data() {
        return ResultEntity.successWithData(dashboardService.getDashboardData());
    }
}
