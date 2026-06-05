package com.ruleforge.console.repository.data;

import com.ruleforge.console.entity.ApprovalTaskEntity;

import java.util.List;

/**
 * Data access repository for approval task entities.
 */
public interface ApprovalRepository {

    void insertTask(ApprovalTaskEntity entity);

    List<ApprovalTaskEntity> findPendingByProjectId(Long projectId);

    List<ApprovalTaskEntity> findByProjectId(Long projectId);

    ApprovalTaskEntity findById(Long id);

    void updateStatus(Long id, String status, String approver, String approveRemark);
}
