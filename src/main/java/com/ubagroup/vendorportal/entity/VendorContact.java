package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "vendor_contact") @Getter @Setter @NoArgsConstructor
public class VendorContact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "contact_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "vendor_id") private Vendor vendor;
    @Column(name = "full_name") private String fullName;
    @Column(name = "phone") private String phone;
    @Column(name = "job_title") private String jobTitle;
    @Column(name = "email") private String email;
    @Column(name = "is_primary") private boolean primaryContact;
    @Column(name = "is_active") private boolean active = true;
}
