package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "service_category") @Getter @Setter @NoArgsConstructor
public class ServiceCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "service_category_id")
    private Integer id;
    @Column(name = "name") private String name;
    @Column(name = "is_active") private boolean active = true;
}
