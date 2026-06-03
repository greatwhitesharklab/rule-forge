package com.ruleforge.executor.controller;

import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.cache.CacheUtils;
import com.ruleforge.runtime.cache.KnowledgeCache;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.ruleforge.runtime.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final KnowledgeService knowledgeService;

    @RequestMapping("/do")
    public String doTest(@RequestParam("path") String path,
                         @RequestParam(value = "flow", required = false) String flow) throws Exception {
        KnowledgePackage knowledgePackage = knowledgeService.getKnowledge(path);
        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);

        if (flow != null && !flow.isEmpty()) {
            // Flow execution is now handled by Flowable BPMN engine in console app.
            // Delegate to console's REST endpoint.
            return "Flow execution requires Flowable engine. Use console /flow/deploy and Flowable RuntimeService.";
        }

        RuleExecutionResponse response = session.fireRules();
        return response.toString();
    }

    @PostMapping("/knowledge")
    public void knowledge(@RequestBody Map<String, String> params) {
        String packageId = params.get("packageId");
        if (packageId != null) {
            KnowledgeCache knowledgeCache = CacheUtils.getKnowledgeCache();
            knowledgeCache.markKnowledgeDirty(packageId);
            log.info("Marked package [{}] as dirty.", packageId);
        }
    }
}
