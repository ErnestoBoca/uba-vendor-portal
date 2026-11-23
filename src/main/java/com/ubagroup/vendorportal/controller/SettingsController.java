package com.ubagroup.vendorportal.controller;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ubagroup.vendorportal.service.SettingsService;

/** Settings > General. */
@Controller
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/settings/general")
    public String general(Model model) {
        model.addAttribute("active", "settings-general");
        model.addAttribute("s", settings.all());
        return "settings-general";
    }

    @PutMapping("/api/settings")
    @ResponseBody
    public Map<String, Object> save(@RequestBody Map<String, String> values) {
        settings.update(values);
        return Map.of("ok", true);
    }
}
