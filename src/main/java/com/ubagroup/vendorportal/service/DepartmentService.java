package com.ubagroup.vendorportal.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.common.Jpa;
import com.ubagroup.vendorportal.entity.AppRole;
import com.ubagroup.vendorportal.entity.Department;
import com.ubagroup.vendorportal.repository.AppRoleRepository;
import com.ubagroup.vendorportal.repository.DepartmentRepository;

import static com.ubagroup.vendorportal.common.Util.blank;
import static com.ubagroup.vendorportal.common.Util.p;

/**
 * Settings > Roles: departamentos e os seus perfis.
 * Cada departamento tem sempre um perfil APPROVER; com "has officers" tem tambem um OFFICER.
 * Corporate Services e Legal sao departamentos de sistema (Officers + Approver, nao podem ser desactivados).
 */
@Service
@Transactional(readOnly = true)
public class DepartmentService {

    public record DepartmentForm(String code, String name, Boolean hasOfficers, Boolean active) { }

    private final DepartmentRepository departments;
    private final AppRoleRepository roles;
    private final Jpa jpa;
    private final AuditService audit;

    public DepartmentService(DepartmentRepository departments, AppRoleRepository roles, Jpa jpa, AuditService audit) {
        this.departments = departments;
        this.roles = roles;
        this.jpa = jpa;
        this.audit = audit;
    }

    private static final String ROLE_SELECT = "select new map(r.id as id, r.code as code, r.name as name, r.level as level, r.active as active, "
        + "d.id as departmentId, d.name as departmentName, "
        + "(select count(u) from AppUser u join u.roles ur where ur.id = r.id and u.active = true) as users) "
        + "from AppRole r left join r.department d ";

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = jpa.list("select new map(d.id as id, d.code as code, d.name as name, d.hasOfficers as hasOfficers, "
            + "d.system as system, d.active as active) from Department d order by d.system desc, d.active desc, d.name");
        for (Map<String, Object> d : rows) {
            d.put("roles", jpa.list(ROLE_SELECT + "where d.id = :d order by r.level desc", p("d", d.get("id"))));
        }
        return rows;
    }

    /** Perfis sem departamento (System Administrator, UBA Staff). */
    public List<Map<String, Object>> systemRoles() {
        return jpa.list(ROLE_SELECT + "where d.id is null order by r.name");
    }

    @Transactional
    public int create(DepartmentForm f) {
        if (blank(f.code()) || !f.code().trim().toUpperCase().matches("[A-Z][A-Z0-9_]{1,19}")) {
            throw new ApiException("Codigo: 2 a 20 caracteres (letras, numeros, underscore), a comecar por letra. Ex.: PROCUREMENT");
        }
        if (blank(f.name())) throw new ApiException("O nome do departamento e obrigatorio.");
        String code = f.code().trim().toUpperCase();
        String name = f.name().trim();
        if (departments.existsByCode(code)) throw new ApiException("Ja existe um departamento com este codigo.", 409);
        if (departments.existsByName(name)) throw new ApiException("Ja existe um departamento com este nome.", 409);
        if (roles.findByCode(code + "_APPROVER").isPresent() || roles.findByCode(code + "_OFFICER").isPresent()) {
            throw new ApiException("Ja existe um perfil com o codigo " + code + "_APPROVER/_OFFICER.", 409);
        }
        Department d = new Department();
        d.setCode(code);
        d.setName(name);
        d.setHasOfficers(Boolean.TRUE.equals(f.hasOfficers()));
        d = departments.save(d);
        syncRoles(d);
        audit.log("DEPARTMENT", d.getId(), null, "CREATE", null, p("code", code, "name", name, "hasOfficers", d.isHasOfficers()));
        return d.getId();
    }

    @Transactional
    public void update(int id, DepartmentForm f) {
        Department d = departments.findById(id).orElseThrow(() -> new ApiException("Departamento nao encontrado.", 404));
        Map<String, Object> before = p("name", d.getName(), "hasOfficers", d.isHasOfficers(), "active", d.isActive());
        if (!blank(f.name())) {
            String name = f.name().trim();
            if (departments.existsByNameAndIdNot(name, id)) throw new ApiException("Ja existe um departamento com este nome.", 409);
            d.setName(name);
        }
        if (f.hasOfficers() != null && f.hasOfficers() != d.isHasOfficers()) {
            if (d.isSystem()) throw new ApiException("Corporate Services e Legal tem sempre Officers e Approver.");
            if (!f.hasOfficers()) ensureRoleUnused(d, AppRole.OFFICER, "remover os Officers");
            d.setHasOfficers(f.hasOfficers());
        }
        if (f.active() != null && f.active() != d.isActive()) {
            if (d.isSystem() && !f.active()) throw new ApiException("Este departamento e de sistema e nao pode ser desactivado.");
            if (!f.active()) {
                ensureRoleUnused(d, AppRole.OFFICER, "desactivar o departamento");
                ensureRoleUnused(d, AppRole.APPROVER, "desactivar o departamento");
            }
            d.setActive(f.active());
        }
        syncRoles(d);
        audit.log("DEPARTMENT", id, null, "UPDATE", before, p("name", d.getName(), "hasOfficers", d.isHasOfficers(), "active", d.isActive()));
    }

    /** Nao deixa retirar um perfil que ainda tem utilizadores activos ou passos de fluxo / tarefas pendentes. */
    private void ensureRoleUnused(Department d, String level, String what) {
        AppRole r = roles.findByDepartmentIdAndLevel(d.getId(), level).orElse(null);
        if (r == null || !r.isActive()) return;
        long users = jpa.count("select count(u) from AppUser u join u.roles ur where ur.id = :r and u.active = true", p("r", r.getId()));
        if (users > 0) throw new ApiException("Nao e possivel " + what + ": " + users + " utilizador(es) activo(s) tem o perfil " + r.getName() + ".", 409);
        long steps = jpa.count("select count(s) from ApprovalFlowStep s where s.approverRole.id = :r and s.active = true", p("r", r.getId()));
        if (steps > 0) throw new ApiException("Nao e possivel " + what + ": o perfil " + r.getName() + " e usado em " + steps + " passo(s) de fluxo.", 409);
        long tasks = jpa.count("select count(t) from ApprovalTask t where t.approverRole.id = :r and t.decision in ('PENDING','WAITING')", p("r", r.getId()));
        if (tasks > 0) throw new ApiException("Nao e possivel " + what + ": ha " + tasks + " tarefa(s) de aprovacao em curso para " + r.getName() + ".", 409);
    }

    /** Cria/renomeia/activa os perfis do departamento conforme hasOfficers e active. */
    private void syncRoles(Department d) {
        upsert(d, AppRole.APPROVER, d.isActive());
        upsert(d, AppRole.OFFICER, d.isActive() && d.isHasOfficers());
        roles.flush();
    }

    private void upsert(Department d, String level, boolean active) {
        AppRole r = roles.findByDepartmentIdAndLevel(d.getId(), level).orElse(null);
        String suffix = level.equals(AppRole.APPROVER) ? " Approver" : " Officer";
        if (r == null) {
            if (!active) return;
            r = new AppRole();
            r.setCode(d.getCode() + "_" + level);
            r.setDepartment(d);
            r.setLevel(level);
        }
        r.setName(d.getName() + suffix);
        r.setActive(active);
        roles.save(r);
    }
}
