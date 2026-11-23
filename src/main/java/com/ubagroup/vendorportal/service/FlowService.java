package com.ubagroup.vendorportal.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.common.Jpa;
import com.ubagroup.vendorportal.entity.AppUser;
import com.ubagroup.vendorportal.entity.ApprovalFlow;
import com.ubagroup.vendorportal.entity.ApprovalFlowStep;
import com.ubagroup.vendorportal.repository.AppUserRepository;
import com.ubagroup.vendorportal.repository.ApprovalFlowRepository;
import com.ubagroup.vendorportal.repository.ApprovalFlowStepRepository;
import com.ubagroup.vendorportal.repository.ServiceCategoryRepository;

import static com.ubagroup.vendorportal.common.Util.p;

/** Configuracao dos fluxos de aprovacao de contratos: por area de servico, ou o fluxo por defeito (scope 0). */
@Service
@Transactional(readOnly = true)
public class FlowService {

    public record StepForm(String name, Long approverUserId) { }

    private final ApprovalFlowRepository flows;
    private final ApprovalFlowStepRepository stepRepo;
    private final ServiceCategoryRepository categories;
    private final AppUserRepository users;
    private final Jpa jpa;
    private final AuditService audit;

    public FlowService(ApprovalFlowRepository flows, ApprovalFlowStepRepository stepRepo, ServiceCategoryRepository categories,
                       AppUserRepository users, Jpa jpa, AuditService audit) {
        this.flows = flows;
        this.stepRepo = stepRepo;
        this.categories = categories;
        this.users = users;
        this.jpa = jpa;
        this.audit = audit;
    }

    private long countSteps(long scope) {
        return jpa.count("select count(s) from ApprovalFlowStep s where s.flow.entityType = 'CONTRACT' and s.flow.scopeId = :sc and s.active = true",
            p("sc", scope));
    }

    /** Uma linha por area de servico + a linha 'Default' (id 0), indicando quantos passos tem. */
    public List<Map<String, Object>> scopes() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(p("id", 0L, "name", "Default (all other services)", "steps", countSteps(0)));
        for (Map<String, Object> c : jpa.list("select new map(c.id as id, c.name as name) from ServiceCategory c where c.active = true order by c.id")) {
            out.add(p("id", ((Number) c.get("id")).longValue(), "name", c.get("name"), "steps", countSteps(((Number) c.get("id")).longValue())));
        }
        return out;
    }

    public List<Map<String, Object>> steps(long scope) {
        return jpa.list("select new map(s.stepNo as stepNo, s.name as name, a.id as approverId, a.fullName as approverName) "
            + "from ApprovalFlowStep s join s.flow f join s.approver a "
            + "where f.entityType = 'CONTRACT' and f.scopeId = :sc and s.active = true order by s.stepNo", p("sc", scope));
    }

    public String scopeName(long scope) {
        if (scope == 0) return "Default (all other services)";
        return categories.findById((int) scope).orElseThrow(() -> new ApiException("Area de servico nao encontrada.", 404)).getName();
    }

    @Transactional
    public void save(long scope, List<StepForm> steps) {
        String scopeName = scopeName(scope);
        if (steps == null || steps.isEmpty()) {
            throw new ApiException("O fluxo precisa de pelo menos um passo.");
        }
        List<AppUser> approvers = new ArrayList<>();
        for (StepForm s : steps) {
            if (s.name() == null || s.name().isBlank()) throw new ApiException("Cada passo precisa de um nome.");
            if (s.approverUserId() == null) throw new ApiException("Cada passo precisa de um aprovador.");
            AppUser u = users.findById(s.approverUserId()).filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException("O aprovador do passo '" + s.name() + "' nao existe ou esta inactivo."));
            approvers.add(u);
        }
        List<Map<String, Object>> before = steps(scope);
        ApprovalFlow flow = flows.findByEntityTypeAndScopeId("CONTRACT", scope).orElse(null);
        if (flow == null) {
            flow = new ApprovalFlow();
            flow.setEntityType("CONTRACT");
            flow.setScopeId(scope);
            flow.setName("Contract flow - " + scopeName);
            flow = flows.save(flow);
        } else {
            flow.setActive(true);
            stepRepo.deleteAllByFlowId(flow.getId());     // apaga ja' na BD (chave unica flow_id + step_no)
            flow = flows.findById(flow.getId()).orElseThrow();
        }
        int no = 1;
        for (int i = 0; i < steps.size(); i++) {
            ApprovalFlowStep st = new ApprovalFlowStep();
            st.setFlow(flow);
            st.setStepNo(no++);
            st.setName(steps.get(i).name().trim());
            st.setApprover(approvers.get(i));
            stepRepo.save(st);
        }
        stepRepo.flush();
        audit.log("APPROVAL_FLOW", flow.getId(), null, before.isEmpty() ? "CREATE" : "UPDATE", before, steps(scope));
    }

    /** Remove o fluxo proprio de uma area (volta a usar o por defeito). O fluxo por defeito nao pode ser removido. */
    @Transactional
    public void removeCustom(long scope) {
        if (scope == 0) throw new ApiException("O fluxo por defeito nao pode ser removido.");
        ApprovalFlow flow = flows.findByEntityTypeAndScopeId("CONTRACT", scope).orElse(null);
        if (flow == null) return;
        List<Map<String, Object>> before = steps(scope);
        Integer flowId = flow.getId();
        stepRepo.deleteAllByFlowId(flowId);
        flows.deleteById(flowId);
        flows.flush();
        audit.log("APPROVAL_FLOW", flowId, null, "DELETE", before, null);
    }
}
