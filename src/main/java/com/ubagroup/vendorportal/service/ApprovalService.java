package com.ubagroup.vendorportal.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.common.CurrentUser;
import com.ubagroup.vendorportal.common.Jpa;
import com.ubagroup.vendorportal.entity.AppUser;
import com.ubagroup.vendorportal.entity.ApprovalFlowStep;
import com.ubagroup.vendorportal.entity.ApprovalRequest;
import com.ubagroup.vendorportal.entity.ApprovalTask;
import com.ubagroup.vendorportal.entity.ApprovalTaskReassignment;
import com.ubagroup.vendorportal.entity.Contract;
import com.ubagroup.vendorportal.repository.AppUserRepository;
import com.ubagroup.vendorportal.repository.ApprovalFlowStepRepository;
import com.ubagroup.vendorportal.repository.ApprovalRequestRepository;
import com.ubagroup.vendorportal.repository.ApprovalTaskReassignmentRepository;
import com.ubagroup.vendorportal.repository.ApprovalTaskRepository;
import com.ubagroup.vendorportal.repository.ContractRepository;

import static com.ubagroup.vendorportal.common.Util.p;

/**
 * Motor generico de aprovacoes (entity_type + entity_id). Hoje so CONTRACT; ASSET entra depois sem mexer aqui.
 * Fluxo sequencial, um aprovador por passo, reassign com motivo.
 */
@Service
@Transactional(readOnly = true)
public class ApprovalService {

    private final ApprovalRequestRepository requests;
    private final ApprovalTaskRepository tasks;
    private final ApprovalTaskReassignmentRepository reassignments;
    private final ApprovalFlowStepRepository flowSteps;
    private final ContractRepository contracts;
    private final AppUserRepository users;
    private final Jpa jpa;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final SettingsService settings;

    public ApprovalService(ApprovalRequestRepository requests, ApprovalTaskRepository tasks,
                           ApprovalTaskReassignmentRepository reassignments, ApprovalFlowStepRepository flowSteps,
                           ContractRepository contracts, AppUserRepository users, Jpa jpa, CurrentUser currentUser,
                           AuditService audit, SettingsService settings) {
        this.requests = requests;
        this.tasks = tasks;
        this.reassignments = reassignments;
        this.flowSteps = flowSteps;
        this.contracts = contracts;
        this.users = users;
        this.jpa = jpa;
        this.currentUser = currentUser;
        this.audit = audit;
        this.settings = settings;
    }

    // ---------- submeter ----------

    @Transactional
    public long submitContract(long contractId) {
        currentUser.requireContractEdit();
        Contract c = contracts.findById(contractId).orElseThrow(() -> new ApiException("Contrato nao encontrado.", 404));
        String st = c.getStatus();
        if (!st.equals("DRAFT") && !st.equals("REJECTED")) {
            throw new ApiException("So contratos em Draft ou Rejected podem ser submetidos.");
        }
        if (!"APPROVED".equals(c.getVendor().getStatus())) {
            throw new ApiException("O vendor deste contrato ainda nao esta aprovado.");
        }
        List<ApprovalFlowStep> steps = resolveSteps("CONTRACT", c.getServiceCategory().getId());
        for (ApprovalFlowStep s : steps) {
            if (s.getApprover() == null && jpa.count("select count(u) from AppUser u join u.roles r where r.id = :r and u.active = true",
                    p("r", s.getApproverRole().getId())) == 0) {
                throw new ApiException("O passo '" + s.getName() + "' e do perfil " + s.getApproverRole().getName()
                    + ", mas nenhum utilizador activo o tem. Corrija em Settings > Users ou Approval Flows.");
            }
        }

        ApprovalRequest r = new ApprovalRequest();
        r.setEntityType("CONTRACT");
        r.setEntityId(contractId);
        r.setSubmittedBy(users.getReferenceById(currentUser.id()));
        r = requests.save(r);

        int no = 1;
        for (ApprovalFlowStep s : steps) {
            ApprovalTask t = new ApprovalTask();
            t.setRequest(r);
            t.setStepNo(no);
            t.setStepName(s.getName());
            t.setApproverRole(s.getApproverRole());
            t.setApprover(s.getApprover());
            t.setDecision(no == 1 ? "PENDING" : "WAITING");
            tasks.save(t);
            no++;
        }
        c.setStatus("IN_APPROVAL");
        audit.log("CONTRACT", contractId, c.getVendor().getId(), "SUBMIT", p("status", st), p("status", "IN_APPROVAL", "requestId", r.getId()));
        return r.getId();
    }

    /** Fluxo do tipo de servico; se nao existir, o fluxo por defeito (scope_id = 0). */
    private List<ApprovalFlowStep> resolveSteps(String entityType, long scope) {
        if (scope != 0) {
            List<ApprovalFlowStep> own = flowSteps.activeSteps(entityType, scope);
            if (!own.isEmpty()) return own;
        }
        List<ApprovalFlowStep> def = flowSteps.activeSteps(entityType, 0L);
        if (def.isEmpty()) {
            throw new ApiException("Nao existe fluxo de aprovacao configurado. Defina-o em Settings > Approval Flows.");
        }
        return def;
    }

    // ---------- decidir ----------

    @Transactional
    public void decide(long taskId, String decision, String comment) {
        ApprovalTask t = tasks.findById(taskId).orElseThrow(() -> new ApiException("Tarefa nao encontrada.", 404));
        if (!"PENDING".equals(t.getDecision())) throw new ApiException("Esta tarefa ja nao esta pendente.");
        ApprovalRequest req = t.getRequest();
        long me = currentUser.id();
        if (!canAct(t)) {
            throw new ApiException("Esta tarefa esta atribuida a outra pessoa ou a outro perfil.", 403);
        }
        if (settings.bool("enforce_segregation_of_duties") && req.getSubmittedBy().getId() == me) {
            throw new ApiException("Segregacao de funcoes activa: quem submeteu nao pode aprovar o proprio pedido.", 403);
        }
        String newDecision = switch (String.valueOf(decision)) {
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            case "RETURN" -> "RETURNED";
            default -> throw new ApiException("Decisao desconhecida.");
        };
        if (!newDecision.equals("APPROVED") && (comment == null || comment.isBlank())) {
            throw new ApiException("O comentario e obrigatorio para rejeitar ou devolver.");
        }

        t.setDecision(newDecision);
        t.setDecidedAt(OffsetDateTime.now());
        t.setDecidedBy(users.getReferenceById(me));
        t.setComments(comment == null || comment.isBlank() ? null : comment.trim());

        List<ApprovalTask> waiting = tasks.findByRequestAndDecision(req.getId(), "WAITING");
        if (newDecision.equals("APPROVED")) {
            if (!waiting.isEmpty()) {
                waiting.get(0).setDecision("PENDING");        // proximo passo
            } else {
                closeRequest(req, "APPROVED");
                onClosed(req, "APPROVED");
            }
        } else {
            waiting.forEach(w -> w.setDecision("CANCELLED"));
            closeRequest(req, newDecision);
            onClosed(req, newDecision);
        }
        audit.log("APPROVAL_TASK", taskId, vendorOf(req), decision, p("decision", "PENDING"),
            p("decision", newDecision, "comment", comment, "entityType", req.getEntityType(), "entityId", req.getEntityId()));
    }

    /** Pode decidir/reatribuir: a pessoa atribuida; se nao houver pessoa, quem tem o perfil do passo; ou o administrador. */
    private boolean canAct(ApprovalTask t) {
        return canAct(t.getApprover() == null ? null : t.getApprover().getId(),
            t.getApproverRole() == null ? null : t.getApproverRole().getId(), currentUser.id(), currentUser.roleIds(), currentUser.isAdmin());
    }

    private static boolean canAct(Long approverId, Integer roleId, long me, List<Integer> myRoles, boolean admin) {
        if (admin) return true;
        if (approverId != null) return approverId == me;
        return roleId != null && myRoles.contains(roleId);
    }

    private void closeRequest(ApprovalRequest r, String status) {
        r.setStatus(status);
        r.setClosedAt(OffsetDateTime.now());
    }

    /** Efeito sobre a entidade quando o pedido fecha. Para novas entidades (ASSET) acrescenta-se um case. */
    private void onClosed(ApprovalRequest r, String outcome) {
        if (!r.getEntityType().equals("CONTRACT")) return;
        Contract c = contracts.findById(r.getEntityId()).orElseThrow(() -> new ApiException("Contrato nao encontrado.", 404));
        switch (outcome) {
            case "APPROVED" -> {
                if (!"APPROVED".equals(c.getVendor().getStatus())) {
                    throw new ApiException("O vendor deixou de estar aprovado; nao e possivel activar o contrato.");
                }
                c.setStatus("ACTIVE");
                c.setActivatedAt(OffsetDateTime.now());
            }
            case "REJECTED" -> c.setStatus("REJECTED");
            case "RETURNED" -> c.setStatus("DRAFT");
            default -> { }
        }
    }

    // ---------- reassign ----------

    @Transactional
    public void reassign(long taskId, long toUserId, String reason) {
        ApprovalTask t = tasks.findById(taskId).orElseThrow(() -> new ApiException("Tarefa nao encontrada.", 404));
        String d = t.getDecision();
        if (!d.equals("PENDING") && !d.equals("WAITING")) throw new ApiException("So tarefas pendentes ou em espera podem ser reatribuidas.");
        long me = currentUser.id();
        AppUser from = t.getApprover();
        if (!canAct(t)) throw new ApiException("Apenas o aprovador actual (ou alguem com o perfil do passo) ou um administrador pode reatribuir.", 403);
        if (reason == null || reason.isBlank()) throw new ApiException("Indique o motivo da reatribuicao.");
        if (from != null && toUserId == from.getId()) throw new ApiException("Escolha uma pessoa diferente do aprovador actual.");
        AppUser to = users.findById(toUserId).filter(AppUser::isActive)
            .orElseThrow(() -> new ApiException("O utilizador escolhido nao existe ou esta inactivo."));

        ApprovalTaskReassignment x = new ApprovalTaskReassignment();
        x.setTask(t);
        x.setFromUser(from);
        x.setToUser(to);
        x.setReassignedBy(users.getReferenceById(me));
        x.setReason(reason.trim());
        reassignments.save(x);
        t.setApprover(to);
        audit.log("APPROVAL_TASK", taskId, vendorOf(t.getRequest()), "REASSIGN",
            p("approver", from == null ? null : from.getId(), "role", t.getApproverRole() == null ? null : t.getApproverRole().getId()),
            p("approver", toUserId, "reason", reason.trim()));
    }

    // ---------- leitura ----------

    private Long vendorOf(ApprovalRequest r) {
        if (!r.getEntityType().equals("CONTRACT")) return null;
        return contracts.findById(r.getEntityId()).map(c -> c.getVendor().getId()).orElse(null);
    }

    private static final String TASK_SELECT =
        "select new map(t.id as taskId, t.stepNo as stepNo, t.stepName as stepName, t.decision as decision, u.id as approverId, "
        + "ar.id as roleId, r.id as requestId, r.entityType as entityType, r.entityId as entityId, c.contractNumber as contractNumber, "
        + "c.title as title, v.supplierName as vendorName, coalesce(u.fullName, concat('Any ', ar.name)) as approverName, "
        + "du.fullName as decidedBy, su.fullName as submittedBy, "
        + "r.submittedAt as submittedAt, t.decidedAt as decidedAt, t.comments as comments) "
        + "from ApprovalTask t join t.request r left join t.approver u left join t.approverRole ar left join t.decidedBy du "
        + "join r.submittedBy su "
        + "left join Contract c on r.entityType = 'CONTRACT' and c.id = r.entityId left join c.vendor v ";

    /** Tarefas "minhas": atribuidas a mim, ou sem pessoa e do meu perfil. */
    private static final String MINE = "(u.id = :me or (u.id is null and ar.id in :roles))";

    private Map<String, Object> mineParams() {
        List<Integer> roles = new java.util.ArrayList<>(currentUser.roleIds());
        roles.add(-1);                                   // nunca vazio (in () nao e valido)
        return p("me", currentUser.id(), "roles", roles);
    }

    /** tab: mine | all | decided. Cada linha traz canAct (mostrar ou nao os botoes). */
    public List<Map<String, Object>> inbox(String tab) {
        List<Map<String, Object>> rows = switch (tab) {
            case "all" -> jpa.list(TASK_SELECT + "where t.decision = 'PENDING' order by r.submittedAt");
            case "decided" -> jpa.top(TASK_SELECT + "where t.decision in ('APPROVED', 'REJECTED', 'RETURNED') order by t.decidedAt desc",
                Map.of(), 100);
            default -> jpa.list(TASK_SELECT + "where t.decision = 'PENDING' and " + MINE + " order by r.submittedAt", mineParams());
        };
        markCanAct(rows);
        return rows;
    }

    private void markCanAct(List<Map<String, Object>> rows) {
        long me = currentUser.id();
        List<Integer> myRoles = currentUser.roleIds();
        boolean admin = currentUser.isAdmin();
        for (Map<String, Object> t : rows) {
            boolean open = "PENDING".equals(t.get("decision"));
            Long approverId = t.get("approverId") == null ? null : ((Number) t.get("approverId")).longValue();
            Integer roleId = t.get("roleId") == null ? null : ((Number) t.get("roleId")).intValue();
            t.put("canAct", open && canAct(approverId, roleId, me, myRoles, admin));
        }
    }

    public long countMine() {
        return jpa.count("select count(t) from ApprovalTask t left join t.approver u left join t.approverRole ar "
            + "where t.decision = 'PENDING' and " + MINE, mineParams());
    }

    /** Historico de pedidos de uma entidade, com tarefas e reatribuicoes. */
    public List<Map<String, Object>> history(String entityType, long entityId) {
        List<Map<String, Object>> list = jpa.list(
            "select new map(r.id as requestId, r.status as status, u.fullName as submittedBy, r.submittedAt as submittedAt, r.closedAt as closedAt) "
            + "from ApprovalRequest r join r.submittedBy u where r.entityType = :et and r.entityId = :id order by r.id desc",
            p("et", entityType, "id", entityId));
        for (Map<String, Object> r : list) {
            List<Map<String, Object>> taskRows = jpa.list(
                "select new map(t.id as taskId, t.stepNo as stepNo, t.stepName as stepName, t.decision as decision, "
                + "coalesce(u.fullName, concat('Any ', ar.name)) as approverName, u.id as approverId, ar.id as roleId, ar.name as roleName, "
                + "du.fullName as decidedBy, t.decidedAt as decidedAt, t.comments as comments) "
                + "from ApprovalTask t left join t.approver u left join t.approverRole ar left join t.decidedBy du "
                + "where t.request.id = :r order by t.stepNo", p("r", r.get("requestId")));
            markCanAct(taskRows);
            for (Map<String, Object> t : taskRows) {
                t.put("reassignments", jpa.list(
                    "select new map(coalesce(fu.fullName, 'role queue') as fromName, tu.fullName as toName, x.reason as reason, bu.fullName as byName, "
                    + "x.reassignedAt as happenedAt) from ApprovalTaskReassignment x left join x.fromUser fu join x.toUser tu "
                    + "join x.reassignedBy bu where x.task.id = :t order by x.reassignedAt", p("t", t.get("taskId"))));
            }
            r.put("tasks", taskRows);
        }
        return list;
    }

    public List<Map<String, Object>> activeUsers() {
        return jpa.list("select new map(u.id as id, u.fullName as fullName, u.username as username) from AppUser u where u.active = true order by u.fullName");
    }
}
