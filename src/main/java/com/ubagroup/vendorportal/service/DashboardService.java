package com.ubagroup.vendorportal.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ubagroup.vendorportal.common.CurrentUser;
import com.ubagroup.vendorportal.common.Jpa;

import static com.ubagroup.vendorportal.common.Util.p;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final Jpa jpa;
    private final CurrentUser currentUser;
    private final SettingsService settings;

    public DashboardService(Jpa jpa, CurrentUser currentUser, SettingsService settings) {
        this.jpa = jpa;
        this.currentUser = currentUser;
        this.settings = settings;
    }

    public Map<String, Object> stats() {
        Map<String, Long> contracts = zero("DRAFT", "IN_APPROVAL", "ACTIVE", "REJECTED", "TERMINATED", "CLOSED");
        for (Object[] r : jpa.rows("select c.status, count(c) from Contract c group by c.status", Map.of())) {
            contracts.put((String) r[0], ((Number) r[1]).longValue());
        }
        Map<String, Long> vendors = zero("PENDING", "UNDER_REVIEW", "APPROVED", "REJECTED");
        for (Object[] r : jpa.rows("select v.status, count(v) from Vendor v group by v.status", Map.of())) {
            vendors.put((String) r[0], ((Number) r[1]).longValue());
        }
        int warn = settings.integer("contract_expiry_warning_days", 60);
        long expiring = jpa.count("select count(c) from Contract c where c.status = 'ACTIVE' and c.endDate between :today and :limit",
            p("today", LocalDate.now(), "limit", LocalDate.now().plusDays(warn)));
        long mine = jpa.count("select count(t) from ApprovalTask t where t.decision = 'PENDING' and t.approver.id = :u",
            p("u", currentUser.id()));

        List<Map<String, Object>> areas = new ArrayList<>();
        for (Object[] r : jpa.rows("select sc.name, count(v) from Vendor v join v.serviceCategory sc "
                + "group by sc.name order by count(v) desc, sc.name", Map.of())) {
            areas.add(p("name", r[0], "n", ((Number) r[1]).longValue()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contracts", contracts);
        out.put("vendors", vendors);
        out.put("expiring", expiring);
        out.put("expiryWarningDays", warn);
        out.put("myPending", mine);
        out.put("vendorsByArea", areas);
        return out;
    }

    private static Map<String, Long> zero(String... keys) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String k : keys) m.put(k, 0L);
        return m;
    }
}
