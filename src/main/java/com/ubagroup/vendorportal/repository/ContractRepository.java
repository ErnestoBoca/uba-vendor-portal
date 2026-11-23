package com.ubagroup.vendorportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ubagroup.vendorportal.entity.Contract;

public interface ContractRepository extends JpaRepository<Contract, Long> { }
