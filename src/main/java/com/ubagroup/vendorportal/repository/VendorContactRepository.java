package com.ubagroup.vendorportal.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ubagroup.vendorportal.entity.VendorContact;

public interface VendorContactRepository extends JpaRepository<VendorContact, Long> {
    Optional<VendorContact> findByIdAndVendorIdAndActiveTrue(Long id, Long vendorId);
    boolean existsByVendorIdAndPrimaryContactTrueAndActiveTrue(Long vendorId);
}
