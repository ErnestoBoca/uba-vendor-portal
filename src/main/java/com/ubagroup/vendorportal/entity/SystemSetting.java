package com.ubagroup.vendorportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "system_setting") @Getter @Setter @NoArgsConstructor
public class SystemSetting {
    @Id @Column(name = "setting_key") private String settingKey;
    @Column(name = "setting_value") private String settingValue;
    @Column(name = "description") private String description;
}
