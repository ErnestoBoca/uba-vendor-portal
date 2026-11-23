package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "approval_flow_step") @Getter @Setter @NoArgsConstructor
public class ApprovalFlowStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "step_id")
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "flow_id") private ApprovalFlow flow;
    @Column(name = "step_no") private Integer stepNo;
    @Column(name = "name") private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "approver_user_id") private AppUser approver;
    @Column(name = "is_active") private boolean active = true;
}
