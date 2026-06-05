package com.ruleforge.console.controller;

import com.ruleforge.Utils;
import com.ruleforge.builder.resource.Resource;
import com.ruleforge.console.ExternalProcessService;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.entity.ProjectRuntimeConfigEntity;
import com.ruleforge.console.entity.ProjectVersionEntity;
import com.ruleforge.console.repository.ExternalRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.servlet.common.ErrorInfo;
import com.ruleforge.console.servlet.common.RefFile;
import com.ruleforge.console.servlet.common.ScriptErrorListener;
import com.ruleforge.console.servlet.common.ScriptType;
import com.ruleforge.dsl.DSLRuleSetBuilder;
import com.ruleforge.dsl.RuleParserLexer;
import com.ruleforge.dsl.RuleParserParser;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.rule.RuleSet;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.action.ActionLibrary;
import com.ruleforge.model.library.action.SpringBean;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.parse.deserializer.*;
import com.ruleforge.runtime.BuiltInActionLibraryBuilder;
import com.ruleforge.runtime.cache.CacheUtils;
import com.ruleforge.console.model.PackageConfig;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.storage.RepositoryResourceProvider;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.util.CompareUtils;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.console.util.FileUploadUtils;
import com.ruleforge.console.util.VersionUtils;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.ruleforge.console.storage.RuleForgeBaseRepositoryService.RES_PACKAGE_FILE;

@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/common")
@RequiredArgsConstructor
public class CommonController extends BaseController {

    private final RuleForgeRepositoryService ruleforgeRepositoryService;
    private final ExternalProcessService externalProcessService;
    private final ExternalRepository externalRepository;

    private final ActionLibraryDeserializer actionLibraryDeserializer;
    private final VariableLibraryDeserializer variableLibraryDeserializer;
    private final ConstantLibraryDeserializer constantLibraryDeserializer;
    private final RuleSetDeserializer ruleSetDeserializer;
    private final DecisionTableDeserializer decisionTableDeserializer;
    private final CrosstableDeserializer crosstableDeserializer;
    private final ScriptDecisionTableDeserializer scriptDecisionTableDeserializer;
    private final DecisionTreeDeserializer decisionTreeDeserializer;
    private final ScorecardDeserializer scorecardDeserializer;
    private final ComplexScorecardDeserializer complexScorecardDeserializer;
    private final ParameterLibraryDeserializer parameterLibraryDeserializer;
    private final BuiltInActionLibraryBuilder builtInActionLibraryBuilder;
    private final List<FunctionDescriptor> coll;
    private List<Deserializer<?>> deserializers = new ArrayList<>(11);
    private final List<FunctionDescriptor> functionDescriptors = new ArrayList<>();
    private final DSLRuleSetBuilder dslRuleSetBuilder;

    // todo
    private final ProjectRepository projectRepository;
    private final RuntimeRepository runtimeRepository;
    private final GitStorageService gitStorageService;

    @PostConstruct
    public void init() {
        this.deserializers = Lists.newArrayList(
                this.actionLibraryDeserializer,
                this.variableLibraryDeserializer,
                this.constantLibraryDeserializer,
                this.ruleSetDeserializer,
                this.decisionTableDeserializer,
                this.scriptDecisionTableDeserializer,
                this.decisionTreeDeserializer,
                this.parameterLibraryDeserializer,
                this.scorecardDeserializer,
                this.complexScorecardDeserializer,
                this.crosstableDeserializer
        );

        for (FunctionDescriptor fun : this.coll) {
            if (fun.isDisabled()) {
                continue;
            }
            this.functionDescriptors.add(fun);
        }
    }

    @PostMapping(value = "/loadXml", produces = "text/json;charset=UTF-8")
    public String loadXml(@RequestParam("files") String files) throws IOException {
        List<Object> result = new ArrayList<>();
        files = Utils.decodeURL(files);
        boolean isaction = false;
        if (files != null) {
            if (files.startsWith("builtinactions")) {
                isaction = true;
            } else {
                String[] paths = files.split(";");
                for (String path : paths) {
                    String[] subpaths = path.split(":");
                    path = Utils.decodeURL(subpaths[0]);
                    String version = null;
                    if (subpaths.length == 2) {
                        version = subpaths[1];
                    }
                    try {
                        InputStream inputStream = null;
                        if (org.springframework.util.StringUtils.hasText(version)) {
                            inputStream = this.ruleforgeRepositoryService.readFile(path, version);
                        } else {
                            inputStream = this.ruleforgeRepositoryService.readFile(path, null);
                        }
                        if (inputStream == null) {
                            // 文件不存在 (readFile 返 null) — 之前返 200+error 串,前端
                            // response.json() throw → "服务端出错"。改为抛 404,前端可展示
                            // "文件不存在,请新建"。
                            log.warn("loadXml: file [{}] version [{}] not found", path, version);
                            throw new org.springframework.web.server.ResponseStatusException(
                                    org.springframework.http.HttpStatus.NOT_FOUND,
                                    "file not found: " + path);
                        }
                        Element element = parseXml(inputStream);
                        for (Deserializer<?> des : this.deserializers) {
                            if (des.support(element)) {
                                result.add(des.deserialize(element, true));
                                if (des instanceof ActionLibraryDeserializer) {
                                    isaction = true;
                                }
                                break;
                            }
                        }
                        inputStream.close();
                    } catch (org.springframework.web.server.ResponseStatusException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        log.error("loadXml", ex);
                        // XML 解析失败 / 其他: 改为 400 而非 200+error 串
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.BAD_REQUEST,
                                "loadXml failed: " + ex.getMessage());
                    }
                }
            }
        }
        if (isaction) {
            List<SpringBean> beans = this.builtInActionLibraryBuilder.getBuiltInActions();
            if (!beans.isEmpty()) {
                ActionLibrary al = new ActionLibrary();
                al.setSpringBeans(beans);
                result.add(al);
            }
        }

        return writeObjectToJson(result);
    }

    @GetMapping("/loadFunctions")
    public List<FunctionDescriptor> loadFunctions() throws ServletException, IOException {
        return this.functionDescriptors;
    }

    @PostMapping("/addVariable")
    public Map<String, Object> addVariable(@RequestParam("clazz") String clazz,
                                           @RequestParam("name") String name,
                                           @RequestParam("label") String label,
                                           @RequestParam("dataType") String dataType,
                                           @RequestParam("dsStatus") Integer dsStatus,
                                           @RequestParam("defaultVal") String defaultVal,
                                           @RequestParam("logicComment") String logicComment,
                                           @RequestParam("categoryLabel") String categoryLabel) throws Exception {
        Map<String, Object> result = new HashMap<>();
        Variable variable = new Variable();
        variable.setName(name);
        variable.setLabel(label);
        variable.setType(Datatype.valueOf(dataType));
        variable.setDsStatus(dsStatus);
        variable.setLogicComment(logicComment);
        variable.setCategoryLabel(categoryLabel);
        result.put("status", this.externalRepository.addVariable(clazz, variable));
        return result;
    }

    @PostMapping("/saveFile")
    public Map<String, Object> saveFile(@RequestParam("file") String file,
                                        @RequestParam("content") String content,
                                        @RequestParam(value = "newVersion", required = false) boolean newVersion) {
        User user = EnvironmentUtils.getLoginUser(null);
        Map<String, Object> result = new HashMap<>();
        result.put("status", true);

        // 检验文件合规性
        try {
            file = Utils.decodeURL(file);
            content = Utils.decodeURL(content);

            Resource resource = new Resource(content, file, "");
            if (this.dslRuleSetBuilder.support(resource)) {
                this.dslRuleSetBuilder.build(resource.getContent());
            } else {
                InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                Element element = parseXml(inputStream);
                for (Deserializer<?> des : deserializers) {
                    if (des.support(element)) {
                        des.deserialize(element, true);
                        break;
                    }
                }
            }

        } catch (Exception e) {
            log.error("saveFile", e);
            result.put("status", false);
            result.put("message", e.getMessage());
            return result;
        }

        // 保存文件
        try {
            this.ruleforgeRepositoryService.saveFile(file, content, newVersion, null, user);
            String saveProjectName = file;
            if (saveProjectName.startsWith("/")) {
                saveProjectName = saveProjectName.substring(1);
            }
        } catch (Exception ex) {
            log.error("saveFile", ex);
            result.put("status", false);
            result.put("message", ex.getMessage());
            return result;
        }

        return result;
    }

    @PostMapping("/checkFileDirty")
    public Map<String, Object> checkFileDirty(@RequestParam String filePath,
                                              @RequestParam String content) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("status", true);

        String oldContent = IOUtils.toString(this.ruleforgeRepositoryService.readFile(Utils.decodeURL(filePath), null, null, false));
        String contentDiff = CompareUtils.compareContent(oldContent, Utils.decodeURL(content));
        map.put("data", org.springframework.util.StringUtils.hasText(contentDiff));

        return map;
    }

    @PostMapping("/loadResourceTreeData")
    public RepositoryFile loadResourceTreeData(@RequestParam(required = false) String project,
                                               @RequestParam(required = false) String forLib,
                                               @RequestParam(required = false) String fileType,
                                               @RequestParam(required = false) String searchFileName) throws Exception {
        project = Utils.decodeURL(project);

        User user = EnvironmentUtils.getLoginUser(null);
        FileType[] types = null;
        if (StringUtils.isNotBlank(forLib) && forLib.equals("true")) {
            types = new FileType[]{FileType.ActionLibrary, FileType.ConstantLibrary, FileType.VariableLibrary, FileType.ParameterLibrary};
        } else if (StringUtils.isNotBlank(fileType)) {
            String[] fileTypes = fileType.split(",");
            types = new FileType[fileTypes.length];
            for (int i = 0; i < fileTypes.length; i++) {
                types[i] = FileType.valueOf(fileTypes[i]);
            }
        } else {
            types = new FileType[]{FileType.UL, FileType.Ruleset, FileType.RuleFlow, FileType.DecisionTable, FileType.ScriptDecisionTable, FileType.DecisionTree, FileType.Scorecard, FileType.ComplexScorecard, FileType.Crosstab};
        }
        try {
            Repository repo = this.ruleforgeRepositoryService.loadRepository(project, user, false, types, searchFileName);
            RepositoryFile repositoryFile = repo.getRootFile();
            if (repo.getPublicResource().getChildren() != null) {
                repositoryFile.getChildren().addAll(repo.getPublicResource().getChildren());
            }
            return repositoryFile;
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    @PostMapping("/startApprovalProcess")
    public Map<String, Object> startApprovalProcess(@RequestParam String project,
                                                    @RequestParam String packageId,
                                                    @RequestParam String title,
                                                    @RequestParam String passRateEffect,
                                                    @RequestParam Double passRateRange,
                                                    @RequestParam String badDebtRateEffect,
                                                    @RequestParam Double badDebtRateRange,
//                                                    @RequestParam String originVersion,
                                                    @RequestParam String targetVersion,
                                                    @RequestParam(required = false) String remark,
                                                    @RequestParam(required = false) MultipartFile file) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("status", false);

            // 加载知识包版本配置
            PackageConfig packageConfig = this.ruleforgeRepositoryService.loadPackageConfigs(project);
            if (targetVersion.equals(packageConfig.getVersion())) {
                result.put("message", "发布的版本为当前运行版本，无需发布");
                return result;
            }

            project = project.replace(".rp", "");
            User user = EnvironmentUtils.getLoginUser(null);
            // todo
            ProjectEntity projectEntity = this.projectRepository.findByName(project);

            if (!org.springframework.util.StringUtils.hasText(targetVersion)) {
                String filePath = "/" + project + "/" + RES_PACKAGE_FILE;
                String content = IOUtils.toString(this.ruleforgeRepositoryService.readFile(filePath));
                targetVersion = this.ruleforgeRepositoryService.saveFile(filePath, content, true, null, user);
            }

            if (!org.springframework.util.StringUtils.hasText(targetVersion)) {
                result.put("message", "没有需要审批的内容，不能发起审批");
                return result;
//            } else if (projectVersion != null && projectVersion.getAuditStatus() > 89) {
//                result.put("message", "该版本已完成审批，不可重复申请审批");
//                return result;
            }

            // todo
            ProjectVersionEntity projectVersion = this.projectRepository.findVersionByProjectIdAndVersionName(projectEntity.getId(), targetVersion);

            // 获取版本差异
            String explain = this.ruleforgeRepositoryService.getPackageVersionDiff(project, targetVersion);
            if (!packageConfig.getLock()) {
                try {
                    project = Utils.decodeURL(project);

                    // todo 保存附件
                    String fileName = null;
                    String filePath = null;

                    packageConfig.setLock(true);
                    this.ruleforgeRepositoryService.updatePackageConfigs(project, packageConfig);
                    String processId = this.externalProcessService.start(project, title, packageConfig.getVersion(), targetVersion, remark, explain, fileName, filePath, passRateEffect, passRateRange, badDebtRateEffect, badDebtRateRange);
                    if (org.springframework.util.StringUtils.isEmpty(processId)) {
                        throw new Exception("processId is null");
                    } else {
                        // 更新审批状态
                        if (projectVersion == null) {
                            projectVersion = new ProjectVersionEntity();
                            projectVersion.setProjectId(projectEntity.getId());
                            projectVersion.setVersionName(targetVersion);
                            projectVersion.setVersionNumReal(VersionUtils.convertVersionToLong(targetVersion));
                            projectVersion.setAuditStatus(20);
                            projectVersion.setComment(remark);
                            projectVersion.setCreateUser(user.getUsername());
                            projectVersion.setCreateTime(new Date());
                            this.projectRepository.insertVersion(projectVersion);
                        } else {
                            projectVersion.setAuditStatus(20);
                            this.projectRepository.updateVersion(projectVersion);
                        }
//                        this.ruleforgeRepositoryService.updatePackageConfigs(project, packageConfig);

                        result.put("processId", processId);
                        result.put("status", true);

                        // todo 测试环境自动通过
                        if ("autoProcess".equals(processId)) {
                            // 自动通过
                            Map<String, Object> auditMap = new HashMap<>();
                            auditMap.put("status", 4);
                            Map<String, String> params = new HashMap<>();
                            params.put("projectName", project);
                            params.put("versionCode", targetVersion);
                            auditMap.put("params", params);
                            updateFileInUseVersion(auditMap);
                        }
                    }
                } catch (Exception e) {
                    log.error("start error", e);

                    // 释放锁
                    packageConfig.setLock(false);
                    this.ruleforgeRepositoryService.updatePackageConfigs(project, packageConfig);
                    result.put("message", e.getMessage());
                }
            } else {
                result.put("message", "有审批中的流程，请完成后再发起");
            }

            return result;
        } catch (Exception e) {
            log.error("startApprovalProcess error", e);
            Map<String, Object> result = new HashMap<>();
            result.put("message", e.getMessage());
            result.put("status", false);
            return result;
        }
    }

    @PostMapping("/updateFileInUseVersion")
    public Map<String, Object> updateFileInUseVersion(@RequestBody Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("updateFileInUseVersion params: {}", map);
            Integer status = (Integer) map.get("status");
            Map<String, String> params = (Map<String, String>) map.get("params");
            String project = params.get("projectName");
            String version = params.get("versionCode");

            // todo 加载知识包版本配置
            ProjectEntity projectEntity = this.projectRepository.findByName(project);
            ProjectVersionEntity projectVersion = this.projectRepository.findVersionByProjectIdAndVersionName(projectEntity.getId(), version);

            PackageConfig packageConfig = this.ruleforgeRepositoryService.loadPackageConfigs(project);
            if (projectVersion.getAuditStatus() > 89) {
                throw new Exception("该版本已完成审批");
            }

            // 更新配置
            int auditStatus = 91;
            if (status == 4) {
                Date date = new Date();
                // todo
                this.runtimeRepository.upsertConfig(projectEntity.getId(), projectVersion.getPackageId(), "prod", version, projectVersion.getCreateUser());
                auditStatus = 90;

                CacheUtils.getKnowledgeCache().removeKnowledgeByProjectName(project);

                // Git integration: create tag + push + notify executor on approval
                if (gitStorageService.repoExists(project)) {
                    try {
                        String packageId = projectVersion.getPackageId();
                        if (packageId != null) {
                            String tagName = "pkg/" + packageId + "/" + version;
                            gitStorageService.createTag(project, tagName, "main");
                            gitStorageService.push(project);
                            log.info("Created Git tag [{}] for approved version [{}] in project [{}]",
                                    tagName, version, project);
                        }
                    } catch (Exception e) {
                        log.error("Git tag creation failed during approval for project [{}]", project, e);
                    }
                }

                // Notify executor to refresh knowledge cache
                try {
                    String fullPackageId = project + "/" + projectVersion.getPackageId();
                    this.externalProcessService.syncExec(fullPackageId, "prod",
                            projectVersion.getCreateUser(), null, null, null);
                } catch (Exception e) {
                    log.error("Failed to notify executor for project [{}]", project, e);
                }
            }
            // 更新审批状态
            projectVersion.setAuditStatus(auditStatus);
            this.projectRepository.updateVersion(projectVersion);

            packageConfig.setLock(false);
            this.ruleforgeRepositoryService.updatePackageConfigs(project, packageConfig);

            result.put("status", true);
        } catch (Exception e) {
            log.error("updateFileInUseVersion error", e);
            result.put("status", false);
        }

        return result;
    }

    @PostMapping("/scriptValidation")
    public List<ErrorInfo> scriptValidation(@RequestParam String content, @RequestParam String type) {
        return scriptValidationText(content, type);
    }

    @PostMapping("/loadReferenceFiles")
    public List<RefFile> loadReferenceFiles(@RequestParam(required = false) String project,
                                            HttpServletRequest req) {
        String path = req.getParameter("path");
        path = Utils.decodeURL(path);
        String searchText = buildSearchText(path, req, false);
        String searchTextScript = buildSearchText(path, req, true);
        try {
            List<String> files = ruleforgeRepositoryService.getReferenceFiles(project, Utils.decodeURL(path), searchText, searchTextScript);
            List<RefFile> refFiles = new ArrayList<>();
            for (String file : files) {
                RefFile ref = new RefFile();
                refFiles.add(ref);
                ref.setPath(file);
                if (file.endsWith(FileType.Ruleset.toString())) {
                    ref.setEditor("/ruleset-editor.html");
                    ref.setType("决策集");
                } else if (file.endsWith(FileType.UL.toString())) {
                    ref.setEditor("/ul-editor.html");
                    ref.setType("脚本决策集");
                } else if (file.endsWith(FileType.DecisionTable.toString())) {
                    ref.setEditor("/decision-table-editor.html");
                    ref.setType("决策表");
                } else if (file.endsWith(FileType.ScriptDecisionTable.toString())) {
                    ref.setEditor("/scriptdecisiontableeditor");
                    ref.setType("脚本决策表");
                } else if (file.endsWith(FileType.DecisionTree.toString())) {
                    ref.setEditor("/decision-tree-editor.html");
                    ref.setType("决策树");
                } else if (file.endsWith(FileType.RuleFlow.toString())) {
                    ref.setEditor("/rule-flow-designer.html");
                    ref.setType("决策流");
                } else if (file.endsWith(FileType.Scorecard.toString())) {
                    ref.setEditor("/score-card-editor.html");
                    ref.setType("评分卡");
                } else if (file.endsWith(FileType.ComplexScorecard.toString())) {
                    ref.setEditor("/complexscorecard-editor.html");
                    ref.setType("复杂评分卡");
                }
                int pos = file.lastIndexOf("/");
                String name = file;
                if (pos > -1) {
                    name = file.substring(pos + 1);
                }
                ref.setName(name);
            }
            return refFiles;
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    private String buildSearchText(String path, HttpServletRequest req, boolean isScript) {
        StringBuilder sb = new StringBuilder();
        if (path.endsWith(FileType.ActionLibrary.toString())) {
            if (isScript) {
                sb.append(req.getParameter("beanLabel"));
                sb.append(".");
                sb.append(req.getParameter("methodLabel"));
            } else {
                sb.append("bean=\"").append(req.getParameter("beanName")).append("\"");
                sb.append(" bean-label=\"").append(req.getParameter("beanLabel")).append("\"");
                sb.append(" method-label=\"").append(req.getParameter("methodLabel")).append("\"");
                sb.append(" method-name=\"").append(req.getParameter("methodName")).append("\"");
            }
            return sb.toString();
        } else if (path.endsWith(FileType.ConstantLibrary.toString())) {
            if (isScript) {
                sb.append(req.getParameter("constCategoryLabel"));
                sb.append(".");
                sb.append(req.getParameter("constLabel"));
            } else {
                sb.append("const-category=\"").append(req.getParameter("constCategoryLabel")).append("\"");
                sb.append(" const=\"").append(req.getParameter("constName")).append("\"");
            }
            return sb.toString();
        } else if (path.endsWith(FileType.ParameterLibrary.toString())) {
            if (isScript) {
                sb.append("参数.");
                sb.append(req.getParameter("varLabel"));
            } else {
                sb.append("var-category=\"参数\"");
                sb.append(" var=\"").append(req.getParameter("varName")).append("\"");
            }
            return sb.toString();
        } else if (path.endsWith(FileType.VariableLibrary.toString())) {
            if (isScript) {
                sb.append(req.getParameter("varCategory"));
                sb.append(".");
                sb.append(req.getParameter("varLabel"));
            } else {
//                sb.append("var-category=\"").append(req.getParameter("varCategory")).append("\"");
                sb.append(" var=\"").append(req.getParameter("varName")).append("\"");
            }
            return sb.toString();

        } else {
            return Utils.decodeURL(path);
        }
    }

    @PostMapping("/findRuleByKey")
    public List<com.ruleforge.model.rule.Rule> findRuleByKey(@RequestParam String ruleKey,
                                                              @RequestParam String projectName) throws Exception {
        List<com.ruleforge.model.rule.Rule> ruleList = new ArrayList<>();
        User user = EnvironmentUtils.getLoginUser(null);
        FileType[] types = new FileType[]{FileType.RulesetLib};
        Repository repo = this.ruleforgeRepositoryService.loadRepository(projectName, user, false, types, "");
        List<RepositoryFile> repositoryFileList = fetchRsl(repo.getRootFile());
        for (RepositoryFile repositoryFile : repositoryFileList) {
            try (InputStream inputStream = this.ruleforgeRepositoryService.readFile(repositoryFile.getFullPath(), null)) {
                Element element = parseXml(inputStream);
                RuleSet ruleSet = ruleSetDeserializer.deserialize(element);
                for (com.ruleforge.model.rule.Rule rule : ruleSet.getRules()) {
                    if (ruleKey.equals(rule.getName())) {
                        ruleList.add(rule);
                    }
                }
            } catch (Exception ex) {
                throw new RuleException(ex);
            }
        }
        return ruleList;
    }

    private List<RepositoryFile> fetchRsl(RepositoryFile repositoryFile) {
        List<RepositoryFile> list = new ArrayList<>();
        if (repositoryFile.getType() == com.ruleforge.console.repository.model.Type.rule) {
            list.add(repositoryFile);
        } else if (repositoryFile.getChildren() != null) {
            for (RepositoryFile child : repositoryFile.getChildren()) {
                list.addAll(fetchRsl(child));
            }
        }
        return list;
    }

    private List<ErrorInfo> scriptValidationText(String content, String type) {
        if (StringUtils.isNotBlank(content)) {
            ScriptType scriptType = ScriptType.valueOf(type);
            ANTLRInputStream antlrInputStream = new ANTLRInputStream(content);
            RuleParserLexer lexer = new RuleParserLexer(antlrInputStream);
            CommonTokenStream steam = new CommonTokenStream(lexer);
            RuleParserParser parser = new RuleParserParser(steam);
            parser.removeErrorListeners();
            ScriptErrorListener errorListener = new ScriptErrorListener();
            parser.addErrorListener(errorListener);
            switch (scriptType) {
                case Script:
                    parser.ruleSet();
                    break;
                case DecisionNode:
                    parser.condition();
                    break;
                case ScriptNode:
                    parser.actions();
            }
            return errorListener.getInfos();
        }

        return new ArrayList<>();
    }


}
