package com.ubagroup.vendorportal.controller;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.service.ApprovalService;

@Controller
public class ApprovalController {

    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping("/approvals")
    public String inbox(@RequestParam(defaultValue = "mine") String tab, Model model) {
        if (!tab.equals("mine") && !tab.equals("all") && !tab.equals("decided")) {
            tab = "mine";
        }
        model.addAttribute("active", "approvals");
        model.addAttribute("tab", tab);
        model.addAttribute("tasks", approvals.inbox(tab));
        model.addAttribute("users", approvals.activeUsers());
        return "approvals";
    }

    /** body: {"decision": "APPROVE|REJECT|RETURN", "comment": "..."} */
    @PostMapping("/api/approvals/tasks/{id}/decide")
    @ResponseBody
    public Map<String, Object> decide(@PathVariable long id, @RequestBody Map<String, String> body) {
        approvals.decide(id, body.get("decision"), body.get("comment"));
        return Map.of("ok", true);
    }

    /** body: {"toUserId": 2, "reason": "..."} */
    @PostMapping("/api/approvals/tasks/{id}/reassign")
    @ResponseBody
    public Map<String, Object> reassign(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Object to = body.get("toUserId");
        if (to == null || String.valueOf(to).isBlank()) {
            throw new ApiException("Seleccione a nova pessoa.");
        }
        approvals.reassign(id, Long.parseLong(String.valueOf(to)), (String) body.get("reason"));
        return Map.of("ok", true);
    }
}
