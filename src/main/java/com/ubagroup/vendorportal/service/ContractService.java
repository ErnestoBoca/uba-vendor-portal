package com.ubagroup.vendorportal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.common.CurrentUser;
import com.ubagroup.vendorportal.common.Jpa;
import com.ubagroup.vendorportal.entity.Contract;
import com.ubagroup.vendorportal.entity.Vendor;
import com.ubagroup.vendorportal.repository.AppUserRepository;
import com.ubagroup.vendorportal.repository.ContractRepository;
import com.ubagroup.vendorportal.repository.ServiceCategoryRepository;
import com.ubagroup.vendorportal.repository.VendorRepository;

import static com.ubagroup.vendorportal.common.Util.blank;
import static com.ubagroup.vendorportal.common.Util.nullIfBlank;
import static com.ubagroup.vendorportal.common.Util.p;

@Service
@Transactional(readOnly = true)
public class ContractService {

    public record ContractForm(Long vendorId, Integer serviceCategoryId, String contractNumber, String title, String contractType,
                               String objectDescription, LocalDate signingDate, LocalDate startDate, LocalDate endDate,
                               BigDecimal valueAmount, String currency, String paymentTerms, Integer terminationNoticeDays,
                               String penaltyClause, String governingLaw, String slaTerms, String dpaStatus,
                               Boolean auditRights, Boolean autoRenew, Integer renewalNoticeDays,
                               String vendorSignatory, String bankSignatory, Long ownerUserId) { }

    private static final Set<String> TYPES = Set.of("SERVICE", "SUPPLY", "LICENSE", "CONSULTING", "OTHER");
    private static final Set<String> CURRENCIES = Set.of("MZN", "USD", "EUR", "ZAR");
    private static final Set<String> DPA = Set.of("NOT_REQUIRED", "PENDING", "SIGNED", "EXPIRED");

    private final ContractRepository contracts;
    private final VendorRepository vendors;
    private final ServiceCategoryRepository categories;
    private final AppUserRepository users;
    private final Jpa jpa;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final SettingsService settings;
    private final ServiceAreaService serviceAreas;

    public ContractService(ContractRepository contracts, VendorRepository vendors, ServiceCategoryRepository categories,
                           AppUserRepository users, Jpa jpa, CurrentUser currentUser, AuditService audit, SettingsService settings,
                           ServiceAreaService serviceAreas) {
        this.serviceAreas = serviceAreas;
        this.contracts = contracts;
        this.vendors = vendors;
        this.categories = categories;
        this.users = users;
        this.jpa = jpa;
        this.currentUser = currentUser;
        this.audit = audit;
        this.settings = settings;
    }

    public Map<String, Object> list(String q, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (q != null && !q.isBlank()) {
            where.append(" and (c.contractNumber like :q or c.title like :q or v.supplierName like :q)");
            params.put("q", "%" + q.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            if (status.equals("EXPIRING")) {
                where.append(" and c.status = 'ACTIVE' and c.endDate between :today and :limit");
                params.put("today", LocalDate.now());
                params.put("limit", LocalDate.now().plusDays(settings.integer("contract_expiry_warning_days", 60)));
            } else {
                where.append(" and c.status = :status");
                params.put("status", status);
            }
        }
        String from = " from Contract c join c.vendor v join c.serviceCategory sc";
        long total = jpa.count("select count(c)" + from + where, params);
        int pages = (int) Math.max(1, Math.ceil(total / (double) size));
        int pg = Math.min(Math.max(page, 1), pages);
        List<Map<String, Object>> rows = jpa.page(
            "select new map(c.id as id, c.contractNumber as number, c.title as title, v.supplierName as vendorName, v.id as vendorId, "
            + "sc.name as serviceArea, c.valueAmount as amount, c.currency as currency, c.startDate as startDate, c.endDate as endDate, "
            + "c.status as status)" + from + where + " order by c.createdAt desc, c.id desc", params, (pg - 1) * size, size);
        Map<String, Object> out = new HashMap<>();
        out.put("rows", rows);
        out.put("total", total);
        out.put("page", pg);
        out.put("pages", pages);
        out.put("from", total == 0 ? 0 : (pg - 1) * size + 1);
        out.put("to", Math.min((long) pg * size, total));
        return out;
    }

    public Map<String, Object> get(long id) {
        Map<String, Object> c = jpa.one(
            "select new map(c.id as id, c.contractNumber as number, c.title as title, v.id as vendorId, v.supplierName as vendorName, "
            + "v.vendorCode as vendorCode, sc.id as serviceCategoryId, sc.name as serviceArea, c.contractType as contractType, "
            + "c.objectDescription as objectDescription, c.signingDate as signingDate, c.startDate as startDate, c.endDate as endDate, "
            + "c.valueAmount as valueAmount, c.currency as currency, c.paymentTerms as paymentTerms, "
            + "c.terminationNoticeDays as terminationNoticeDays, c.penaltyClause as penaltyClause, c.governingLaw as governingLaw, "
            + "c.slaTerms as slaTerms, c.dpaStatus as dpaStatus, c.auditRights as auditRights, c.autoRenew as autoRenew, "
            + "c.renewalNoticeDays as renewalNoticeDays, c.vendorSignatory as vendorSignatory, c.bankSignatory as bankSignatory, "
            + "ou.id as ownerUserId, ou.fullName as ownerName, c.status as status, c.activatedAt as activatedAt, "
            + "c.createdAt as createdAt, cu.fullName as createdBy) "
            + "from Contract c join c.vendor v join c.serviceCategory sc left join c.owner ou left join c.createdBy cu "
            + "where c.id = :id", p("id", id));
        if (c == null) {
            throw new ApiException("Contrato nao encontrado.", 404);
        }
        return c;
    }

    public List<Map<String, Object>> forVendor(long vendorId) {
        return jpa.list("select new map(c.id as id, c.contractNumber as number, c.title as title, c.status as status, c.endDate as endDate) "
            + "from Contract c where c.vendor.id = :v order by c.createdAt desc", p("v", vendorId));
    }

    private Vendor validate(ContractForm f) {
        if (f.vendorId() == null) throw new ApiException("Seleccione o vendor.");
        Vendor v = vendors.findById(f.vendorId()).orElseThrow(() -> new ApiException("Vendor nao encontrado."));
        if (!"APPROVED".equals(v.getStatus()) || v.isSuspended()) {
            throw new ApiException("So e possivel criar contratos para vendors aprovados.");
        }
        if (f.serviceCategoryId() == null) throw new ApiException("Seleccione o tipo de servico (define o fluxo de aprovacao).");
        if (blank(f.title())) throw new ApiException("O titulo do contrato e obrigatorio.");
        if (f.contractType() == null || !TYPES.contains(f.contractType())) throw new ApiException("Tipo de contrato invalido.");
        if (f.startDate() == null || f.endDate() == null) throw new ApiException("Indique as datas de inicio e fim.");
        if (f.endDate().isBefore(f.startDate())) throw new ApiException("A data de fim nao pode ser anterior a data de inicio.");
        if (f.valueAmount() != null && f.valueAmount().signum() < 0) throw new ApiException("O valor nao pode ser negativo.");
        if (f.currency() != null && !CURRENCIES.contains(f.currency())) throw new ApiException("Moeda invalida.");
        if (f.dpaStatus() != null && !DPA.contains(f.dpaStatus())) throw new ApiException("Estado de DPA invalido.");
        return v;
    }

    private void fill(Contract c, ContractForm f, Vendor vendor) {
        c.setVendor(vendor);
        c.setServiceCategory(serviceAreas.requireUsable(f.serviceCategoryId(), c.getServiceCategory() == null ? null : c.getServiceCategory().getId()));
        c.setTitle(f.title().trim());
        c.setContractType(f.contractType());
        c.setObjectDescription(nullIfBlank(f.objectDescription()));
        c.setSigningDate(f.signingDate());
        c.setStartDate(f.startDate());
        c.setEndDate(f.endDate());
        c.setValueAmount(f.valueAmount());
        c.setCurrency(f.currency() == null ? "MZN" : f.currency());
        c.setPaymentTerms(nullIfBlank(f.paymentTerms()));
        c.setTerminationNoticeDays(f.terminationNoticeDays());
        c.setPenaltyClause(nullIfBlank(f.penaltyClause()));
        c.setGoverningLaw(blank(f.governingLaw()) ? "Mozambique" : f.governingLaw().trim());
        c.setSlaTerms(nullIfBlank(f.slaTerms()));
        c.setDpaStatus(f.dpaStatus() == null ? "NOT_REQUIRED" : f.dpaStatus());
        c.setAuditRights(Boolean.TRUE.equals(f.auditRights()));
        c.setAutoRenew(Boolean.TRUE.equals(f.autoRenew()));
        c.setRenewalNoticeDays(f.renewalNoticeDays() == null ? 60 : f.renewalNoticeDays());
        c.setVendorSignatory(nullIfBlank(f.vendorSignatory()));
        c.setBankSignatory(nullIfBlank(f.bankSignatory()));
        c.setOwner(f.ownerUserId() == null ? null : users.getReferenceById(f.ownerUserId()));
    }

    @Transactional
    public long create(ContractForm f) {
        currentUser.requireContractEdit();
        Vendor vendor = validate(f);
        Contract c = new Contract();
        fill(c, f, vendor);
        c.setContractNumber(blank(f.contractNumber()) ? "TMP-" + UUID.randomUUID() : f.contractNumber().trim());
        c.setCreatedBy(users.getReferenceById(currentUser.id()));
        c = contracts.save(c);
        if (blank(f.contractNumber())) {
            c.setContractNumber(String.format("CTR-%d-%05d", LocalDate.now().getYear(), c.getId()));
        }
        contracts.flush();
        audit.log("CONTRACT", c.getId(), f.vendorId(), "CREATE", null, get(c.getId()));
        return c.getId();
    }

    @Transactional
    public void update(long id, ContractForm f) {
        currentUser.requireContractEdit();
        Contract c = contracts.findById(id).orElseThrow(() -> new ApiException("Contrato nao encontrado.", 404));
        if (!c.getStatus().equals("DRAFT") && !c.getStatus().equals("REJECTED")) {
            throw new ApiException("So e possivel editar contratos em Draft ou Rejected.");
        }
        Vendor vendor = validate(f);
        Map<String, Object> before = get(id);
        fill(c, f, vendor);
        if (!blank(f.contractNumber())) {
            c.setContractNumber(f.contractNumber().trim());
        }
        contracts.flush();
        audit.log("CONTRACT", id, f.vendorId(), "UPDATE", before, get(id));
    }
}
