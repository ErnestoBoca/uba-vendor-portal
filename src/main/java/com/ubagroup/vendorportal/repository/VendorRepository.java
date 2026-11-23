package com.ubagroup.vendorportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ubagroup.vendorportal.entity.Vendor;

public interface VendorRepository extends JpaRepository<Vendor, Long> {
    boolean existsByNuit(String nuit);
    boolean existsByNuitAndIdNot(String nuit, Long id);
}
