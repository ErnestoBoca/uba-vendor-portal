package com.ubagroup.vendorportal.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ubagroup.vendorportal.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findByIdAndActiveTrue(Long id);
}
