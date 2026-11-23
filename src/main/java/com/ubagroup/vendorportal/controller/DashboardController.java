package com.ubagroup.vendorportal.controller;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ubagroup.vendorportal.service.DashboardService;

@Controller
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/")
    public String page(Model model) {
        model.addAttribute("active", "dashboard");
        return "dashboard";
    }

    @GetMapping("/api/dashboard")
    @ResponseBody
    public Map<String, Object> stats() {
        return dashboard.stats();
    }
}
