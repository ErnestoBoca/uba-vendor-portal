package com.ubagroup.vendorportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ubagroup.vendorportal.entity.ServiceCategory;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Integer> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Integer id);
}
