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

import com.ubagroup.vendorportal.service.UserService;
import com.ubagroup.vendorportal.service.UserService.UserForm;

/** Settings > Users. */
@Controller
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/settings/users")
    public String page(Model model) {
        model.addAttribute("active", "settings-users");
        model.addAttribute("users", users.list());
        model.addAttribute("roles", users.roles());
        return "settings-users";
    }

    @PostMapping("/api/users")
    @ResponseBody
    public Map<String, Object> create(@RequestBody UserForm form) {
        return Map.of("id", users.create(form));
    }

    @PutMapping("/api/users/{id}")
    @ResponseBody
    public Map<String, Object> update(@PathVariable long id, @RequestBody UserForm form) {
        users.update(id, form);
        return Map.of("ok", true);
    }
}
