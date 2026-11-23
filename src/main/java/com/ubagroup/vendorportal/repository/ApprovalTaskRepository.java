package com.ubagroup.vendorportal.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ubagroup.vendorportal.entity.ApprovalTask;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {

    @Query("select t from ApprovalTask t where t.request.id = :requestId and t.decision = :decision order by t.stepNo")
    List<ApprovalTask> findByRequestAndDecision(@Param("requestId") Long requestId, @Param("decision") String decision);
}
