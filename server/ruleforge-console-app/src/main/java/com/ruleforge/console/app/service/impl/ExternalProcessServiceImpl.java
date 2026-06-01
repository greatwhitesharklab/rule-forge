package com.ruleforge.console.app.service.impl;

import com.ruleforge.console.ExternalProcessService;
import com.ruleforge.console.entity.ApprovalTaskEntity;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.repository.data.ApprovalRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.util.EnvironmentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Fred
 * 2019-12-27 3:50 PM
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalProcessServiceImpl implements ExternalProcessService {

    private final RestTemplate execRestTemplate;
    private final ApprovalRepository approvalRepository;
    private final ProjectRepository projectRepository;

    @Value("${ruleforge.approval.mode:auto}")
    private String approvalMode;

    @Override
    public void syncExec(String fullPackageId, String env, String username, Integer proportion, Date start, Date end) {
        log.info("syncExec {} {} {}", fullPackageId, env, proportion);
        try {
            String url = "/test/knowledge";
            Map<String, String> params = new HashMap<>();
            params.put("packageId", fullPackageId);
            params.put("env", env);
            this.execRestTemplate.postForObject(url, params, Void.class);
            log.info("Successfully notified executor for package [{}]", fullPackageId);
        } catch (Exception e) {
            log.error("Failed to notify executor for package [{}]: {}", fullPackageId, e.getMessage());
        }
    }

    @Override
    public String start(String project,
                        String title,
                        String nowVersion,
                        String version,
                        String explain,
                        String remark,
                        String fileName,
                        String filePath,
                        String passRateEffect,
                        Double passRateRange,
                        String badDebtRateEffect,
                        Double badDebtRateRange) throws Exception {
        log.info("{} ,{}, {}, {}, {}", project, version, explain, fileName, filePath);
        if (StringUtils.isEmpty("")) {
        }

        ProjectEntity projectEntity = projectRepository.findByName(project);
        String processId = UUID.randomUUID().toString();

        ApprovalTaskEntity task = new ApprovalTaskEntity();
        task.setProjectId(projectEntity != null ? projectEntity.getId() : null);
        task.setTitle(title);
        task.setProjectVersion(version);
        task.setExecEnv("prod");
        task.setApprovalType("deploy");
        task.setExplainText(explain);
        task.setRemark(remark);
        task.setRequester(EnvironmentUtils.getLoginUser(null) != null
                ? EnvironmentUtils.getLoginUser(null).getUsername() : null);

        if ("manual".equalsIgnoreCase(approvalMode)) {
            task.setStatus("pending");
            task.setProcessId(processId);
            approvalRepository.insertTask(task);
            log.info("Manual approval mode: created pending task [{}] for project [{}]", processId, project);
            return processId;
        } else {
            task.setStatus("approved");
            task.setProcessId("autoProcess");
            approvalRepository.insertTask(task);
            log.info("Auto approval mode: auto-approved task for project [{}]", project);
            return "autoProcess";
        }
    }

    @Override
    public String testStart(String title, String project, String fileName, Date startTime, Date endTime,
                            String version, Integer testRate, String remark, String explain) throws Exception {
        ProjectEntity projectEntity = projectRepository.findByName(project);
        String processId = UUID.randomUUID().toString();

        ApprovalTaskEntity task = new ApprovalTaskEntity();
        task.setProjectId(projectEntity != null ? projectEntity.getId() : null);
        task.setTitle(title);
        task.setProjectVersion(version);
        task.setExecEnv("test");
        task.setApprovalType("test_deploy");
        task.setExplainText(explain);
        task.setRemark(remark);
        task.setRequester(EnvironmentUtils.getLoginUser(null) != null
                ? EnvironmentUtils.getLoginUser(null).getUsername() : null);

        if ("manual".equalsIgnoreCase(approvalMode)) {
            task.setStatus("pending");
            task.setProcessId(processId);
            approvalRepository.insertTask(task);
            log.info("Manual approval mode: created pending test task [{}] for project [{}]", processId, project);
            return processId;
        } else {
            task.setStatus("approved");
            task.setProcessId("autoProcess");
            approvalRepository.insertTask(task);
            log.info("Auto approval mode: auto-approved test task for project [{}]", project);
            return "autoProcess";
        }
    }
}
