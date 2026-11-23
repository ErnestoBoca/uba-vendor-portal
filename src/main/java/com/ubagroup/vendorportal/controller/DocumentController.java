package com.ubagroup.vendorportal.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.common.CurrentUser;
import com.ubagroup.vendorportal.common.Util;
import com.ubagroup.vendorportal.service.ContractService;
import com.ubagroup.vendorportal.service.DocumentService;

@Controller
public class DocumentController {

    private static final Set<String> ALLOWED = Set.of("VENDOR", "CONTRACT");

    private final DocumentService documents;
    private final ContractService contracts;
    private final CurrentUser currentUser;

    public DocumentController(DocumentService documents, ContractService contracts, CurrentUser currentUser) {
        this.documents = documents;
        this.contracts = contracts;
        this.currentUser = currentUser;
    }

    /** Documentos de vendor: quem edita vendors; de contrato: quem edita contratos. */
    private void requireEdit(String entityType) {
        if ("VENDOR".equals(entityType)) currentUser.requireVendorEdit();
        else currentUser.requireContractEdit();
    }

    @PostMapping(value = "/api/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, Object> upload(@RequestParam String entityType,
                                      @RequestParam long entityId,
                                      @RequestParam String name,
                                      @RequestParam(required = false) Integer documentTypeId,
                                      @RequestParam(required = false) String issueDate,
                                      @RequestParam(required = false) String expiryDate,
                                      @RequestParam("file") MultipartFile file) {
        if (!ALLOWED.contains(entityType)) {
            throw new ApiException("Tipo de entidade nao suportado.");
        }
        requireEdit(entityType);
        Long vendorId = entityType.equals("VENDOR") ? Long.valueOf(entityId) : Util.num(contracts.get(entityId).get("vendorId"));
        long id = documents.save(entityType, entityId, documentTypeId, name, file, parse(issueDate), parse(expiryDate), vendorId);
        return Map.of("id", id);
    }

    @DeleteMapping("/api/documents/{id}")
    @ResponseBody
    public Map<String, Object> remove(@PathVariable long id) {
        Map<String, Object> d = documents.find(id);
        if (d == null) throw new ApiException("Documento nao encontrado.", 404);
        requireEdit((String) d.get("entityType"));
        documents.deactivate(id);
        return Map.of("ok", true);
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        Map<String, Object> d = documents.find(id);
        if (d == null) {
            throw new ApiException("Documento nao encontrado.", 404);
        }
        Resource r = documents.load((String) d.get("storageKey"));
        String mime = d.get("mimeType") == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : (String) d.get("mimeType");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(mime))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename((String) d.get("fileName")).build().toString())
            .body(r);
    }

    private static LocalDate parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new ApiException("Data invalida: " + s);
        }
    }
}
