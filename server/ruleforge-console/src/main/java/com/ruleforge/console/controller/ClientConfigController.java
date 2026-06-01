package com.ruleforge.console.controller;

import com.ruleforge.Utils;
import com.ruleforge.console.model.User;
import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.repository.BaseRepositoryService;
import com.ruleforge.console.util.EnvironmentUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/${ruleforgeV2.root.path}/clientconfig")
@RequiredArgsConstructor
public class ClientConfigController {

    private final RepositoryService repositoryService;

    @PostMapping("/loadData")
    public List<?> loadData(@RequestParam String project) throws Exception {
        project = Utils.decodeURL(project);
        return repositoryService.loadClientConfigs(project);
    }

    @PostMapping("/save")
    public void save(@RequestParam String project, @RequestParam String content) throws Exception {
        project = Utils.decodeURL(project);
        String file = project + "/" + BaseRepositoryService.CLIENT_CONFIG_FILE;
        content = Utils.decodeURL(content);
        User user = EnvironmentUtils.getLoginUser(null);
        repositoryService.saveFile(file, content, false, null, user);
    }
}
