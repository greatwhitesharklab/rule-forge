package com.ruleforge.console.flow.controller;

import com.ruleforge.Utils;
import com.ruleforge.console.model.User;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.flow.converter.FlowXmlConverter;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.exception.RuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/flow")
@RequiredArgsConstructor
public class BpmnFlowController {

    private final RuleForgeRepositoryService repositoryService;
    private final RepositoryService flowableRepositoryService;
    private final FlowXmlConverter flowXmlConverter;

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
            String deploymentId = flowableRepositoryService.createDeployment()
                    .addString(file + ".bpmn20.xml", bpmnXml)
                    .name(file)
                    .deploy()
                    .getId();
            return "{\"deploymentId\":\"" + deploymentId + "\"}";
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
}
