package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Service Area: classifica vendors e contratos e define o fluxo de aprovacao. Gerida em Settings > Service Areas. */
@Entity @Table(name = "service_category") @Getter @Setter @NoArgsConstructor
public class ServiceCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "service_category_id")
    private Integer id;
    @Column(name = "name") private String name;
    @Column(name = "description") private String description;
    @Column(name = "is_active") private boolean active = true;
}
