package com.ruleforge.console.controller;

import com.ruleforge.Utils;
import com.ruleforge.builder.ResourceLibraryBuilder;
import com.ruleforge.dsl.DSLRuleSetBuilder;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.RuleSet;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Fred
 * 2025/6/25 14:46
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforgeV2.root.path}/uleditor")
@RequiredArgsConstructor
public class ULEditorController extends BaseController {

    private final RuleForgeRepositoryService ruleforgeRepositoryService;
    private final DSLRuleSetBuilder dslRuleSetBuilder;
    private final ResourceLibraryBuilder resourceLibraryBuilder;

    @PostMapping("/loadUL")
    public void loadUL(HttpServletResponse resp,
                       @RequestParam("file") String file,
                       @RequestParam(value = "version", required = false) String version) throws ServletException, IOException {
        file = Utils.decodeURL(file);
        OutputStream outputStream = resp.getOutputStream();
        InputStream inputStream = null;
        try {
            if (StringUtils.isEmpty(version)) {
                inputStream = ruleforgeRepositoryService.readFile(file, null);
            } else {
                inputStream = ruleforgeRepositoryService.readFile(file, version);
            }
            IOUtils.copy(inputStream, outputStream);
        } catch (Exception ex) {
            throw new RuleException(ex);
        } finally {
            outputStream.close();
            inputStream.close();
        }
    }

    @PostMapping("/loadULLibs")
    public void loadULLibs(HttpServletRequest req) throws ServletException, IOException {
        String content = req.getParameter("content");
        RuleSet ruleSet = dslRuleSetBuilder.build(content);
        ResourceLibrary library = resourceLibraryBuilder.buildResourceLibrary(ruleSet.getLibraries());
        writeObjectToJson(library);
    }

}
