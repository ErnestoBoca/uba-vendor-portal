package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity @Table(name = "approval_task") @Getter @Setter @NoArgsConstructor
public class ApprovalTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "task_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "request_id") private ApprovalRequest request;
    @Column(name = "step_no") private Integer stepNo;
    @Column(name = "step_name") private String stepName;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "approver_user_id") private AppUser approver;
    @Column(name = "decision") private String decision = "WAITING";
    @Column(name = "decided_at") private OffsetDateTime decidedAt;
    @Column(name = "comments") private String comments;
}
