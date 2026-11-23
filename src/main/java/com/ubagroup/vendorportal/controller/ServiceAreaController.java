package com.ubagroup.vendorportal.controller;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ubagroup.vendorportal.service.ServiceAreaService;
import com.ubagroup.vendorportal.service.ServiceAreaService.AreaForm;

/** Settings > Service Areas (acesso restrito ao SYS_ADMIN pelo AccessInterceptor). */
@Controller
public class ServiceAreaController {

    private final ServiceAreaService areas;

    public ServiceAreaController(ServiceAreaService areas) {
        this.areas = areas;
    }

    @GetMapping("/settings/service-areas")
    public String page(Model model) {
        model.addAttribute("active", "settings-areas");
        model.addAttribute("areas", areas.list());
        return "settings-service-areas";
    }

    @PostMapping("/api/service-areas")
    @ResponseBody
    public Map<String, Object> create(@RequestBody AreaForm form) {
        return Map.of("id", areas.create(form));
    }

    @PutMapping("/api/service-areas/{id}")
    @ResponseBody
    public Map<String, Object> update(@PathVariable int id, @RequestBody AreaForm form) {
        areas.update(id, form);
        return Map.of("ok", true);
    }

    @DeleteMapping("/api/service-areas/{id}")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable int id) {
        areas.delete(id);
        return Map.of("ok", true);
    }
}
