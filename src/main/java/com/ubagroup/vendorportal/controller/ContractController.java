package com.ubagroup.vendorportal.controller;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ubagroup.vendorportal.service.ApprovalService;
import com.ubagroup.vendorportal.service.ContractService;
import com.ubagroup.vendorportal.service.ContractService.ContractForm;
import com.ubagroup.vendorportal.service.DocumentService;
import com.ubagroup.vendorportal.service.VendorService;

@Controller
public class ContractController {

    private final ContractService contracts;
    private final VendorService vendors;
    private final DocumentService documents;
    private final ApprovalService approvals;

    public ContractController(ContractService contracts, VendorService vendors, DocumentService documents, ApprovalService approvals) {
        this.contracts = contracts;
        this.vendors = vendors;
        this.documents = documents;
        this.approvals = approvals;
    }

    // ---------- paginas ----------

    @GetMapping("/contracts")
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        model.addAttribute("active", "contracts");
        model.addAttribute("result", contracts.list(q, status, page, 10));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        return "contracts";
    }

    @GetMapping("/contracts/new")
    public String create(@RequestParam(required = false) Long vendorId, Model model) {
        fillForm(model);
        model.addAttribute("c", null);
        model.addAttribute("presetVendorId", vendorId);
        return "contract-form";
    }

    @GetMapping("/contracts/{id}/edit")
    public String edit(@PathVariable long id, Model model) {
        fillForm(model);
        model.addAttribute("c", contracts.get(id));
        return "contract-form";
    }

    @GetMapping("/contracts/{id}")
    public String detail(@PathVariable long id, Model model) {
        model.addAttribute("active", "contracts");
        model.addAttribute("c", contracts.get(id));
        model.addAttribute("docs", documents.list("CONTRACT", id));
        model.addAttribute("docTypes", documents.types("CONTRACT"));
        model.addAttribute("history", approvals.history("CONTRACT", id));
        model.addAttribute("users", approvals.activeUsers());
        return "contract-detail";
    }

    private void fillForm(Model model) {
        model.addAttribute("active", "contracts");
        model.addAttribute("vendorOptions", vendors.approvedForSelect());
        model.addAttribute("categories", vendors.categories());
        model.addAttribute("users", approvals.activeUsers());
    }

    // ---------- API ----------

    @PostMapping("/api/contracts")
    @ResponseBody
    public Map<String, Object> createApi(@RequestBody ContractForm form) {
        return Map.of("id", contracts.create(form));
    }

    @PutMapping("/api/contracts/{id}")
    @ResponseBody
    public Map<String, Object> update(@PathVariable long id, @RequestBody ContractForm form) {
        contracts.update(id, form);
        return Map.of("id", id);
    }

    @PostMapping("/api/contracts/{id}/submit")
    @ResponseBody
    public Map<String, Object> submit(@PathVariable long id) {
        return Map.of("requestId", approvals.submitContract(id));
    }
}
