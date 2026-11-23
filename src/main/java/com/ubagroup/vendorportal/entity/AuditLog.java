package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import org.hibernate.annotations.Immutable;

/** So leitura: as gravacoes sao feitas pelo AuditService (a tabela e' append-only, com trigger). */
@Entity @Immutable @Table(name = "audit_log") @Getter @NoArgsConstructor
public class AuditLog {
    @Id @Column(name = "audit_id") private Long id;
    @Column(name = "occurred_at") private OffsetDateTime occurredAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_user_id") private AppUser actor;
    @Column(name = "entity_type") private String entityType;
    @Column(name = "entity_id") private String entityId;
    @Column(name = "vendor_id") private Long vendorId;
    @Column(name = "action") private String action;
}
