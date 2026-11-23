package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "app_role") @Getter @Setter @NoArgsConstructor
public class AppRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "role_id")
    private Integer id;
    @Column(name = "code") private String code;
    @Column(name = "name") private String name;
}
