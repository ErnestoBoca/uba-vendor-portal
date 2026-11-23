package com.ubagroup.vendorportal.common;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ubagroup.vendorportal.entity.AppUser;
import com.ubagroup.vendorportal.repository.AppUserRepository;

/** Acesso ao utilizador autenticado (sessao Spring Security) e aos seus dados em app_user. */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public String username() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    public AppUser user() {
        return users.findByUsername(String.valueOf(username()))
            .orElseThrow(() -> new ApiException("Sessao invalida.", 401));
    }

    public long id() {
        return user().getId();
    }

    /** Dados para o layout (topo): me.id, me.fullName, ... */
    public Map<String, Object> info() {
        AppUser u = user();
        return Util.p("id", u.getId(), "username", u.getUsername(), "fullName", u.getFullName(), "email", u.getEmail());
    }

    public boolean isAdmin() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_SYS_ADMIN"));
    }
}
