package com.ubagroup.vendorportal.controller;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ubagroup.vendorportal.service.ApprovalService;
import com.ubagroup.vendorportal.service.FlowService;
import com.ubagroup.vendorportal.service.FlowService.StepForm;

/** Settings > Approval Flows. */
@Controller
public class FlowController {

    private final FlowService flows;
    private final ApprovalService approvals;

    public FlowController(FlowService flows, ApprovalService approvals) {
        this.flows = flows;
        this.approvals = approvals;
    }

    @GetMapping("/settings/flows")
    public String page(@RequestParam(defaultValue = "0") long scope, Model model) {
        model.addAttribute("active", "settings-flows");
        model.addAttribute("scopes", flows.scopes());
        model.addAttribute("scope", scope);
        model.addAttribute("scopeName", flows.scopeName(scope));
        model.addAttribute("steps", flows.steps(scope));
        model.addAttribute("users", approvals.activeUsers());
        return "settings-flows";
    }

    @PutMapping("/api/flows/{scope}")
    @ResponseBody
    public Map<String, Object> save(@PathVariable long scope, @RequestBody List<StepForm> steps) {
        flows.save(scope, steps);
        return Map.of("ok", true);
    }

    @DeleteMapping("/api/flows/{scope}")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable long scope) {
        flows.removeCustom(scope);
        return Map.of("ok", true);
    }
}
