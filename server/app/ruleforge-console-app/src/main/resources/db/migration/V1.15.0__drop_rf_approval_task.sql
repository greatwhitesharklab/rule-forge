-- V7.7.2:废弃老 .rp 知识包审批流水线。
-- 唯一引用 rf_approval_task 的代码(ApprovalController / ExternalProcessServiceImpl /
-- ApprovalRepository)已全删,V1 原生发布(V7.6+)无审批,直接发布即生效。
-- 删表 — 表无 consumer,无 FK 引用,直接 drop。
DROP TABLE IF EXISTS rf_approval_task;