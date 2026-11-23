package com.ubagroup.vendorportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ubagroup.vendorportal.entity.ApprovalRequest;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> { }
