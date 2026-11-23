package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity @Table(name = "approval_task_reassignment") @Getter @Setter @NoArgsConstructor
public class ApprovalTaskReassignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "reassignment_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "task_id") private ApprovalTask task;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "from_user_id") private AppUser fromUser;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "to_user_id") private AppUser toUser;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reassigned_by") private AppUser reassignedBy;
    @Column(name = "reason") private String reason;
    @Column(name = "reassigned_at") private OffsetDateTime reassignedAt;

    @PrePersist void onCreate() { reassignedAt = OffsetDateTime.now(); }
}
