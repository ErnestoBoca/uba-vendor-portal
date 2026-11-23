package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity @Table(name = "app_user") @Getter @Setter @NoArgsConstructor
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "user_id")
    private Long id;
    @Column(name = "username") private String username;
    @Column(name = "full_name") private String fullName;
    @Column(name = "email") private String email;
    @Column(name = "password_hash") private String passwordHash;
    @Column(name = "is_active") private boolean active = true;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<AppRole> roles = new HashSet<>();

    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); }
}
