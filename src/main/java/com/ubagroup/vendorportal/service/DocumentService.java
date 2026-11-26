package com.ubagroup.vendorportal.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.common.CurrentUser;
import com.ubagroup.vendorportal.common.FileStorage;
import com.ubagroup.vendorportal.common.Jpa;
import com.ubagroup.vendorportal.entity.Document;
import com.ubagroup.vendorportal.repository.AppUserRepository;
import com.ubagroup.vendorportal.repository.DocumentRepository;
import com.ubagroup.vendorportal.repository.DocumentTypeRepository;

import static com.ubagroup.vendorportal.common.Util.p;

/** Tabela unica de documentos (document), usada por VENDOR, CONTRACT e, no futuro, ASSET. */
@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documents;
    private final DocumentTypeRepository types;
    private final AppUserRepository users;
    private final Jpa jpa;
    private final FileStorage storage;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public DocumentService(DocumentRepository documents, DocumentTypeRepository types, AppUserRepository users, Jpa jpa,
                           FileStorage storage, CurrentUser currentUser, AuditService audit) {
        this.documents = documents;
        this.types = types;
        this.users = users;
        this.jpa = jpa;
        this.storage = storage;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public List<Map<String, Object>> list(String entityType, long entityId) {
        return jpa.list("select new map(d.id as id, d.name as name, d.fileName as fileName, d.fileSizeBytes as sizeBytes, "
            + "dt.name as typeName, d.issueDate as issueDate, d.expiryDate as expiryDate, d.uploadedAt as uploadedAt, u.fullName as uploadedBy) "
            + "from Document d join d.uploadedBy u left join d.documentType dt "
            + "where d.entityType = :et and d.entityId = :eid and d.active = true order by d.uploadedAt desc",
            p("et", entityType, "eid", entityId));
    }

    public List<Map<String, Object>> types(String entityType) {
        return jpa.list("select new map(t.id as id, t.name as name) from DocumentType t where t.entityType = :et and t.active = true order by t.name",
            p("et", entityType));
    }

    @Transactional
    public long save(String entityType, long entityId, Integer documentTypeId, String name, MultipartFile file,
                     LocalDate issue, LocalDate expiry, Long vendorIdForAudit) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("Seleccione um ficheiro para o documento '" + name + "'.");
        }
        if (name == null || name.isBlank()) {
            throw new ApiException("O nome do documento e obrigatorio.");
        }
        if (issue != null && expiry != null && expiry.isBefore(issue)) {
            throw new ApiException("A data de validade nao pode ser anterior a data de emissao (" + name + ").");
        }
        try {
            byte[] bytes = file.getBytes();
            Document d = new Document();
            d.setEntityType(entityType);
            d.setEntityId(entityId);
            if (documentTypeId != null) {
                d.setDocumentType(types.getReferenceById(documentTypeId));
            }
            d.setName(name.trim());
            d.setFileName(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
            d.setStorageKey(storage.store(file.getOriginalFilename(), bytes));
            d.setMimeType(file.getContentType());
            d.setFileSizeBytes(file.getSize());
            d.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            d.setIssueDate(issue);
            d.setExpiryDate(expiry);
            d.setStage(stageOf(entityType, entityId));
            d.setUploadedBy(users.getReferenceById(currentUser.id()));
            d = documents.save(d);
            audit.log("DOCUMENT", d.getId(), vendorIdForAudit, "UPLOAD", null,
                p("entityType", entityType, "entityId", entityId, "name", d.getName(), "file", d.getFileName()));
            return d.getId();
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new ApiException("Nao foi possivel guardar o ficheiro: " + e.getMessage(), 500);
        }
    }

    /** Estado actual do dono do documento (vendor ou contrato): fica gravado como a fase do upload. */
    private String stageOf(String entityType, long entityId) {
        String jpql = entityType.equals("VENDOR")
            ? "select new map(v.status as s) from Vendor v where v.id = :id"
            : "select new map(c.status as s) from Contract c where c.id = :id";
        Map<String, Object> m = jpa.one(jpql, p("id", entityId));
        return m == null ? null : (String) m.get("s");
    }

    /**
     * Todos os documentos de um vendor, em todas as fases: os do proprio vendor (onboarding) e os dos seus contratos,
     * com quem os submeteu, quando e em que estado estava a entidade. Inclui os removidos (active = false) para rasto.
     */
    public List<Map<String, Object>> forVendor(long vendorId) {
        List<Map<String, Object>> all = new ArrayList<>();
        List<Map<String, Object>> own = jpa.list("select new map(d.id as id, d.name as name, d.fileName as fileName, "
            + "dt.name as typeName, d.issueDate as issueDate, d.expiryDate as expiryDate, d.uploadedAt as uploadedAt, "
            + "u.fullName as uploadedBy, d.stage as stage, d.active as active, v.id as originId) "
            + "from Document d join d.uploadedBy u left join d.documentType dt join Vendor v on v.id = d.entityId "
            + "where d.entityType = 'VENDOR' and v.id = :vid", p("vid", vendorId));
        for (Map<String, Object> m : own) {
            m.put("originType", "VENDOR");
            m.put("origin", "Company");
        }
        List<Map<String, Object>> ofContracts = jpa.list("select new map(d.id as id, d.name as name, d.fileName as fileName, "
            + "dt.name as typeName, d.issueDate as issueDate, d.expiryDate as expiryDate, d.uploadedAt as uploadedAt, "
            + "u.fullName as uploadedBy, d.stage as stage, d.active as active, c.id as originId, c.contractNumber as origin) "
            + "from Document d join d.uploadedBy u left join d.documentType dt join Contract c on c.id = d.entityId "
            + "where d.entityType = 'CONTRACT' and c.vendor.id = :vid", p("vid", vendorId));
        for (Map<String, Object> m : ofContracts) {
            m.put("originType", "CONTRACT");
        }
        all.addAll(own);
        all.addAll(ofContracts);
        all.sort(Comparator.comparing((Map<String, Object> m) -> (OffsetDateTime) m.get("uploadedAt")).reversed());
        return all;
    }

    public Map<String, Object> find(long id) {
        return jpa.one("select new map(d.id as id, d.entityType as entityType, d.entityId as entityId, d.name as name, d.fileName as fileName, "
            + "d.storageKey as storageKey, d.mimeType as mimeType) from Document d where d.id = :id and d.active = true", p("id", id));
    }

    public Resource load(String storageKey) {
        try {
            return storage.load(storageKey);
        } catch (IOException e) {
            throw new ApiException("Ficheiro nao encontrado no armazenamento.", 404);
        }
    }

    @Transactional
    public void deactivate(long id) {
        Document d = documents.findByIdAndActiveTrue(id).orElseThrow(() -> new ApiException("Documento nao encontrado.", 404));
        d.setActive(false);
        audit.log("DOCUMENT", id, null, "DELETE", p("name", d.getName(), "file", d.getFileName()), null);
    }
}
