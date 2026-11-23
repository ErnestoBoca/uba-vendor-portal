package com.ubagroup.vendorportal.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.common.CurrentUser;
import com.ubagroup.vendorportal.common.Jpa;
import com.ubagroup.vendorportal.common.Util;
import com.ubagroup.vendorportal.entity.Vendor;
import com.ubagroup.vendorportal.entity.VendorContact;
import com.ubagroup.vendorportal.repository.AppUserRepository;
import com.ubagroup.vendorportal.repository.ServiceCategoryRepository;
import com.ubagroup.vendorportal.repository.VendorContactRepository;
import com.ubagroup.vendorportal.repository.VendorRepository;

import static com.ubagroup.vendorportal.common.Util.blank;
import static com.ubagroup.vendorportal.common.Util.nullIfBlank;
import static com.ubagroup.vendorportal.common.Util.p;

@Service
@Transactional(readOnly = true)
public class VendorService {

    public record ContactForm(String fullName, String phone, String jobTitle, String email) { }

    public record DocForm(String name, LocalDate issueDate, LocalDate expiryDate, Integer fileIndex) { }

    public record VendorForm(String supplierName, String email, String mobile, String phone, String address, String nuit,
                             Integer serviceCategoryId, String serviceDescription, String companyRegNumber,
                             List<ContactForm> contacts, List<DocForm> documents) { }

    private final VendorRepository vendors;
    private final VendorContactRepository contactRepo;
    private final ServiceCategoryRepository categories;
    private final AppUserRepository users;
    private final Jpa jpa;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final DocumentService documents;

    public VendorService(VendorRepository vendors, VendorContactRepository contactRepo, ServiceCategoryRepository categories,
                         AppUserRepository users, Jpa jpa, CurrentUser currentUser, AuditService audit, DocumentService documents) {
        this.vendors = vendors;
        this.contactRepo = contactRepo;
        this.categories = categories;
        this.users = users;
        this.jpa = jpa;
        this.currentUser = currentUser;
        this.audit = audit;
        this.documents = documents;
    }

    // ---------- leitura ----------

    public List<Map<String, Object>> categories() {
        return jpa.list("select new map(c.id as id, c.name as name) from ServiceCategory c where c.active = true order by c.name");
    }

    public Map<String, Object> list(String q, String status, Integer categoryId, int page, int size) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (q != null && !q.isBlank()) {
            where.append(" and (v.supplierName like :q or v.nuit like :q or v.vendorCode like :q)");
            params.put("q", "%" + q.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" and v.status = :status");
            params.put("status", status);
        }
        if (categoryId != null) {
            where.append(" and v.serviceCategory.id = :cat");
            params.put("cat", categoryId);
        }
        long total = jpa.count("select count(v) from Vendor v" + where, params);
        int pages = (int) Math.max(1, Math.ceil(total / (double) size));
        int pg = Math.min(Math.max(page, 1), pages);
        List<Map<String, Object>> rows = jpa.page(
            "select new map(v.id as id, v.vendorCode as code, v.supplierName as name, v.nuit as nuit, sc.name as serviceArea, "
            + "pc.fullName as contactName, v.mobile as mobile, v.email as email, v.status as status, v.createdAt as createdAt) "
            + "from Vendor v join v.serviceCategory sc "
            + "left join VendorContact pc on pc.vendor = v and pc.primaryContact = true and pc.active = true"
            + where + " order by v.createdAt desc, v.id desc", params, (pg - 1) * size, size);
        Map<String, Object> out = new HashMap<>();
        out.put("rows", rows);
        out.put("total", total);
        out.put("page", pg);
        out.put("pages", pages);
        out.put("size", size);
        out.put("from", total == 0 ? 0 : (pg - 1) * size + 1);
        out.put("to", Math.min((long) pg * size, total));
        return out;
    }

    public Map<String, Object> get(long id) {
        Map<String, Object> v = jpa.one(
            "select new map(v.id as id, v.vendorCode as code, v.supplierName as name, v.email as email, v.mobile as mobile, v.phone as phone, "
            + "v.address as address, v.nuit as nuit, sc.id as serviceCategoryId, sc.name as serviceArea, v.serviceDescription as serviceDescription, "
            + "v.companyRegNumber as companyRegNumber, v.status as status, v.reviewComment as reviewComment, v.reviewedAt as reviewedAt, "
            + "ru.fullName as reviewedBy, v.createdAt as createdAt, cu.fullName as createdBy) "
            + "from Vendor v join v.serviceCategory sc left join v.reviewedBy ru left join v.createdBy cu where v.id = :id", p("id", id));
        if (v == null) {
            throw new ApiException("Vendor nao encontrado.", 404);
        }
        return v;
    }

    public List<Map<String, Object>> contacts(long vendorId) {
        return jpa.list("select new map(c.id as id, c.fullName as fullName, c.phone as phone, c.jobTitle as jobTitle, c.email as email, "
            + "c.primaryContact as isPrimary) from VendorContact c where c.vendor.id = :v and c.active = true "
            + "order by c.primaryContact desc, c.id", p("v", vendorId));
    }

    public List<Map<String, Object>> history(long vendorId) {
        return jpa.top("select new map(a.occurredAt as happenedAt, u.fullName as actor, a.entityType as entityType, a.action as action) "
            + "from AuditLog a left join a.actor u where a.vendorId = :v order by a.occurredAt desc, a.id desc", p("v", vendorId), 30);
    }

    public List<Map<String, Object>> approvedForSelect() {
        return jpa.list("select new map(v.id as id, v.vendorCode as code, v.supplierName as name, v.serviceCategory.id as serviceCategoryId) "
            + "from Vendor v where v.status = 'APPROVED' and v.suspended = false order by v.supplierName");
    }

    public boolean nuitExists(String nuit, Long exceptId) {
        return exceptId == null ? vendors.existsByNuit(nuit) : vendors.existsByNuitAndIdNot(nuit, exceptId);
    }

    // ---------- escrita ----------

    private void validateMain(VendorForm f, Long exceptId) {
        if (blank(f.supplierName())) throw new ApiException("O nome do fornecedor e obrigatorio.");
        if (blank(f.email()) || !f.email().contains("@")) throw new ApiException("Indique um email valido.");
        if (blank(f.mobile())) throw new ApiException("O telemovel e obrigatorio.");
        if (blank(f.address())) throw new ApiException("O endereco e obrigatorio.");
        if (blank(f.nuit()) || !f.nuit().trim().matches("\\d{9}")) throw new ApiException("O NUIT deve ter exactamente 9 digitos.");
        if (f.serviceCategoryId() == null) throw new ApiException("Seleccione a area de servico.");
        if (nuitExists(f.nuit().trim(), exceptId)) throw new ApiException("Ja existe um fornecedor com este NUIT.", 409);
    }

    private void fill(Vendor v, VendorForm f) {
        v.setSupplierName(f.supplierName().trim());
        v.setEmail(f.email().trim());
        v.setMobile(f.mobile().trim());
        v.setPhone(nullIfBlank(f.phone()));
        v.setAddress(f.address().trim());
        v.setNuit(f.nuit().trim());
        v.setServiceCategory(categories.findById(f.serviceCategoryId()).orElseThrow(() -> new ApiException("Area de servico invalida.")));
        v.setServiceDescription(nullIfBlank(f.serviceDescription()));
        v.setCompanyRegNumber(nullIfBlank(f.companyRegNumber()));
    }

    @Transactional
    public long create(VendorForm f, List<MultipartFile> files) {
        validateMain(f, null);
        List<ContactForm> contacts = f.contacts() == null ? List.of() : f.contacts();
        if (contacts.isEmpty()) {
            throw new ApiException("Adicione pelo menos um contacto da empresa (o principal).");
        }
        for (ContactForm c : contacts) {
            if (blank(c.fullName()) || blank(c.phone()) || blank(c.jobTitle())) {
                throw new ApiException("Cada contacto precisa de nome, telefone e funcao.");
            }
        }
        List<DocForm> docs = f.documents() == null ? List.of() : f.documents();
        for (DocForm d : docs) {
            if (blank(d.name())) throw new ApiException("Cada documento precisa de um nome.");
            if (d.fileIndex() == null || files == null || d.fileIndex() >= files.size() || files.get(d.fileIndex()).isEmpty()) {
                throw new ApiException("Falta o ficheiro do documento '" + d.name() + "'.");
            }
        }

        Vendor v = new Vendor();
        fill(v, f);
        v.setCreatedBy(users.getReferenceById(currentUser.id()));
        v = vendors.save(v);
        long id = v.getId();

        for (int i = 0; i < contacts.size(); i++) {
            addContactEntity(v, contacts.get(i), i == 0);
        }
        for (DocForm d : docs) {
            documents.save("VENDOR", id, null, d.name(), files.get(d.fileIndex()), d.issueDate(), d.expiryDate(), id);
        }
        audit.log("VENDOR", id, id, "CREATE", null, get(id));
        return id;
    }

    @Transactional
    public void update(long id, VendorForm f) {
        Vendor v = vendors.findById(id).orElseThrow(() -> new ApiException("Vendor nao encontrado.", 404));
        if (!v.getStatus().equals("PENDING")) {
            throw new ApiException("So e possivel editar vendors com estado Pending.");
        }
        validateMain(f, id);
        Map<String, Object> before = get(id);
        fill(v, f);
        vendors.flush();
        audit.log("VENDOR", id, id, "UPDATE", before, get(id));
    }

    @Transactional
    public void review(long id, String action, String comment) {
        Vendor v = vendors.findById(id).orElseThrow(() -> new ApiException("Vendor nao encontrado.", 404));
        String st = v.getStatus();
        String next;
        switch (String.valueOf(action)) {
            case "START" -> {
                if (!st.equals("PENDING")) throw new ApiException("Apenas vendors Pending podem entrar em revisao.");
                next = "UNDER_REVIEW";
            }
            case "APPROVE" -> {
                if (!st.equals("PENDING") && !st.equals("UNDER_REVIEW")) throw new ApiException("Este vendor ja foi decidido.");
                next = "APPROVED";
            }
            case "REJECT" -> {
                if (!st.equals("PENDING") && !st.equals("UNDER_REVIEW")) throw new ApiException("Este vendor ja foi decidido.");
                if (blank(comment)) throw new ApiException("Indique o motivo da rejeicao.");
                next = "REJECTED";
            }
            default -> throw new ApiException("Accao desconhecida.");
        }
        v.setStatus(next);
        v.setReviewedBy(users.getReferenceById(currentUser.id()));
        v.setReviewedAt(OffsetDateTime.now());
        v.setReviewComment(nullIfBlank(comment));
        audit.log("VENDOR", id, id, "REVIEW_" + action, p("status", st), p("status", next, "comment", comment));
    }

    @Transactional
    public long addContact(long vendorId, ContactForm c) {
        Vendor v = vendors.findById(vendorId).orElseThrow(() -> new ApiException("Vendor nao encontrado.", 404));
        if (blank(c.fullName()) || blank(c.phone()) || blank(c.jobTitle())) {
            throw new ApiException("O contacto precisa de nome, telefone e funcao.");
        }
        boolean hasPrimary = contactRepo.existsByVendorIdAndPrimaryContactTrueAndActiveTrue(vendorId);
        long cid = addContactEntity(v, c, !hasPrimary);
        audit.log("VENDOR_CONTACT", cid, vendorId, "CREATE", null, c);
        return cid;
    }

    @Transactional
    public void removeContact(long vendorId, long contactId) {
        VendorContact c = contactRepo.findByIdAndVendorIdAndActiveTrue(contactId, vendorId)
            .orElseThrow(() -> new ApiException("Contacto nao encontrado.", 404));
        if (c.isPrimaryContact()) throw new ApiException("O contacto principal nao pode ser removido.");
        c.setActive(false);
        audit.log("VENDOR_CONTACT", contactId, vendorId, "DELETE", p("fullName", c.getFullName()), null);
    }

    private long addContactEntity(Vendor vendor, ContactForm f, boolean primary) {
        VendorContact c = new VendorContact();
        c.setVendor(vendor);
        c.setFullName(f.fullName().trim());
        c.setPhone(f.phone().trim());
        c.setJobTitle(f.jobTitle().trim());
        c.setEmail(nullIfBlank(f.email()));
        c.setPrimaryContact(primary);
        return contactRepo.save(c).getId();
    }
}
