package com.ruleforge.console.service.impl;

import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.entity.ProjectRuntimeConfigEntity;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.repository.model.ResourceItem;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.model.PackageConfig;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.exception.RuleException;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.service.KnowledgePackageService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Service
@AllArgsConstructor
public class RuleForgeKnowledgePackageService implements KnowledgePackageService {
    private final KnowledgeBuilder knowledgeBuilder;
    private final RuleForgeRepositoryService ruleforgeRepositoryService;
    private final ProjectRepository projectRepository;
    private final RuntimeRepository runtimeRepository;

    @Override
    public KnowledgePackage buildKnowledgePackage(String packageInfo) throws RuleException {
        return buildKnowledgePackage(packageInfo, false);
    }

    @Override
    public KnowledgePackage buildKnowledgePackage(String packageInfo, Boolean latest) throws RuleException {
        try {
            String[] info = packageInfo.split("/");
            if (info.length != 2) {
                throw new RuleException("PackageInfo [" + packageInfo + "] is invalid. Correct such as \"projectName/packageId\".");
            }
            String project = info[0];
            String packageId = info[1];

            // 加载版本配置
            PackageConfig packageConfig = this.ruleforgeRepositoryService.loadPackageConfigs(project.contains(":") ? project.split(":")[0] : project);

            // todo
            ProjectEntity projectEntity = this.projectRepository.findByName(project);
            ProjectRuntimeConfigEntity projectRuntime = this.runtimeRepository.findConfigByPackage(projectEntity.getId(), packageId, "prod");

            if (!latest && !project.contains(":")) {
                if (projectRuntime != null) {
                    project += ":" + projectRuntime.getProjectVersion();
                } else if (packageConfig.getVersion() != null) {
                    project += ":" + packageConfig.getVersion();
                }
            }

            List<ResourcePackage> packages = this.ruleforgeRepositoryService.loadProjectResourcePackages(project);
            List<ResourceItem> list = null;
            for (ResourcePackage p : packages) {
                if (p.getId().equals(packageId)) {
                    list = p.getResourceItems();
                    if (StringUtils.hasText(p.getVersion())) {
                        packageConfig.setVersion(p.getVersion());
                    }
                    break;
                }
            }
            if (list == null) {
                throw new RuleException("PackageId [" + packageId + "] was not found in project [" + project + "].");
            }
            ResourceBase resourceBase = this.knowledgeBuilder.newResourceBase();
            for (ResourceItem item : list) {
                resourceBase.addResource(item.getPath(), item.getVersion(), packageConfig.getVersion());
            }
            KnowledgeBase knowledgeBase = this.knowledgeBuilder.buildKnowledgeBase(resourceBase);
            KnowledgePackage knowledgePackage = knowledgeBase.getKnowledgePackage();
            if (project.contains(":")) {
                knowledgePackage.setVersion(project.split(":")[1]);
            }
            return knowledgePackage;
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    @Override
    public boolean isKnowledgePackageNeedUpdate(String packageInfo) throws IOException {
        return true;
    }
}
