package com.ubagroup.vendorportal.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.common.CurrentUser;
import com.ubagroup.vendorportal.common.Jpa;
import com.ubagroup.vendorportal.entity.AppRole;
import com.ubagroup.vendorportal.entity.AppUser;
import com.ubagroup.vendorportal.repository.AppRoleRepository;
import com.ubagroup.vendorportal.repository.AppUserRepository;

import static com.ubagroup.vendorportal.common.Util.p;

@Service
@Transactional(readOnly = true)
public class UserService {

    public record UserForm(String username, String fullName, String email, String password, Integer roleId, Boolean active) { }

    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final Jpa jpa;
    private final PasswordEncoder encoder;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public UserService(AppUserRepository users, AppRoleRepository roles, Jpa jpa, PasswordEncoder encoder,
                       CurrentUser currentUser, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.jpa = jpa;
        this.encoder = encoder;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public List<Map<String, Object>> list() {
        return jpa.list("select new map(u.id as id, u.username as username, u.fullName as fullName, u.email as email, u.active as active, "
            + "r.id as roleId, r.name as roleName) from AppUser u left join u.roles r order by u.fullName");
    }

    public List<Map<String, Object>> roles() {
        return jpa.list("select new map(r.id as id, r.code as code, r.name as name) from AppRole r order by r.name");
    }

    private Set<AppRole> roleSet(Integer roleId) {
        Set<AppRole> s = new HashSet<>();
        s.add(roles.findById(roleId).orElseThrow(() -> new ApiException("Perfil invalido.")));
        return s;
    }

    @Transactional
    public long create(UserForm f) {
        if (f.username() == null || !f.username().trim().matches("[A-Za-z0-9._-]{3,50}")) {
            throw new ApiException("Username: 3 a 50 caracteres (letras, numeros, ponto, hifen, underscore).");
        }
        if (f.fullName() == null || f.fullName().isBlank()) throw new ApiException("O nome completo e obrigatorio.");
        if (f.email() == null || !f.email().contains("@")) throw new ApiException("Indique um email valido.");
        if (f.password() == null || f.password().length() < 6) throw new ApiException("A palavra-passe deve ter pelo menos 6 caracteres.");
        if (f.roleId() == null) throw new ApiException("Seleccione o perfil.");
        if (users.existsByUsername(f.username().trim()) || users.existsByEmail(f.email().trim())) {
            throw new ApiException("Ja existe um utilizador com este username ou email.", 409);
        }
        AppUser u = new AppUser();
        u.setUsername(f.username().trim());
        u.setFullName(f.fullName().trim());
        u.setEmail(f.email().trim());
        u.setPasswordHash(encoder.encode(f.password()));
        u.setRoles(roleSet(f.roleId()));
        u = users.save(u);
        audit.log("USER", u.getId(), null, "CREATE", null, p("username", u.getUsername(), "roleId", f.roleId()));
        return u.getId();
    }

    @Transactional
    public void update(long id, UserForm f) {
        AppUser u = users.findById(id).orElseThrow(() -> new ApiException("Utilizador nao encontrado.", 404));
        if (f.fullName() != null && !f.fullName().isBlank()) {
            u.setFullName(f.fullName().trim());
        }
        if (f.email() != null && !f.email().isBlank()) {
            if (users.existsByEmailAndIdNot(f.email().trim(), id)) throw new ApiException("Este email ja esta em uso.", 409);
            u.setEmail(f.email().trim());
        }
        if (f.password() != null && !f.password().isEmpty()) {
            if (f.password().length() < 6) throw new ApiException("A palavra-passe deve ter pelo menos 6 caracteres.");
            u.setPasswordHash(encoder.encode(f.password()));
        }
        if (f.active() != null) {
            if (!f.active() && id == currentUser.id()) throw new ApiException("Nao pode desactivar o proprio utilizador.");
            u.setActive(f.active());
        }
        if (f.roleId() != null) {
            u.setRoles(roleSet(f.roleId()));
        }
        audit.log("USER", id, null, "UPDATE", null, p("username", u.getUsername(), "active", f.active(), "roleId", f.roleId(),
            "passwordChanged", f.password() != null && !f.password().isEmpty()));
    }
}
