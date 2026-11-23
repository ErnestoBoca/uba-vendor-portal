package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity @Table(name = "vendor") @Getter @Setter @NoArgsConstructor
public class Vendor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "vendor_id")
    private Long id;
    /** Coluna calculada na BD (UBA-VND-000001): so leitura. */
    @Column(name = "vendor_code", insertable = false, updatable = false) private String vendorCode;
    @Column(name = "supplier_name") private String supplierName;
    @Column(name = "email") private String email;
    @Column(name = "mobile") private String mobile;
    @Column(name = "phone") private String phone;
    @Column(name = "address") private String address;
    @Column(name = "nuit") private String nuit;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_category_id")
    private ServiceCategory serviceCategory;
    @Column(name = "service_description") private String serviceDescription;
    @Column(name = "company_reg_number") private String companyRegNumber;
    @Column(name = "nuit_verified") private boolean nuitVerified = false;
    @Column(name = "status") private String status = "PENDING";
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by") private AppUser reviewedBy;
    @Column(name = "reviewed_at") private OffsetDateTime reviewedAt;
    @Column(name = "review_comment") private String reviewComment;
    @Column(name = "is_suspended") private boolean suspended = false;
    @Column(name = "suspended_reason") private String suspendedReason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") private AppUser createdBy;
    @Column(name = "created_at") private OffsetDateTime createdAt;
    @Column(name = "updated_at") private OffsetDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
