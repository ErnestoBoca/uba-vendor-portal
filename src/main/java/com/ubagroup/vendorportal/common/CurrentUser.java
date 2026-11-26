package com.ubagroup.vendorportal.common;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /** Lança 403 se o utilizador não puder registar/editar contratos (Legal ou administrador). */
    public void requireContractEdit() {
        if (!canManageContracts()) {
            throw new ApiException("Apenas o Legal pode registar ou editar contratos.", 403);
        }
    }

    /** Lança 403 se o utilizador não puder registar/editar vendors (Corporate Services ou administrador). */
    public void requireVendorEdit() {
        if (!canManageVendors()) {
            throw new ApiException("Apenas Corporate Services pode registar ou editar vendors.", 403);
        }
    }

    /** Lança 403 se o utilizador não puder rever (iniciar revisão, aprovar, rejeitar) vendors. */
    public void requireVendorReview() {
        if (!canReviewVendors()) {
            throw new ApiException("O seu perfil não pode rever vendors.", 403);
        }
    }

    public boolean isAdmin() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_SYS_ADMIN"));
    }

    /** True se o utilizador tem pelo menos um dos perfis (codigos de app_role, sem o prefixo ROLE_). */
    public boolean hasAnyRole(String... codes) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        Set<String> wanted = Arrays.stream(codes).map(c -> "ROLE_" + c).collect(Collectors.toSet());
        return a.getAuthorities().stream().anyMatch(g -> wanted.contains(g.getAuthority()));
    }

    /** Corporate Services (e administrador): regista vendors, contactos e documentos do vendor. */
    public boolean canManageVendors() {
        return hasAnyRole("SYS_ADMIN", "CS_OFFICER");
    }

    /** Legal (e administrador): regista contratos e os seus documentos. */
    public boolean canManageContracts() {
        return hasAnyRole("SYS_ADMIN", "LEGAL");
    }

    /** Quem pode rever (iniciar revisao, aprovar, rejeitar) um vendor. Quem regista nao revê. */
    public boolean canReviewVendors() {
        return hasAnyRole("SYS_ADMIN", "APPROVER", "COMPLIANCE", "RISK_TEAM");
    }
}
