package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity @Table(name = "approval_request") @Getter @Setter @NoArgsConstructor
public class ApprovalRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "request_id")
    private Long id;
    @Column(name = "entity_type") private String entityType;
    @Column(name = "entity_id") private Long entityId;
    @Column(name = "status") private String status = "IN_APPROVAL";
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submitted_by") private AppUser submittedBy;
    @Column(name = "submitted_at") private OffsetDateTime submittedAt;
    @Column(name = "closed_at") private OffsetDateTime closedAt;

    @PrePersist void onCreate() { submittedAt = OffsetDateTime.now(); }
}
