package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Departamento (Corporate Services, Legal, Risk & Compliance, ...). Cada um tem um perfil APPROVER e, se hasOfficers, um OFFICER. */
@Entity @Table(name = "department") @Getter @Setter @NoArgsConstructor
public class Department {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "department_id")
    private Integer id;
    @Column(name = "code") private String code;
    @Column(name = "name") private String name;
    @Column(name = "has_officers") private boolean hasOfficers;
    @Column(name = "is_system") private boolean system;
    @Column(name = "is_active") private boolean active = true;
}
