package com.ubagroup.vendorportal.service;

import java.sql.Types;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ubagroup.vendorportal.common.CurrentUser;

/**
 * Regista quem fez o que (audit_log, append-only). Usa JDBC directo de proposito: a tabela tem um trigger
 * INSTEAD OF UPDATE/DELETE e nunca e' alterada, por isso nao precisa de entidade de escrita.
 */
@Service
public class AuditService {

    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final ObjectMapper om;

    public AuditService(JdbcTemplate jdbc, CurrentUser currentUser, ObjectMapper om) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.om = om;
    }

    @Transactional
    public void log(String entityType, Object entityId, Long vendorId, String action, Object oldData, Object newData) {
        String ip = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            ip = sra.getRequest().getRemoteAddr();
        }
        jdbc.update("INSERT INTO dbo.audit_log(actor_user_id, actor_ip, entity_type, entity_id, vendor_id, action, old_data, new_data) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            new Object[] {currentUser.id(), ip, entityType, entityId == null ? null : String.valueOf(entityId), vendorId,
                          action, json(oldData), json(newData)},
            new int[] {Types.BIGINT, Types.NVARCHAR, Types.NVARCHAR, Types.NVARCHAR, Types.BIGINT,
                       Types.NVARCHAR, Types.NVARCHAR, Types.NVARCHAR});
    }

    private String json(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
