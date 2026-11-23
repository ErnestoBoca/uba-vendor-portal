package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Perfil de acesso. level: ADMIN (sistema), OFFICER (prepara/regista), APPROVER (aprova), VIEWER (so leitura).
 * Perfis de departamento: code = DEPT_OFFICER / DEPT_APPROVER.
 */
@Entity @Table(name = "app_role") @Getter @Setter @NoArgsConstructor
public class AppRole {
    public static final String ADMIN = "ADMIN";
    public static final String OFFICER = "OFFICER";
    public static final String APPROVER = "APPROVER";
    public static final String VIEWER = "VIEWER";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "role_id")
    private Integer id;
    @Column(name = "code") private String code;
    @Column(name = "name") private String name;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "department_id") private Department department;
    @Column(name = "role_level") private String level = VIEWER;
    @Column(name = "is_active") private boolean active = true;
}
