package com.ruleforge.console.service.impl;

import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.console.repository.ExternalRepository;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.model.BaseProcessDto;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.service.PackageService;
import com.ruleforge.console.service.impl.runner.ExportExcelRunnable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Fred
 * @since 2025/8/26 10:29
 */
@Slf4j
@Service
@Deprecated
@RequiredArgsConstructor
public class PackageServiceImpl implements PackageService {
    private final RuleForgeRepositoryService ruleforgeRepositoryService;
    private final KnowledgeBuilder knowledgeBuilder;
    private final ExternalRepository externalRepository;
    private final ExecutorService exportExcelDataExecutorService = Executors.newCachedThreadPool();


    @Override
    public BlockingQueue<BaseProcessDto<SXSSFWorkbook>> exportExcelData(String project, String packageId, Date startDate, Date endDate, Integer limit) {
        try {
            List<ResourcePackage> packages;
            packages = this.ruleforgeRepositoryService.loadProjectResourcePackages(project);
            String path = null;
            for (ResourcePackage resourcePackage : packages) {
                if (resourcePackage.getId().equals(packageId)) {
                    path = resourcePackage.getResourceItems().get(0).getPath();
                    break;
                }
            }
            if (path == null) {
                return null;
            }
            KnowledgeBase knowledgeBase = buildKnowledgeBase(path);

            BlockingQueue<BaseProcessDto<SXSSFWorkbook>> exportExcelDataDtoBlockingQueue = new LinkedBlockingQueue<>();
            this.exportExcelDataExecutorService.execute(new ExportExcelRunnable(
                    this.externalRepository,
                    knowledgeBase,
                    project,
                    packageId,
                    startDate,
                    endDate,
                    limit,
                    exportExcelDataDtoBlockingQueue
            ));

            return exportExcelDataDtoBlockingQueue;
        } catch (Exception e) {
            log.error("download excel error", e);
            return null;
        }

    }

    private KnowledgeBase buildKnowledgeBase(String files) throws IOException {
        files = Utils.decodeURL(files);
        ResourceBase resourceBase = this.knowledgeBuilder.newResourceBase();
        String[] paths = files.split(";");
        for (String path : paths) {
            String[] subpaths = path.split(",");
            path = subpaths[0];
            String version = null;
            if (subpaths.length > 1) {
                version = subpaths[1];
            }
            resourceBase.addResource(path, version);
        }
        return this.knowledgeBuilder.buildKnowledgeBase(resourceBase);
    }

}
