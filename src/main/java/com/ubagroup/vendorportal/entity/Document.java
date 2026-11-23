package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Tabela unica de documentos: entity_type + entity_id (VENDOR, CONTRACT, futuramente ASSET). */
@Entity @Table(name = "document") @Getter @Setter @NoArgsConstructor
public class Document {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "document_id")
    private Long id;
    @Column(name = "entity_type") private String entityType;
    @Column(name = "entity_id") private Long entityId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "document_type_id") private DocumentType documentType;
    @Column(name = "name") private String name;
    @Column(name = "file_name") private String fileName;
    @Column(name = "storage_key") private String storageKey;
    @Column(name = "mime_type") private String mimeType;
    @Column(name = "file_size_bytes") private Long fileSizeBytes;
    @Column(name = "sha256") private String sha256;
    @Column(name = "issue_date") private LocalDate issueDate;
    @Column(name = "expiry_date") private LocalDate expiryDate;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploaded_by") private AppUser uploadedBy;
    @Column(name = "uploaded_at") private OffsetDateTime uploadedAt;
    @Column(name = "is_active") private boolean active = true;

    @PrePersist void onCreate() { uploadedAt = OffsetDateTime.now(); }
}
