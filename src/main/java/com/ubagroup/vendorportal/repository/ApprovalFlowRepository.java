package com.ubagroup.vendorportal.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ubagroup.vendorportal.entity.ApprovalFlow;

public interface ApprovalFlowRepository extends JpaRepository<ApprovalFlow, Integer> {
    Optional<ApprovalFlow> findByEntityTypeAndScopeId(String entityType, Long scopeId);
}
