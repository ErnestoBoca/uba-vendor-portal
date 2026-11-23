package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "approval_flow") @Getter @Setter @NoArgsConstructor
public class ApprovalFlow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "flow_id")
    private Integer id;
    @Column(name = "entity_type") private String entityType;
    /** 0 = fluxo por defeito; senao o id da area de servico. */
    @Column(name = "scope_id") private Long scopeId = 0L;
    @Column(name = "name") private String name;
    @Column(name = "is_active") private boolean active = true;
}
