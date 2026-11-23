package com.ubagroup.vendorportal.common;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ubagroup.vendorportal.entity.AppRole;
import com.ubagroup.vendorportal.entity.AppUser;
import com.ubagroup.vendorportal.repository.AppUserRepository;

/**
 * Acesso ao utilizador autenticado e as suas permissoes.
 * As permissoes sao lidas da BD em cada pedido: uma mudanca de perfil em Settings > Users tem efeito imediato.
 *
 * Matriz (ver tambem docs/TEST_PLAN.md):
 *  - SYS_ADMIN ............ tudo (Settings incluido)
 *  - CS_OFFICER ........... regista/edita vendors, contactos e documentos; cria/edita/submete contratos
 *  - CS_APPROVER .......... inicia revisao, aprova/rejeita vendors
 *  - LEGAL_OFFICER ........ cria/edita/submete contratos (contract register)
 *  - *_APPROVER ........... decide os passos de aprovacao atribuidos ao seu perfil (ou a si)
 *  - VIEWER (UBA Staff) ... so leitura
 */
@Component
public class CurrentUser {

    public static final String DEPT_CS = "CS";
    public static final String DEPT_LEGAL = "LEGAL";

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
        String roleNames = String.join(", ", u.getRoles().stream().filter(AppRole::isActive).map(AppRole::getName).sorted().toList());
        return Util.p("id", u.getId(), "username", u.getUsername(), "fullName", u.getFullName(), "email", u.getEmail(), "roles", roleNames);
    }

    // ---------- perfis ----------

    private static boolean isAdmin(AppUser u) {
        return u.getRoles().stream().anyMatch(r -> r.isActive() && AppRole.ADMIN.equals(r.getLevel()));
    }

    /** true se o utilizador tem um perfil activo do departamento (activo) com o nivel indicado. */
    private static boolean has(AppUser u, String dept, String level) {
        return u.getRoles().stream().anyMatch(r -> r.isActive() && level.equals(r.getLevel())
            && r.getDepartment() != null && r.getDepartment().isActive() && dept.equals(r.getDepartment().getCode()));
    }

    public boolean isAdmin() {
        return isAdmin(user());
    }

    /** Ids dos perfis activos do utilizador (para a caixa de aprovacoes por perfil). */
    public List<Integer> roleIds() {
        return user().getRoles().stream()
            .filter(r -> r.isActive() && (r.getDepartment() == null || r.getDepartment().isActive()))
            .map(AppRole::getId).toList();
    }

    public boolean canEditVendors() {
        AppUser u = user();
        return isAdmin(u) || has(u, DEPT_CS, AppRole.OFFICER);
    }

    public boolean canReviewVendors() {
        AppUser u = user();
        return isAdmin(u) || has(u, DEPT_CS, AppRole.APPROVER);
    }

    public boolean canEditContracts() {
        AppUser u = user();
        return isAdmin(u) || has(u, DEPT_CS, AppRole.OFFICER) || has(u, DEPT_LEGAL, AppRole.OFFICER);
    }

    /** Mapa para os templates: can.vendorEdit, can.vendorReview, can.contractEdit, can.settings. */
    public Map<String, Object> permissions() {
        AppUser u = user();
        boolean admin = isAdmin(u);
        return Util.p(
            "settings", admin,
            "vendorEdit", admin || has(u, DEPT_CS, AppRole.OFFICER),
            "vendorReview", admin || has(u, DEPT_CS, AppRole.APPROVER),
            "contractEdit", admin || has(u, DEPT_CS, AppRole.OFFICER) || has(u, DEPT_LEGAL, AppRole.OFFICER));
    }

    // ---------- verificacoes (lancam 403) ----------

    public void requireAdmin() {
        if (!isAdmin()) throw new ApiException("Apenas o administrador do sistema pode alterar as configuracoes.", 403);
    }

    public void requireVendorEdit() {
        if (!canEditVendors()) throw new ApiException("Sem permissao: apenas Corporate Services Officers registam e alteram vendors.", 403);
    }

    public void requireVendorReview() {
        if (!canReviewVendors()) throw new ApiException("Sem permissao: apenas o Corporate Services Approver aprova ou rejeita vendors.", 403);
    }

    public void requireContractEdit() {
        if (!canEditContracts()) throw new ApiException("Sem permissao: apenas Corporate Services ou Legal Officers criam e submetem contratos.", 403);
    }
}
