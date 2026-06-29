package com.ruleforge.console;

/**
 * V7.7.2:ExternalProcessService 接口 stub — .rp 知识包审批流水线已废弃,
 * 但 CommonController.startApprovalProcess / updateFileInUseVersion 仍调本接口
 * 的 start() / syncExec() 方法。V1 决策流走 V1 原生发布(V7.6+),不经过审批。
 *
 * <p>保留接口签名(2 method)以维持 controller 编译,实现返 null + 错误信息。
 * 这两个 endpoint 调用方返回 {status:false, message:".rp 已废弃"}。
 */
public interface ExternalProcessService {

    /**
     * 启动 .rp 审批流程(已废弃)— V1 决策流走 V1PublishService.publish,无审批。
     */
    String start(String project, String title, String version, String targetVersion,
                 String remark, String explain, String fileName, String filePath,
                 String passRateEffect, Double passRateRange,
                 String badDebtRateEffect, Double badDebtRateRange);

    /**
     * 同步通知 executor(已废弃)— V1 决策流发布后由 V1PublishController 内部
     * 直接通知 V1ResourceProvider,不走本接口。
     */
    void syncExec(String packageId, String env, String username,
                  String passRateEffect, String passRateRange,
                  String badDebtRateEffect);
}
