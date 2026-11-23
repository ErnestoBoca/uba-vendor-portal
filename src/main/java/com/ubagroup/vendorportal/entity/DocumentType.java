package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "document_type") @Getter @Setter @NoArgsConstructor
public class DocumentType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "document_type_id")
    private Integer id;
    @Column(name = "entity_type") private String entityType;
    @Column(name = "name") private String name;
    @Column(name = "is_active") private boolean active = true;
}
