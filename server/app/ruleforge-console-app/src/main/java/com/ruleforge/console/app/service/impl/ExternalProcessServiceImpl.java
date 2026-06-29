package com.ruleforge.console.app.service.impl;

import com.ruleforge.console.ExternalProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * V7.7.2:ExternalProcessService stub 实现 — .rp 知识包审批流水线已废弃。
 * 保留为 no-op 实现以维持 controller 编译。前端调用 startApprovalProcess /
 * updateFileInUseVersion 这两个 endpoint 会立即收到"已废弃"响应。
 *
 * <p>V1 决策流审批已废,V1 走 V1PublishController 内部 publish(直接发布即生效)。
 */
@Slf4j
@Service
public class ExternalProcessServiceImpl implements ExternalProcessService {

    @Override
    public String start(String project, String title, String version, String targetVersion,
                        String remark, String explain, String fileName, String filePath,
                        String passRateEffect, Double passRateRange,
                        String badDebtRateEffect, Double badDebtRateRange) {
        log.warn("ExternalProcessService.start called for project [{}] but .rp approval flow is deprecated (V7.7.2)", project);
        return null; // null processId → caller returns {status:false}
    }

    @Override
    public void syncExec(String packageId, String env, String username,
                         String passRateEffect, String passRateRange,
                         String badDebtRateEffect) {
        log.warn("ExternalProcessService.syncExec called for package [{}] but .rp flow is deprecated (V7.7.2)", packageId);
        // no-op
    }
}
