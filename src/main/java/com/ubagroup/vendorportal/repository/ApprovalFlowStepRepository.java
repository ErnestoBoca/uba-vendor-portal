package com.ubagroup.vendorportal.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ubagroup.vendorportal.entity.ApprovalFlowStep;

public interface ApprovalFlowStepRepository extends JpaRepository<ApprovalFlowStep, Integer> {

    /** Passos activos do fluxo (entityType + scope), por ordem. */
    @Query("select s from ApprovalFlowStep s join fetch s.approver where s.flow.entityType = :et and s.flow.scopeId = :scope "
         + "and s.flow.active = true and s.active = true order by s.stepNo")
    List<ApprovalFlowStep> activeSteps(@Param("et") String entityType, @Param("scope") Long scope);

    /** Apaga logo na BD (antes de inserir os novos), para nao colidir com a chave (flow_id, step_no). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ApprovalFlowStep s where s.flow.id = :flowId")
    void deleteAllByFlowId(@Param("flowId") Integer flowId);
}
