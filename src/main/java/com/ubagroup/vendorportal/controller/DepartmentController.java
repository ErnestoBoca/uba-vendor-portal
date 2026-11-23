package com.ubagroup.vendorportal.controller;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ubagroup.vendorportal.service.DepartmentService;
import com.ubagroup.vendorportal.service.DepartmentService.DepartmentForm;

/** Settings > Roles: departamentos e perfis Officer/Approver (acesso restrito ao SYS_ADMIN). */
@Controller
public class DepartmentController {

    private final DepartmentService departments;

    public DepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    @GetMapping("/settings/roles")
    public String page(Model model) {
        model.addAttribute("active", "settings-roles");
        model.addAttribute("departments", departments.list());
        model.addAttribute("systemRoles", departments.systemRoles());
        return "settings-roles";
    }

    @PostMapping("/api/departments")
    @ResponseBody
    public Map<String, Object> create(@RequestBody DepartmentForm form) {
        return Map.of("id", departments.create(form));
    }

    @PutMapping("/api/departments/{id}")
    @ResponseBody
    public Map<String, Object> update(@PathVariable int id, @RequestBody DepartmentForm form) {
        departments.update(id, form);
        return Map.of("ok", true);
    }
}
