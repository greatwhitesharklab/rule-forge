package com.ruleforge.console.controller;

import com.ruleforge.Utils;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.ruleforge.runtime.service.KnowledgePackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @author Fred Gu
 * 2025-04-17 16:35
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/loadKnowledge")
@RequiredArgsConstructor
public class LoadKnowledgeController extends BaseController {

    private final KnowledgePackageService knowledgePackageService;

    @PostMapping("/load/test")
    public void loadTest(HttpServletRequest req) throws Exception {
        String packageId = req.getParameter("packageId");
        if (StringUtils.isEmpty(packageId)) {
            return;
        }

        packageId = Utils.decodeURL(packageId);
        KnowledgePackage knowledgePackage = this.knowledgePackageService.buildKnowledgePackage(packageId, true);

        writeObjectToJson(new KnowledgePackageWrapper(knowledgePackage));
    }
}
