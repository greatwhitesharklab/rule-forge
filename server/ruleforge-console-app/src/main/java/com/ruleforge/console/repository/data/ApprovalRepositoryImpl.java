package com.ruleforge.console.repository.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruleforge.console.entity.ApprovalTaskEntity;
import com.ruleforge.console.mapper.ApprovalTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalRepositoryImpl implements ApprovalRepository {

    private final ApprovalTaskMapper approvalTaskMapper;

    @Override
    public void insertTask(ApprovalTaskEntity entity) {
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        approvalTaskMapper.insert(entity);
    }

    @Override
    public List<ApprovalTaskEntity> findPendingByProjectId(Long projectId) {
        return approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProjectId, projectId)
                .eq(ApprovalTaskEntity::getStatus, "pending")
                .orderByDesc(ApprovalTaskEntity::getCreateTime));
    }

    @Override
    public List<ApprovalTaskEntity> findByProjectId(Long projectId) {
        return approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProjectId, projectId)
                .orderByDesc(ApprovalTaskEntity::getCreateTime));
    }

    @Override
    public ApprovalTaskEntity findById(Long id) {
        return approvalTaskMapper.selectById(id);
    }

    @Override
    public void updateStatus(Long id, String status, String approver, String approveRemark) {
        approvalTaskMapper.update(null, new LambdaUpdateWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getId, id)
                .set(ApprovalTaskEntity::getStatus, status)
                .set(ApprovalTaskEntity::getApprover, approver)
                .set(ApprovalTaskEntity::getApproveRemark, approveRemark)
                .set(ApprovalTaskEntity::getApproveTime, new Date())
                .set(ApprovalTaskEntity::getUpdateTime, new Date()));
    }
}
