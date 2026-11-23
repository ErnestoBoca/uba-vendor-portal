package com.ubagroup.vendorportal.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.entity.SystemSetting;
import com.ubagroup.vendorportal.repository.SystemSettingRepository;

@Service
@Transactional(readOnly = true)
public class SettingsService {

    private static final Set<String> EDITABLE = Set.of("enforce_segregation_of_duties", "contract_expiry_warning_days");

    private final SystemSettingRepository settings;
    private final AuditService audit;

    public SettingsService(SystemSettingRepository settings, AuditService audit) {
        this.settings = settings;
        this.audit = audit;
    }

    public Map<String, String> all() {
        Map<String, String> m = new LinkedHashMap<>();
        for (SystemSetting s : settings.findAll(Sort.by("settingKey"))) {
            m.put(s.getSettingKey(), s.getSettingValue());
        }
        return m;
    }

    public boolean bool(String key) {
        return "true".equalsIgnoreCase(all().get(key));
    }

    public int integer(String key, int fallback) {
        try {
            return Integer.parseInt(all().get(key));
        } catch (Exception e) {
            return fallback;
        }
    }

    @Transactional
    public void update(Map<String, String> values) {
        Map<String, String> before = all();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!EDITABLE.contains(e.getKey())) {
                throw new ApiException("Definicao desconhecida: " + e.getKey());
            }
            String v = e.getValue() == null ? "" : e.getValue().trim();
            if (e.getKey().equals("enforce_segregation_of_duties") && !v.equals("true") && !v.equals("false")) {
                throw new ApiException("Valor invalido para segregacao de funcoes.");
            }
            if (e.getKey().equals("contract_expiry_warning_days")) {
                try {
                    int n = Integer.parseInt(v);
                    if (n < 1 || n > 365) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException ex) {
                    throw new ApiException("Dias de aviso deve ser um numero entre 1 e 365.");
                }
            }
            SystemSetting s = settings.findById(e.getKey()).orElseThrow(() -> new ApiException("Definicao desconhecida."));
            s.setSettingValue(v);
        }
        settings.flush();
        audit.log("SETTING", null, null, "UPDATE", before, all());
    }
}
