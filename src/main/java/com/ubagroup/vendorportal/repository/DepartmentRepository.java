package com.ubagroup.vendorportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ubagroup.vendorportal.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Integer> {
    boolean existsByCode(String code);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Integer id);
}
