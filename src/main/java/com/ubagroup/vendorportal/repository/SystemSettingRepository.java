package com.ubagroup.vendorportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ubagroup.vendorportal.entity.SystemSetting;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> { }
