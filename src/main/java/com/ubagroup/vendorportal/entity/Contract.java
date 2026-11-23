package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity @Table(name = "contract") @Getter @Setter @NoArgsConstructor
public class Contract {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "contract_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "vendor_id") private Vendor vendor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_category_id") private ServiceCategory serviceCategory;
    @Column(name = "contract_number") private String contractNumber;
    @Column(name = "title") private String title;
    @Column(name = "contract_type") private String contractType;
    @Column(name = "object_description") private String objectDescription;
    @Column(name = "signing_date") private LocalDate signingDate;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "value_amount") private BigDecimal valueAmount;
    @Column(name = "currency") private String currency = "MZN";
    @Column(name = "payment_terms") private String paymentTerms;
    @Column(name = "termination_notice_days") private Integer terminationNoticeDays;
    @Column(name = "penalty_clause") private String penaltyClause;
    @Column(name = "governing_law") private String governingLaw = "Mozambique";
    @Column(name = "sla_terms") private String slaTerms;
    @Column(name = "dpa_status") private String dpaStatus = "NOT_REQUIRED";
    @Column(name = "audit_rights") private boolean auditRights;
    @Column(name = "auto_renew") private boolean autoRenew;
    @Column(name = "renewal_notice_days") private Integer renewalNoticeDays = 60;
    @Column(name = "vendor_signatory") private String vendorSignatory;
    @Column(name = "bank_signatory") private String bankSignatory;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_user_id") private AppUser owner;
    @Column(name = "status") private String status = "DRAFT";
    @Column(name = "activated_at") private OffsetDateTime activatedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") private AppUser createdBy;
    @Column(name = "created_at") private OffsetDateTime createdAt;
    @Column(name = "updated_at") private OffsetDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
