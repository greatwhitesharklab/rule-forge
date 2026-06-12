package com.ruleforge.console.flow.controller;

import com.ruleforge.Utils;
import com.ruleforge.console.model.User;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.flow.converter.FlowXmlConverter;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import com.ruleforge.exception.RuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/flow")
@RequiredArgsConstructor
public class BpmnFlowController {

    private final RuleForgeRepositoryService repositoryService;
    private final FlowXmlConverter flowXmlConverter;
    private final BpmnXmlParser bpmnXmlParser;
    private final RestTemplate execRestTemplate;

    @Value("${ruleforge.exec.url}")
    private String execUrl;

    @GetMapping(value = "/load", produces = "text/xml;charset=UTF-8")
    public ResponseEntity<String> loadBpmn(@RequestParam String file,
                           @RequestParam(required = false) String version) {
        try {
            InputStream inputStream;
            if (StringUtils.isEmpty(version)) {
                inputStream = repositoryService.readFile(file, null);
            } else {
                inputStream = repositoryService.readFile(file, version);
            }
            // V5.9.x: readFile 在 Git 不可用 + DB 也没 content 时静默返 null (saveFile 不存 content 到 DB)
            // 之前没 guard 直接 .readAllBytes() 会 NPE → 500。现在显式 404,跟 "not exist" 路径语义一致
            if (inputStream == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (RuleException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("not exist")) {
                return ResponseEntity.notFound().build();
            }
            throw ex;
        } catch (Exception ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("not exist")) {
                return ResponseEntity.notFound().build();
            }
            throw new RuleException(ex);
        }
    }

    @PostMapping(value = "/save", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String saveBpmn(@RequestParam String file,
                           @RequestParam String content,
                           @RequestParam(required = false, defaultValue = "false") boolean newVersion) {
        try {
            User user = EnvironmentUtils.getLoginUser(null);
            repositoryService.saveFile(file, content, newVersion, null, user);
            // V5.20: save 之后通知 executor 清缓存。失败不影响 save 自身结果。
            notifyExecutorInvalidate(file);
            return "{\"result\":true}";
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    @PostMapping(value = "/deploy", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String deployBpmn(@RequestParam String file,
                             @RequestParam(required = false) String version) {
        try {
            InputStream inputStream;
            if (StringUtils.isEmpty(version)) {
                inputStream = repositoryService.readFile(file, null);
            } else {
                inputStream = repositoryService.readFile(file, version);
            }
            // V5.9.x: 跟 loadBpmn 一致 — Git 不可用时 readFile 返 null,显式 404 避免 NPE
            if (inputStream == null) {
                throw new RuleException("File [" + file + "] not exist.");
            }
            String bpmnXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            // V5.21+: 不再调 Flowable 部署。改为解析 IR(校验 XML 格式) + 通知 executor
            // invalidate。返回 deploymentId 用 file 名占位(前端 alert 成功,字段语义保留)。
            // 真正的"部署"语义由 executor 端 evaluate 时 lazy 拉最新 BPMN 完成。
            // V5.37 B0: BpmnXmlParser.parse BREAKING 返 BpmnDefinition;此处只校验单 process
            // 部署格式(collaboration 走 V5.37 B1+ 多池部署 path),用 parseSingleProcess 保留契约。
            FlowDefinition def = bpmnXmlParser.parseSingleProcess(bpmnXml);
            log.info("[DEPLOY-BPMN] parsed flowId={} nodes={} xmlHash={}",
                def.getProcessId(), def.getNodes().size(), def.getSourceXmlHash());
            notifyExecutorInvalidate(file);

            return "{\"deploymentId\":\"" + file + "\"}";
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    @PostMapping(value = "/convert", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = "text/xml;charset=UTF-8")
    public String convertToBpmn(@RequestParam String file) {
        try {
            InputStream inputStream = repositoryService.readFile(file, null);
            // V5.9.x: 跟 loadBpmn/deployBpmn 一致 — Git 不可用时 readFile 返 null 显式 404
            if (inputStream == null) {
                throw new RuleException("File [" + file + "] not exist.");
            }
            String oldXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return flowXmlConverter.convertToBpmn(oldXml);
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    /**
     * 通知 executor 端清掉 FlowDefinition 缓存。
     * <p>
     * POST {ruleforge.exec.url}/flow/invalidate?flowId=xxx — 失败仅 warn,
     * 不会影响 saveBpmn / deployBpmn 自身结果(executor 下次 evaluate 时会 lazy 拉最新)。
     */
    private void notifyExecutorInvalidate(String file) {
        try {
            String base = execUrl.endsWith("/") ? execUrl.substring(0, execUrl.length() - 1) : execUrl;
            String url = base + "/ruleforge/flow/invalidate?flowId="
                + URLEncoder.encode(file, StandardCharsets.UTF_8);
            execRestTemplate.postForEntity(url, null, String.class);
        } catch (Exception ex) {
            log.warn("[FLOW-INVALIDATE] notify executor failed for flowId={} (will lazy-refresh on next evaluate): {}",
                file, ex.getMessage());
        }
    }
}
