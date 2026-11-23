package com.ubagroup.vendorportal.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.common.Jpa;
import com.ubagroup.vendorportal.entity.ServiceCategory;
import com.ubagroup.vendorportal.repository.ServiceCategoryRepository;

import static com.ubagroup.vendorportal.common.Util.blank;
import static com.ubagroup.vendorportal.common.Util.nullIfBlank;
import static com.ubagroup.vendorportal.common.Util.p;

/**
 * Settings > Service Areas. Criar, renomear, activar/desactivar e remover (so se nunca foi usada).
 * Uma area inactiva deixa de aparecer nos formularios de vendor/contrato e nos fluxos, mas os registos existentes mantem-na.
 */
@Service
@Transactional(readOnly = true)
public class ServiceAreaService {

    public record AreaForm(String name, String description, Boolean active) { }

    private final ServiceCategoryRepository areas;
    private final Jpa jpa;
    private final AuditService audit;

    public ServiceAreaService(ServiceCategoryRepository areas, Jpa jpa, AuditService audit) {
        this.areas = areas;
        this.jpa = jpa;
        this.audit = audit;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = jpa.list("select new map(c.id as id, c.name as name, c.description as description, c.active as active) "
            + "from ServiceCategory c order by c.active desc, c.name");
        for (Map<String, Object> r : rows) {
            Integer id = (Integer) r.get("id");
            r.put("vendors", jpa.count("select count(v) from Vendor v where v.serviceCategory.id = :id", p("id", id)));
            r.put("contracts", jpa.count("select count(c) from Contract c where c.serviceCategory.id = :id", p("id", id)));
            r.put("flowSteps", jpa.count("select count(s) from ApprovalFlowStep s where s.flow.entityType = 'CONTRACT' "
                + "and s.flow.scopeId = :sc and s.active = true", p("sc", id.longValue())));
        }
        return rows;
    }

    /** Usada pelos servicos de vendor/contrato: area tem de existir e estar activa (excepto se ja era a do registo). */
    public ServiceCategory requireUsable(Integer id, Integer currentId) {
        if (id == null) throw new ApiException("Seleccione a area de servico.");
        ServiceCategory c = areas.findById(id).orElseThrow(() -> new ApiException("Area de servico invalida."));
        if (!c.isActive() && !id.equals(currentId)) throw new ApiException("A area de servico '" + c.getName() + "' esta inactiva.");
        return c;
    }

    private String validName(String name) {
        if (blank(name)) throw new ApiException("O nome da area de servico e obrigatorio.");
        String n = name.trim();
        if (n.length() > 100) throw new ApiException("O nome pode ter no maximo 100 caracteres.");
        return n;
    }

    @Transactional
    public int create(AreaForm f) {
        String name = validName(f.name());
        if (areas.existsByName(name)) throw new ApiException("Ja existe uma area de servico com este nome.", 409);
        ServiceCategory c = new ServiceCategory();
        c.setName(name);
        c.setDescription(nullIfBlank(f.description()));
        c.setActive(f.active() == null || f.active());
        c = areas.save(c);
        audit.log("SERVICE_AREA", c.getId(), null, "CREATE", null, p("name", name, "active", c.isActive()));
        return c.getId();
    }

    @Transactional
    public void update(int id, AreaForm f) {
        ServiceCategory c = areas.findById(id).orElseThrow(() -> new ApiException("Area de servico nao encontrada.", 404));
        Map<String, Object> before = p("name", c.getName(), "description", c.getDescription(), "active", c.isActive());
        String name = validName(f.name());
        if (areas.existsByNameAndIdNot(name, id)) throw new ApiException("Ja existe uma area de servico com este nome.", 409);
        c.setName(name);
        c.setDescription(nullIfBlank(f.description()));
        if (f.active() != null) c.setActive(f.active());
        audit.log("SERVICE_AREA", id, null, "UPDATE", before, p("name", c.getName(), "description", c.getDescription(), "active", c.isActive()));
    }

    /** So remove areas nunca usadas (sem vendors, contratos nem fluxo proprio). Caso contrario, desactive. */
    @Transactional
    public void delete(int id) {
        ServiceCategory c = areas.findById(id).orElseThrow(() -> new ApiException("Area de servico nao encontrada.", 404));
        long used = jpa.count("select count(v) from Vendor v where v.serviceCategory.id = :id", p("id", id))
            + jpa.count("select count(x) from Contract x where x.serviceCategory.id = :id", p("id", id));
        if (used > 0) throw new ApiException("Esta area ja tem vendors ou contratos. Desactive-a em vez de a remover.", 409);
        long flows = jpa.count("select count(f) from ApprovalFlow f where f.entityType = 'CONTRACT' and f.scopeId = :sc", p("sc", (long) id));
        if (flows > 0) throw new ApiException("Esta area tem um fluxo de aprovacao proprio. Remova-o primeiro em Approval Flows.", 409);
        areas.delete(c);
        areas.flush();
        audit.log("SERVICE_AREA", id, null, "DELETE", p("name", c.getName()), null);
    }
}
