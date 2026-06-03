package com.ruleforge.console.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.ruleforge.Utils;
import com.ruleforge.console.EnvironmentProvider;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.entity.ProjectRuntimeFlowEntity;
import com.ruleforge.console.entity.ProjectVersionEntity;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.repository.model.Type;
import com.ruleforge.console.repository.model.VersionFile;
import com.ruleforge.console.servlet.RequestContext;
import com.ruleforge.console.servlet.frame.ExportProject;
import com.ruleforge.exception.RuleException;
import com.ruleforge.runtime.cache.CacheUtils;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.util.EnvironmentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ruleforge.console.storage.RuleForgeBaseRepositoryService.PACKAGE_CONFIG_FILE;
import static com.ruleforge.console.storage.RuleForgeBaseRepositoryService.RES_PACKAGE_FILE;


@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/frame")
@RequiredArgsConstructor
public class FrameController extends BaseController {
    private static final String CLASSIFY_COOKIE_NAME = "_lib_classify";
    private final RuleForgeRepositoryService ruleforgeRepositoryService;
    private final EnvironmentProvider environmentProvider;
    private final ProjectRepository projectRepository;
    private final RuntimeRepository runtimeRepository;
    private final GitStorageService gitStorageService;

    @PostMapping("/loadProjects")
    public Map<String, Object> loadProjects(@RequestParam(value = "projectName", required = false) String projectName,
                                            @RequestParam(value = "searchFileName", required = false) String searchFileName,
                                            @RequestParam(value = "classify", required = false) String classifyValue,
                                            @RequestParam(value = "types", required = false) String typesStr,
                                            @RequestParam(value = "projectDetail", required = false) Boolean projectDetail,
                                            HttpServletRequest req, HttpServletResponse resp) {
        try {
            User user = EnvironmentUtils.getLoginUser(null);
            boolean classify = getClassify(classifyValue, req, resp);
            projectName = Utils.decodeURL(projectName);
            FileType[] types = null;
            if (StringUtils.hasText(typesStr) && !typesStr.equals("all")) {
                switch (typesStr) {
                    case "lib":
                        types = new FileType[]{FileType.VariableLibrary, FileType.ConstantLibrary, FileType.ParameterLibrary, FileType.ActionLibrary};
                        break;
                    case "rule":
                        types = new FileType[]{FileType.Ruleset, FileType.UL, FileType.RulesetLib};
                        break;
                    case "table":
                        types = new FileType[]{FileType.DecisionTable, FileType.ScriptDecisionTable, FileType.ComplexScorecard};
                        break;
                    case "tree":
                        types = new FileType[]{FileType.DecisionTree};
                        break;
                    case "flow":
                        types = new FileType[]{FileType.RuleFlow};
                        break;
                }
            }

            Repository repo = this.ruleforgeRepositoryService.loadRepository(projectName, user, classify, types, searchFileName, projectDetail == null || projectDetail);
            Map<String, Object> map = new HashMap<>();
            map.put("repo", repo);
            map.put("classify", classify);
            map.put("user", user);
            return map;
        } catch (Exception e) {
            log.error("loadProjects error", e);
            return new HashMap<>();
        }
    }

    @PostMapping("/fileSource")
    public Map<String, Object> fileSource(@RequestParam("path") String path,
                                          @RequestParam(required = false) String env,
                                          @RequestParam(required = false) String projectVersion,
                                          @RequestParam(required = false) String gitTag) throws Exception {
        path = Utils.decodeURL(path);
        String[] subpaths = path.split(":");
        path = Utils.decodeURL(subpaths[0]);
        String version = null;
        if (subpaths.length == 2) {
            version = subpaths[1];
        }
        InputStream inputStream;

        // If gitTag is provided, read directly from Git by tag
        if (StringUtils.hasText(gitTag)) {
            String projectName = extractProjectNameFromPath(path);
            if (projectName != null && gitStorageService.repoExists(projectName)) {
                String gitPath = path.startsWith("/") ? path.substring(1) : path;
                inputStream = gitStorageService.readFileStream(projectName, gitTag, gitPath);
                if (inputStream == null) {
                    throw new RuleException("File [" + path + "] not found at Git tag [" + gitTag + "]");
                }
            } else {
                throw new RuleException("Git repository not found for project in path [" + path + "]");
            }
        } else if (StringUtils.hasText(env)) {
            inputStream = this.ruleforgeRepositoryService.readFile(path, version, projectVersion, false);
        } else {
            inputStream = this.ruleforgeRepositoryService.readFile(path, version);
        }
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        inputStream.close();
        String xml;
        try {
            Document doc = DocumentHelper.parseText(content);
            OutputFormat format = OutputFormat.createPrettyPrint();
            StringWriter out = new StringWriter();
            XMLWriter writer = new XMLWriter(out, format);
            writer.write(doc);
            xml = out.toString();
        } catch (Exception ex) {
            xml = content;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("content", xml);
        return result;
    }

    @PostMapping("/fileVersions")
    public Map<String, Object> fileVersions(@RequestParam("path") String path,
                                            @RequestParam(required = false) String project,
                                            @RequestParam(required = false) String packageId,
                                            @RequestParam(required = false, defaultValue = "1") Integer page,
                                            @RequestParam(required = false, defaultValue = "25") Integer row) throws Exception {
        Map<String, Object> result = new HashMap<>(2);
        path = Utils.decodeURL(path);

        if (path.endsWith(RES_PACKAGE_FILE) && StringUtils.hasText(packageId)) {
            List<VersionFile> files = this.ruleforgeRepositoryService.getProjectPackageVersions(project, packageId);
            result.put("files", files);

            return result;
        }

        List<VersionFile> files = this.ruleforgeRepositoryService.getVersionFiles(path, true, page, row, false, false);
        List<String> versionList = files.stream().map(VersionFile::getName).collect(Collectors.toList());
        Map<String, String> testVersionAuditStatusMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(versionList)) {
            // todo
            VersionFile versionFile = files.get(0);
            List<ProjectRuntimeFlowEntity> projectRuntimeFlowEntities = runtimeRepository.findFlowsByProjectIdAndVersions(versionFile.getProjectId(), versionList);
            if (!CollectionUtils.isEmpty(projectRuntimeFlowEntities)) {
                testVersionAuditStatusMap.putAll(projectRuntimeFlowEntities
                        .stream()
                        .collect(Collectors.toMap(ProjectRuntimeFlowEntity::getProjectVersion, val -> String.valueOf(val.getAuditStatus()), (k1, k2) -> k1)));
            }
        }
        Long count = this.ruleforgeRepositoryService.countVersionFiles(path);
        // 获取审批状态
        if (StringUtils.hasText(project)) {
            List<VersionFile> versionFileList = this.ruleforgeRepositoryService.getProjectVersions(project, true, page, row);
            if (!versionFileList.isEmpty()) {
                Map<String, String> versionAuditStatusMap = new HashMap<>();
                for (VersionFile versionFile : versionFileList) {
                    versionAuditStatusMap.put(versionFile.getName(), versionFile.getAuditStatus());
                }
                for (VersionFile versionFile : files) {
                    versionFile.setAuditStatus(versionAuditStatusMap.get(versionFile.getName()));
                    versionFile.setTestAuditStatus(testVersionAuditStatusMap.get(versionFile.getName()));
                }
            }
        }

        result.put("files", files);
        result.put("count", count);

        return result;
    }

    @PostMapping("/fileExistCheck")
    public Map<String, Object> fileExistCheck(@RequestParam String fullFileName,
                                              @RequestParam(required = false) String newFileName,
                                              @RequestParam(required = false) String newFileNameForRename) throws ServletException, IOException {
        if (StringUtils.isEmpty(fullFileName)) {
            return null;
        }
        fullFileName = Utils.decodeURL(fullFileName);
        fullFileName = fullFileName.trim();
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("valid", !this.ruleforgeRepositoryService.fileExistCheck(fullFileName));
            return result;
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    @PostMapping("/createFile")
    public RepositoryFile createFile(@RequestParam String path,
                                     @RequestParam String type) throws ServletException, IOException {
        path = Utils.decodeURL(path);
        FileType fileType = FileType.parse(type);
        if (fileType == null) {
            throw new RuleException("Unknown file type: " + type);
        }
        StringBuilder content = new StringBuilder();
        if (fileType.equals(FileType.UL)) {
            content.append("rule \"rule01\"");
            content.append("\n");
            content.append("if");
            content.append("\r\n");
            content.append("then");
            content.append("\r\n");
            content.append("end");
        } else if (fileType.equals(FileType.DecisionTable)) {
            content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            content.append("<decision-table>");
            content.append("<cell row=\"0\" col=\"2\" rowspan=\"1\"></cell>");
            content.append("<cell row=\"0\" col=\"1\" rowspan=\"1\">");
            content.append("<joint type=\"and\"/>");
            content.append("</cell>");
            content.append("<cell row=\"0\" col=\"0\" rowspan=\"1\">");
            content.append("<joint type=\"and\"/>");
            content.append("</cell>");
            content.append("<cell row=\"1\" col=\"2\" rowspan=\"1\">");
            content.append("</cell>");
            content.append("<cell row=\"1\" col=\"1\" rowspan=\"1\">");
            content.append("<joint type=\"and\"/>");
            content.append("</cell>");
            content.append("<cell row=\"1\" col=\"0\" rowspan=\"1\">");
            content.append("<joint type=\"and\"/>");
            content.append("</cell>");
            content.append("<row num=\"0\" height=\"40\"/>");
            content.append("<row num=\"1\" height=\"40\"/>");
            content.append("<col num=\"0\" width=\"120\" type=\"Criteria\"/>");
            content.append("<col num=\"1\" width=\"120\" type=\"Criteria\"/>");
            content.append("<col num=\"2\" width=\"200\" type=\"Assignment\"/>");
            content.append("</decision-table>");
        } else if (fileType.equals(FileType.DecisionTree)) {
            content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            content.append("<decision-tree>");
            content.append("<variable-tree-node></variable-tree-node>");
            content.append("</decision-tree>");
        } else if (fileType.equals(FileType.ScriptDecisionTable)) {
            content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            content.append("<script-decision-table>");
            content.append("<script-cell row=\"0\" col=\"2\" rowspan=\"1\"></script-cell>");
            content.append("<script-cell row=\"0\" col=\"1\" rowspan=\"1\"></script-cell>");
            content.append("<script-cell row=\"0\" col=\"0\" rowspan=\"1\"></script-cell>");
            content.append("<script-cell row=\"1\" col=\"2\" rowspan=\"1\"></script-cell>");
            content.append("<script-cell row=\"1\" col=\"1\" rowspan=\"1\"></script-cell>");
            content.append("<script-cell row=\"1\" col=\"0\" rowspan=\"1\"></script-cell>");
            content.append("<row num=\"0\" height=\"40\"/>");
            content.append("<row num=\"1\" height=\"40\"/>");
            content.append("<col num=\"0\" width=\"120\" type=\"Criteria\"/>");
            content.append("<col num=\"1\" width=\"120\" type=\"Criteria\"/>");
            content.append("<col num=\"2\" width=\"200\" type=\"Assignment\"/>");
            content.append("</script-decision-table>");
        } else if (fileType.equals(FileType.Crosstab)) {
            content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            content.append("<crosstab>");
            content.append("<header>LEFT &amp;&amp; TOP</header>");
            content.append("<row number=\"1\" type=\"top\"/>");
            content.append("<column number=\"1\" type=\"left\"/>");
            content.append("<column number=\"2\" type=\"top\"/>");
            content.append("<row number=\"2\" type=\"left\"/>");
            content.append("<condition-cell row=\"1\" col=\"2\"/>");
            content.append("<condition-cell row=\"2\" col=\"1\"/>");
            content.append("<value-cell row=\"2\" col=\"2\"/>");
            content.append("</crosstab>");
        } else if (fileType.equals(FileType.Scorecard)) {
            content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            content.append("<scorecard scoring-type=\"sum\" assign-target-type=\"none\">");
            content.append("</scorecard>");
        } else if (fileType.equals(FileType.ComplexScorecard)) {
            content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            content.append("<complex-scorecard scoring-type=\"sum\" assign-target-type=\"none\">");
            content.append("<cell row=\"0\" col=\"2\" rowspan=\"1\"></cell>");
            content.append("<cell row=\"0\" col=\"1\" rowspan=\"1\">");
            content.append("<joint type=\"and\"/>");
            content.append("</cell>");
            content.append("<cell row=\"0\" col=\"0\" rowspan=\"1\">");
            content.append("<joint type=\"and\"/>");
            content.append("</cell>");
            content.append("<cell row=\"1\" col=\"2\" rowspan=\"1\">");
            content.append("</cell>");
            content.append("<cell row=\"1\" col=\"1\" rowspan=\"1\">");
            content.append("<joint type=\"and\"/>");
            content.append("</cell>");
            content.append("<cell row=\"1\" col=\"0\" rowspan=\"1\">");
            content.append("<joint type=\"and\"/>");
            content.append("</cell>");
            content.append("<row num=\"0\" height=\"40\"/>");
            content.append("<row num=\"1\" height=\"40\"/>");
            content.append("<col num=\"0\" width=\"150\" type=\"Criteria\"/>");
            content.append("<col num=\"1\" width=\"150\" type=\"Criteria\"/>");
            content.append("<col num=\"2\" width=\"120\" type=\"Score\"/>");
            content.append("</complex-scorecard>");
        } else if (fileType.equals(FileType.Ruleset)) {
            String name = getRootTagName(fileType);
            content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            content.append("<").append(name).append(">");
            content.append("</").append(name).append(">");
        } else {
            String name = getRootTagName(fileType);
            content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            content.append("<").append(name).append(">");
            content.append("</").append(name).append(">");
        }

        User user = EnvironmentUtils.getLoginUser(null);
        RepositoryFile newFileInfo = new RepositoryFile();
        newFileInfo.setFullPath(path);
        if (fileType.equals(FileType.VariableLibrary)) {
            newFileInfo.setType(Type.variable);
        } else if (fileType.equals(FileType.ActionLibrary)) {
            newFileInfo.setType(Type.action);
        } else if (fileType.equals(FileType.ConstantLibrary)) {
            newFileInfo.setType(Type.constant);
        } else if (fileType.equals(FileType.ParameterLibrary)) {
            newFileInfo.setType(Type.parameter);
        } else if (fileType.equals(FileType.DecisionTable)) {
            newFileInfo.setType(Type.decisionTable);
        } else if (fileType.equals(FileType.ScriptDecisionTable)) {
            newFileInfo.setType(Type.scriptDecisionTable);
        } else if (fileType.equals(FileType.Ruleset) || fileType.equals(FileType.RulesetLib)) {
            newFileInfo.setType(Type.rule);
        } else if (fileType.equals(FileType.UL)) {
            newFileInfo.setType(Type.ul);
        } else if (fileType.equals(FileType.DecisionTree)) {
            newFileInfo.setType(Type.decisionTree);
        } else if (fileType.equals(FileType.RuleFlow)) {
            newFileInfo.setType(Type.flow);
        } else if (fileType.equals(FileType.Scorecard)) {
            newFileInfo.setType(Type.scorecard);
        } else if (fileType.equals(FileType.ComplexScorecard)) {
            newFileInfo.setType(Type.complexscorecard);
        } else if (fileType.equals(FileType.Crosstab)) {
            newFileInfo.setType(Type.crosstab);
        }
        try {
            this.ruleforgeRepositoryService.createFile(path, content.toString(), user);
        } catch (Exception ex) {
            throw new RuleException(ex);
        }

        return newFileInfo;
    }

    @GetMapping("/exportProjectBackupFile")
    public void exportProjectBackupFile(HttpServletResponse resp,
                                        @RequestParam String path) throws Exception {
        Date start = new Date();
        User user = this.environmentProvider.getLoginUser(null);

        // 判断权限
        if (!user.isExport()) {
//            Map<String, Object> result = new HashMap<>();
//            result.put("content", "无权限");
            return;
        }

        String projectPath = Utils.decodeURL(path);
        if (StringUtils.isEmpty(projectPath)) {
            throw new RuleException("Export project not be null.");
        }

        SimpleDateFormat sd = new SimpleDateFormat("yyyyMMddHHmmss");
        String projectName = projectPath.substring(1);
        String filename = projectName + "-ruleforge-repo-" + sd.format(new Date()) + "V3.tar.gz";
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1) + "\"");
        resp.setHeader("content-type", "application/octet-stream");

        try (OutputStream outputStream = resp.getOutputStream();
             GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(outputStream);
             TarArchiveOutputStream tOut = new TarArchiveOutputStream(gzOut)) {
            // 保存版本文件
            Repository repository = this.ruleforgeRepositoryService.loadRepository(projectName, user, false, null, null);

            // 保存项目xml
            RepositoryFile children = repository.getRootFile().getChildren().get(0);
            byte[] bytes = JSON.toJSONString(children).getBytes();
            TarArchiveEntry tarEntry = new TarArchiveEntry("systemView.json");
            tarEntry.setSize(bytes.length);
            tOut.putArchiveEntry(tarEntry);
            tOut.write(bytes, 0, bytes.length);
            tOut.closeArchiveEntry();

            // 保存版本文件
            Map<String, ExportProject> exportProjectMap = new HashMap<>();
            iterateRepositoryFile(repository.getRootFile(), tOut, exportProjectMap);

            // 保存版本文件版本
            bytes = JSON.toJSONString(exportProjectMap).getBytes();
            tarEntry = new TarArchiveEntry("version.json");
            tarEntry.setSize(bytes.length);
            tOut.putArchiveEntry(tarEntry);
            tOut.write(bytes, 0, bytes.length);
            tOut.closeArchiveEntry();

            // 保存包配置文件
            String packageConfigPath = "/" + projectName + "/" + PACKAGE_CONFIG_FILE;
            InputStream packageConfig = this.ruleforgeRepositoryService.readFile(packageConfigPath);
            bytes = IOUtils.toByteArray(packageConfig);
            tarEntry = new TarArchiveEntry("packageConfig.xml");
            tarEntry.setSize(bytes.length);
            tOut.putArchiveEntry(tarEntry);
            tOut.write(bytes, 0, bytes.length);
            tOut.closeArchiveEntry();
            // todo
            ProjectEntity projectEntity = this.projectRepository.findByName(projectName);
            List<ProjectVersionEntity> projectVersionEntityList = this.projectRepository.findVersionsByProjectId(projectEntity.getId(), null, false);
            bytes = JSON.toJSONString(projectVersionEntityList).getBytes();
            tarEntry = new TarArchiveEntry("projectVersion.json");
            tarEntry.setSize(bytes.length);
            tOut.putArchiveEntry(tarEntry);
            tOut.write(bytes, 0, bytes.length);
            tOut.closeArchiveEntry();

            double processTime = (System.currentTimeMillis() - start.getTime()) / 1000D;
            log.info("{} 导出成功 {}", projectName, (processTime + "秒"));

            tOut.finish();
        }
    }

    private void iterateRepositoryFile(RepositoryFile repositoryFile, TarArchiveOutputStream tarOutputStream, Map<String, ExportProject> exportProjectMap) throws Exception {
        List<RepositoryFile> repositoryFileList = repositoryFile.getChildren();
        if (repositoryFileList != null && !repositoryFileList.isEmpty()) {
            for (RepositoryFile repositoryFileItem : repositoryFileList) {
                if (repositoryFileItem.getChildren() != null && !repositoryFileItem.getChildren().isEmpty()) {
                    iterateRepositoryFile(repositoryFileItem, tarOutputStream, exportProjectMap);
                } else {
                    log.info("iterateRepositoryFile repositoryFileItem: {}", repositoryFileItem.getFullPath());
                    syncVersionFileList(repositoryFileItem, tarOutputStream, exportProjectMap);
                }
            }
        }
    }

    private void syncVersionFileList(RepositoryFile repositoryFile, TarArchiveOutputStream tarOutputStream, Map<String, ExportProject> exportProjectMap) throws Exception {
        try {
            List<VersionFile> versionFileList = this.ruleforgeRepositoryService.getVersionFiles(repositoryFile.getFullPath(), false, 0, 0, true, true);
            log.info("syncVersionFileList versionFileList: {}", versionFileList == null ? 0 : versionFileList.size());
            if (versionFileList != null && !versionFileList.isEmpty()) {
                ExportProject exportProject = new ExportProject();
                int i = 0;
                for (VersionFile versionFile : versionFileList) {
                    log.info("syncVersionFileList versionFile: {} {}", versionFile.getName(), versionFile.getPath());
//                    InputStream inputStream = this.ruleforgeRepositoryService.readFile(versionFile.getPath(), versionFile.getName());
//                    byte[] bytes = IOUtils.toByteArray(inputStream);
                    String content = versionFile.getContent();
                    versionFile.setContent(null);
                    exportProject.getVersionFileMap().put(String.valueOf(i++), versionFile);

                    TarArchiveEntry tarEntry = new TarArchiveEntry(versionFile.getPath() + "/" + versionFile.getName());
                    tarEntry.setSize(content.getBytes().length);
                    tarOutputStream.putArchiveEntry(tarEntry);
                    tarOutputStream.write(content.getBytes(), 0, content.getBytes().length);
                    tarOutputStream.closeArchiveEntry();
                }

                exportProjectMap.put(repositoryFile.getFullPath(), exportProject);
            }
        } catch (Exception e) {
            log.error("syncVersionFileList error", e);
        }
    }

    @PostMapping("/importProject")
    public Map<String, Object> importProject(HttpServletRequest req, @RequestParam("file") MultipartFile file) throws Exception {
        User user = this.environmentProvider.getLoginUser(null);
        log.info("importProject file：{} user：{}", file.getOriginalFilename(), JSON.toJSONString(user));
        Map<String, Object> result = new HashMap<>();
        result.put("status", false);
        Date start = new Date();

        // 判断权限
        if (!user.isImport()) {
            result.put("content", "无权限");
            return result;
        }

        Map<String, Object> zipMap = extraImportGzip(file);
        RepositoryFile repositoryFile = (RepositoryFile) zipMap.get("repositoryFile");
        String packageConfigJson = (String) zipMap.get("packageConfigJson");
        String projectVersionConfigJson = (String) zipMap.get("projectVersionConfigJson");
        Map<String, ExportProject> exportProjectMap = (Map<String, ExportProject>) zipMap.get("exportProjectMap");
        String projectName = repositoryFile.getName();

        Long lockVersion = this.ruleforgeRepositoryService.lockPath(projectName, user);
        if (lockVersion == null) {
            result.put("content", "项目已被锁定");
            return result;
        }

        try {
            // 删除旧版本
            try {
                this.ruleforgeRepositoryService.deleteProject(projectName, user);
                log.info("{} 删除成功 {}秒", projectName, (System.currentTimeMillis() - start.getTime()) / 1000D);
            } catch (Exception e) {
                log.error("deleteFile {}秒", (System.currentTimeMillis() - start.getTime()) / 1000D, e);
            }

            try {
                // 导入数据库
                Long projectId = this.ruleforgeRepositoryService.importFromZip(user, file, repositoryFile, exportProjectMap, false);
                // 插入项目配置
                String packageConfigPath = "/" + projectName + "/" + PACKAGE_CONFIG_FILE;
                this.ruleforgeRepositoryService.createFile(packageConfigPath, packageConfigJson, user);
                // todo 插入project version
                List<ProjectVersionEntity> projectVersionEntityList = JSON.parseObject(projectVersionConfigJson, new TypeReference<List<ProjectVersionEntity>>() {
                });
                // 遍历列表，修改 projectId 并清空 id，然后插入数据库
                if (projectVersionEntityList != null) {
                    for (ProjectVersionEntity version : projectVersionEntityList) {
                        version.setProjectId(projectId); // 设置为当前项目的 ID
                        version.setId(null); // 将 ID 设置为 null，以便 MybatisPlus 自动生成新的 ID
                    }
                    this.projectRepository.batchInsertVersions(projectVersionEntityList);
                }

            } catch (Exception e) {
                log.error("extraImportZipJcr", e);
            }

            // 清理缓存
            CacheUtils.getKnowledgeCache().removeKnowledgeByProjectName(projectName);

            double processTime = (System.currentTimeMillis() - start.getTime()) / 1000D;
            log.info("{} 导入成功 {}", projectName, (req.getContextPath() + "/ruleforge/frame " + processTime + "秒"));

            result.put("status", true);
        } catch (Exception e) {
            log.error("importProject {}", projectName, e);
        } finally {
            this.ruleforgeRepositoryService.unlockPath(projectName, user, lockVersion);
        }

        return result;
    }

    private Map<String, Object> extraImportGzip(MultipartFile file) {
        RepositoryFile repositoryFile = null;
        String packageConfigJson = null;
        String projectVersionConfigJson = null;

        Map<String, String> dataMap = new HashMap<>();
        String versionJson = null;
        try (InputStream fi = file.getInputStream();
             BufferedInputStream bi = new BufferedInputStream(fi);
             GzipCompressorInputStream gzi = new GzipCompressorInputStream(bi);
             TarArchiveInputStream ti = new TarArchiveInputStream(gzi)) {

            ArchiveEntry entry;
            while ((entry = ti.getNextEntry()) != null && !entry.isDirectory()) {
                switch (entry.getName()) {
                    case "systemView.json":
                        String systemView = IOUtils.toString(ti, StandardCharsets.UTF_8);
                        repositoryFile = JSON.parseObject(systemView, new TypeReference<RepositoryFile>() {
                        });
                        break;
                    case "version.json":
                        versionJson = IOUtils.toString(ti, StandardCharsets.UTF_8);
                        break;
                    case "projectVersion.json":
                        projectVersionConfigJson = IOUtils.toString(ti, StandardCharsets.UTF_8);
                        break;
                    case "packageConfig.xml":
                        packageConfigJson = IOUtils.toString(ti, StandardCharsets.UTF_8);
                        break;
                    default:
                        String data = IOUtils.toString(ti, StandardCharsets.UTF_8);
                        dataMap.put("/" + entry.getName(), data);
                        log.info("dataMap entry getName {}", entry.getName());
                }
            }
        } catch (IOException e) {
            log.error("extraImportGzip", e);
        }

        // 处理版本
        Map<String, ExportProject> exportProjectMap = null;
        if (StringUtils.hasText(versionJson)) {
            exportProjectMap = JSON.parseObject(versionJson, new TypeReference<Map<String, ExportProject>>() {
            });

            for (String versionFileKey : exportProjectMap.keySet()) {
                for (String iKey : exportProjectMap.get(versionFileKey).getVersionFileMap().keySet()) {
                    String content = dataMap.get(versionFileKey + "/" + exportProjectMap.get(versionFileKey).getVersionFileMap().get(iKey).getName());
                    exportProjectMap.get(versionFileKey).getVersionFileMap().get(iKey).setContent(content);
                }
            }
        }

        Map<String, Object> map = new HashMap<>(3);
        map.put("repositoryFile", repositoryFile);
        map.put("packageConfigJson", packageConfigJson);
        map.put("projectVersionConfigJson", projectVersionConfigJson);
        map.put("exportProjectMap", exportProjectMap);
        return map;
    }

    private String getRootTagName(FileType type) {
        String root = null;
        switch (type) {
            case ActionLibrary:
                root = "action-library";
                break;
            case ConstantLibrary:
                root = "constant-library";
                break;
            case DecisionTable:
                root = "decision-table";
                break;
            case DecisionTree:
                root = "decision-tree";
                break;
            case ParameterLibrary:
                root = "parameter-library";
                break;
            case RuleFlow:
                root = "rule-flow";
                break;
            case Ruleset:
                root = "rule-set";
                break;
            case ScriptDecisionTable:
                root = "script-decision-table";
                break;
            case VariableLibrary:
                root = "variable-library";
                break;
            case UL:
                root = "script";
                break;
            case Scorecard:
                root = "scorecard";
                break;
            case DIR:
                throw new IllegalArgumentException("Unsupport filetype : " + type);
        }
        return root;
    }

    private boolean getClassify(String classifyValue, HttpServletRequest req, HttpServletResponse resp) {
        if (!StringUtils.hasText(classifyValue)) {
            Cookie[] cookies = req.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (CLASSIFY_COOKIE_NAME.equals(cookie.getName())) {
                        classifyValue = cookie.getValue();
                        break;
                    }
                }
            }
        } else {
            Cookie classifyCookie = new Cookie(CLASSIFY_COOKIE_NAME, classifyValue);
            classifyCookie.setMaxAge(2100000000);
            resp.addCookie(classifyCookie);
        }
        boolean classify = true;
        if (StringUtils.hasText(classifyValue)) {
            classify = Boolean.parseBoolean(classifyValue);
        }
        return classify;
    }

    @PostMapping("/deleteProject")
    public Map<String, Object> deleteProject(@RequestParam("path") String path,
                                             @RequestParam(value = "isFolder", required = false) String isFolder,
                                             @RequestParam(value = "projectName", required = false) String projectName,
                                             @RequestParam(value = "searchFileName", required = false) String searchFileName,
                                             @RequestParam(value = "classify", required = false) String classifyValue,
                                             @RequestParam(value = "types", required = false) String typesStr,
                                             HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("status", false);
        path = Utils.decodeURL(path);
        User user = EnvironmentUtils.getLoginUser(null);
        String deleteProjectName = path;
        if (deleteProjectName.startsWith("/")) {
            deleteProjectName = deleteProjectName.substring(1);
        }
        log.info(String.format("deleteProject file：%s user：%s", deleteProjectName, JSON.toJSONString(user)));
        Long lockVersion = this.ruleforgeRepositoryService.lockPath(deleteProjectName, user);
        if (lockVersion == null) {
            result.put("content", "项目已被锁定");
            return result;
        }

        try {
            this.ruleforgeRepositoryService.deleteProject(deleteProjectName, user);
            deleteProjectName = deleteProjectName.split("/")[0];
            CacheUtils.getKnowledgeCache().removeKnowledgeByProjectName(deleteProjectName);
            result.put("status", true);
        } finally {
            this.ruleforgeRepositoryService.unlockPath(deleteProjectName, user, lockVersion);
        }

        if (StringUtils.hasText(isFolder) && isFolder.equals("true")) {
            return loadProjects(projectName, searchFileName, classifyValue, typesStr, true, req, resp);
        }

        return result;
    }

    @PostMapping("/deleteFile")
    public Map<String, Object> deleteFile(@RequestParam("path") String path,
                                          @RequestParam(value = "isFolder", required = false) String isFolder,
                                          @RequestParam(value = "projectName", required = false) String projectName,
                                          @RequestParam(value = "searchFileName", required = false) String searchFileName,
                                          @RequestParam(value = "classify", required = false) String classifyValue,
                                          @RequestParam(value = "types", required = false) String typesStr,
                                          HttpServletRequest req, HttpServletResponse resp) throws Exception {
        User user = EnvironmentUtils.getLoginUser(null);
        log.info(String.format("deleteFile path:%s isFolder：%s projectName:%s searchFileName:%s classify:%s types：%s user:%s",
                path, isFolder, projectName, searchFileName, classifyValue, typesStr, JSON.toJSONString(user)));
        path = Utils.decodeURL(path);
        this.ruleforgeRepositoryService.deleteFile(path, user);
        if (StringUtils.hasText(isFolder) && isFolder.equals("true")) {
            return loadProjects(projectName, searchFileName, classifyValue, typesStr, true, req, resp);
        }

        return null;
    }

    @PostMapping("/createFolder")
    public Map<String, Object> createFolder(@RequestParam(value = "fullFolderName", required = false) String fullFolderName,
                                            @RequestParam(value = "projectName", required = false) String projectName,
                                            @RequestParam(value = "searchFileName", required = false) String searchFileName,
                                            @RequestParam(value = "classify", required = false) String classifyValue,
                                            @RequestParam(value = "types", required = false) String typesStr,
                                            HttpServletRequest req, HttpServletResponse resp) throws Exception {
        fullFolderName = Utils.decodeURL(fullFolderName);
        User user = EnvironmentUtils.getLoginUser(null);
        this.ruleforgeRepositoryService.createDir(fullFolderName, user);
        return loadProjects(projectName, searchFileName, classifyValue, typesStr, true, req, resp);
    }

    @PostMapping("/projectExistCheck")
    public Map<String, Object> projectExistCheck(@RequestParam("newProjectName") String projectName) throws ServletException, IOException {
        if (org.apache.commons.lang.StringUtils.isEmpty(projectName)) {
            return null;
        }
        projectName = Utils.decodeURL(projectName);
        projectName = projectName.trim();
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("valid", !this.ruleforgeRepositoryService.fileExistCheck(projectName));
        } catch (Exception ex) {
            throw new RuleException(ex);
        }

        return result;
    }

    @PostMapping("/createProject")
    public RepositoryFile createProject(@RequestParam(value = "classify", required = false) String classifyValue,
                                        @RequestParam("newProjectName") String projectName,
                                        HttpServletRequest req, HttpServletResponse resp) throws Exception {
        projectName = Utils.decodeURL(projectName);
        boolean classify = getClassify(classifyValue, req, resp);
        User user = EnvironmentUtils.getLoginUser(null);
        return this.ruleforgeRepositoryService.createProject(projectName, user, classify);
    }

    public void lockFile(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String file = req.getParameter("file");
        User user = EnvironmentUtils.getLoginUser(new RequestContext(req, resp));
        this.ruleforgeRepositoryService.lockPath(file, user);
    }

    public void unlockFile(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String file = req.getParameter("file");
        User user = EnvironmentUtils.getLoginUser(new RequestContext(req, resp));
        this.ruleforgeRepositoryService.unlockPath(file, user, null);
    }

    public void copyFile(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String newFullPath = req.getParameter("newFullPath");
        String oldFullPath = req.getParameter("oldFullPath");
        newFullPath = Utils.decodeURL(newFullPath);
        oldFullPath = Utils.decodeURL(oldFullPath);
        try {
            InputStream inputStream = this.ruleforgeRepositoryService.readFile(oldFullPath, null);
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            inputStream.close();
            User user = EnvironmentUtils.getLoginUser(new RequestContext(req, resp));
            this.ruleforgeRepositoryService.createFile(newFullPath, content, user);
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    public void fileRename(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String path = req.getParameter("path");
        path = Utils.decodeURL(path);
        String newPath = req.getParameter("newPath");
        newPath = Utils.decodeURL(newPath);
        this.ruleforgeRepositoryService.fileRename(path, newPath);
    }

    /**
     * Extract project name from a file path like "/projectName/folder/file.xml".
     */
    private String extractProjectNameFromPath(String path) {
        if (path == null || path.isEmpty()) return null;
        String cleaned = path.startsWith("/") ? path.substring(1) : path;
        int slash = cleaned.indexOf('/');
        return slash > 0 ? cleaned.substring(0, slash) : cleaned;
    }

}
