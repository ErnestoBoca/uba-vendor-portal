package com.ubagroup.vendorportal.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ubagroup.vendorportal.entity.AppRole;

public interface AppRoleRepository extends JpaRepository<AppRole, Integer> {
    Optional<AppRole> findByCode(String code);
    Optional<AppRole> findByDepartmentIdAndLevel(Integer departmentId, String level);
}
