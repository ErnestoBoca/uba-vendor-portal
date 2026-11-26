package com.ubagroup.vendorportal.controller;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.service.ApprovalService;
import com.ubagroup.vendorportal.service.ContractService;
import com.ubagroup.vendorportal.service.DocumentService;
import com.ubagroup.vendorportal.service.VendorService;
import com.ubagroup.vendorportal.service.VendorService.ContactForm;
import com.ubagroup.vendorportal.service.VendorService.VendorForm;

/** Paginas de Vendor Onboarding (Thymeleaf) e os endpoints JSON /api/vendors chamados pelo JavaScript. */
@Controller
public class VendorController {

    private final VendorService vendors;
    private final ContractService contracts;
    private final DocumentService documents;
    private final ApprovalService approvals;
    private final ObjectMapper om;

    public VendorController(VendorService vendors, ContractService contracts, DocumentService documents,
                            ApprovalService approvals, ObjectMapper om) {
        this.vendors = vendors;
        this.contracts = contracts;
        this.documents = documents;
        this.approvals = approvals;
        this.om = om;
    }

    // ---------- paginas ----------

    @GetMapping("/vendors")
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Integer category,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        model.addAttribute("active", "vendors");
        model.addAttribute("result", vendors.list(q, status, category, page, 10));
        model.addAttribute("categories", vendors.categories());
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("category", category);
        return "vendors";
    }

    @GetMapping("/vendors/{id}")
    public String detail(@PathVariable long id, Model model) {
        Map<String, Object> v = vendors.get(id);
        model.addAttribute("active", "vendors");
        model.addAttribute("v", v);
        model.addAttribute("contacts", vendors.contacts(id));
        model.addAttribute("docs", documents.list("VENDOR", id));
        model.addAttribute("docTypes", documents.types("VENDOR"));
        model.addAttribute("allDocs", documents.forVendor(id));
        model.addAttribute("vendorFlow", approvals.plannedFlow("CONTRACT", ((Number) v.get("serviceCategoryId")).longValue()));
        model.addAttribute("contractRuns", approvals.contractRuns(id));
        model.addAttribute("history", vendors.history(id));
        model.addAttribute("contracts", contracts.forVendor(id));
        model.addAttribute("categories", vendors.categories());
        return "vendor-detail";
    }

    // ---------- API ----------

    /** Registo: multipart com "data" (JSON do VendorForm) e "files" (um por documento, indexados por fileIndex). */
    @PostMapping(value = "/api/vendors", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, Object> create(@RequestParam("data") String data,
                                      @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        VendorForm form;
        try {
            form = om.readValue(data, VendorForm.class);
        } catch (Exception e) {
            throw new ApiException("Dados do formulario invalidos.");
        }
        return Map.of("id", vendors.create(form, files));
    }

    @PutMapping("/api/vendors/{id}")
    @ResponseBody
    public Map<String, Object> update(@PathVariable long id, @RequestBody VendorForm form) {
        vendors.update(id, form);
        return Map.of("id", id);
    }

    @PostMapping("/api/vendors/{id}/review")
    @ResponseBody
    public Map<String, Object> review(@PathVariable long id, @RequestBody Map<String, String> body) {
        vendors.review(id, body.get("action"), body.get("comment"));
        return Map.of("ok", true);
    }

    @PostMapping("/api/vendors/{id}/contacts")
    @ResponseBody
    public Map<String, Object> addContact(@PathVariable long id, @RequestBody ContactForm form) {
        return Map.of("id", vendors.addContact(id, form));
    }

    @DeleteMapping("/api/vendors/{id}/contacts/{contactId}")
    @ResponseBody
    public Map<String, Object> removeContact(@PathVariable long id, @PathVariable long contactId) {
        vendors.removeContact(id, contactId);
        return Map.of("ok", true);
    }

    @GetMapping("/api/vendors/check-nuit")
    @ResponseBody
    public Map<String, Object> checkNuit(@RequestParam String nuit, @RequestParam(required = false) Long exceptId) {
        return Map.of("exists", vendors.nuitExists(nuit, exceptId));
    }
}
