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
import com.ruleforge.console.util.GitPathUtils;
import com.ruleforge.exception.RuleException;
import com.ruleforge.config.CacheUtils;
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
    private final EnvironmentUtils environmentUtils;
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
            User user = environmentUtils.getLoginUser(null);
            boolean classify = getClassify(classifyValue, req, resp);
            projectName = Utils.decodeURL(projectName);
            FileType[] types = null;
            if (StringUtils.hasText(typesStr) && !typesStr.equals("all")) {
                switch (typesStr) {
                    case "lib":
                        types = new FileType[]{FileType.VariableLibrary, FileType.ConstantLibrary, FileType.ParameterLibrary, FileType.ActionLibrary};
                        break;
                    // V6.20.0 P2:删老 urule 类别(rule/table/tree),只留 lib + flow;
                    // DRL 不分类(用户按需搜)。
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
            String projectName = GitPathUtils.extractProjectName(path);
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
        // readFile 在 Git/DB 都查不到时返 null(见 RuleForgeRepositoryServiceImpl.readFile 注释),
        // 这里必须显式判空 — 否则 IOUtils.toString(null) NPE 500,调用方拿不到有效信息。
        if (inputStream == null) {
            throw new RuleException("File [" + path + "] not found.");
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
        // V6.20.0 P2:UI 老 urule 规则类型(.rs.xml/.dt.xml/.dtree.xml/.sdt.xml/.sc/.scc/.ct.xml/.ul.xml)
        // 入口已移除。前端不再传这些 FileType,所以这里不再需要初始模板分支。
        // 后端 FileType 枚举值保留(老 .rp 内已有文件仍能 loadXml 解析)。
        if (fileType.equals(FileType.Drl)) {
            // V6.20.0:DRL 新建文件初始模板 — 最小可编译骨架
            content.append("rule \"rule01\"\n");
            content.append("when\n");
            content.append("then\n");
            content.append("end");
        } else if (fileType.equals(FileType.Dmn)) {
            // V6.20.0 P3:DMN 1.3 最小可被 Kie DMNCompiler 编译的骨架
            // (definitions + single decision table) — UI 创建占位,后续用户从外部导入覆盖
            content.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            content.append("<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\"\n");
            content.append("             xmlns:dmndi=\"https://www.omg.org/spec/DMN/20191111/DMNDI/\"\n");
            content.append("             xmlns:dc=\"http://www.omg.org/spec/DMN/20180521/DC/\"\n");
            content.append("             id=\"definitions_stub\"\n");
            content.append("             name=\"stub\"\n");
            content.append("             namespace=\"stub\">\n");
            content.append("  <decision id=\"decision_stub\" name=\"stub\">\n");
            content.append("    <decisionTable id=\"decisionTable_stub\" hitPolicy=\"FIRST\">\n");
            content.append("      <input id=\"input1\"><inputExpression typeRef=\"string\"><text>stub</text></inputExpression></input>\n");
            content.append("      <output id=\"output1\"/>\n");
            content.append("    </decisionTable>\n");
            content.append("  </decision>\n");
            content.append("</definitions>\n");
        } else if (fileType.equals(FileType.Pmml)) {
            // V6.20.0 P3:PMML 4.4 最小 Scorecard 骨架(pmml4s 1.5.6 可解析)
            // (PMML 顶层字段已填,子结构留空 = 0 rules emitted,但 dispatcher 不抛错)
            content.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            content.append("<PMML xmlns=\"http://www.dmg.org/PMML-4_4\" version=\"4.4\">\n");
            content.append("  <Header copyright=\"\" description=\"stub\"/>\n");
            content.append("  <DataDictionary>\n");
            content.append("    <DataField name=\"stub\" dataType=\"string\" optype=\"categorical\"/>\n");
            content.append("  </DataDictionary>\n");
            content.append("  <Scorecard modelName=\"stub\" useReasonCodes=\"false\" initialScore=\"0\" baselineMethod=\"0\" reasonCodeAlgorithm=\"pointsBelow\">\n");
            content.append("    <MiningSchema>\n");
            content.append("      <MiningField name=\"stub\" usageType=\"active\"/>\n");
            content.append("    </MiningSchema>\n");
            content.append("  </Scorecard>\n");
            content.append("</PMML>\n");
        } else if (fileType.equals(FileType.V1Flow)) {
            // V7.0.0:V1 决策流最小 RuleAsset 骨架(.json)。
            // Start + Decision 两节点线性流,后端 V1FlowRunner 可直接执行;前端画布可加载编辑。
            content.append("{\"version\":\"1.0\",\"id\":\"untitled\",\"name\":\"未命名决策流\",");
            content.append("\"flow\":{\"id\":\"f1\",\"name\":\"Flow\",\"version\":\"1.0\",\"flowElements\":[");
            content.append("{\"type\":\"startEvent\",\"id\":\"start\",\"name\":\"Start\",\"implementation\":\"Start:start\",\"position\":{\"x\":80,\"y\":200}},");
            content.append("{\"type\":\"endEvent\",\"id\":\"decision\",\"name\":\"Decision\",\"implementation\":\"Decision:decision\",\"position\":{\"x\":400,\"y\":200}},");
            content.append("{\"type\":\"sequenceFlow\",\"id\":\"f1\",\"sourceRef\":\"start\",\"targetRef\":\"decision\"}");
            content.append("]},");
            content.append("\"nodes\":{");
            content.append("\"start\":{\"id\":\"start\",\"type\":\"Start\",\"name\":\"Start\",\"schema\":\"Fact\"},");
            content.append("\"decision\":{\"id\":\"decision\",\"type\":\"Decision\",\"name\":\"Decision\",\"outputs\":[\"approve\",\"reject\"],\"decisionField\":\"decision\",\"defaultOutput\":\"reject\"}");
            content.append("}}");
        } else if (fileType.equals(FileType.V1Library)) {
            // V7.4:V1 库最小骨架(.v1lib.json,默认 PARAMETER 参数库;前端可改 type 为 CONSTANT/VARIABLE)
            content.append("{\"type\":\"PARAMETER\",\"name\":\"未命名参数库\",\"entries\":[]}");
        } else if (fileType.equals(FileType.V1RuleSet)) {
            // V7.5:V1 规则集最小骨架(.v1rs.json,hitPolicy=FIRST,空规则列表)
            content.append("{\"id\":\"ruleset01\",\"type\":\"RuleSet\",\"name\":\"未命名规则集\",\"hitPolicy\":\"FIRST\",\"rules\":[]}");
        } else if (fileType.equals(FileType.V1DecisionTable)) {
            // V7.5:V1 决策表最小骨架(.v1dt.json,空 inputs/outputs/rows)
            content.append("{\"id\":\"dt01\",\"type\":\"DecisionTable\",\"name\":\"未命名决策表\",\"inputs\":[],\"outputs\":[],\"rows\":[]}");
        } else if (fileType.equals(FileType.V1ScoreCard)) {
            // V7.5:V1 评分卡最小骨架(.v1sc.json,空 cards/bands,aggregation=SUM)
            content.append("{\"id\":\"sc01\",\"type\":\"ScoreCard\",\"name\":\"未命名评分卡\",\"cards\":[],\"bands\":[],\"aggregation\":\"SUM\"}");
        } else {
            String name = getRootTagName(fileType);
            content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            content.append("<").append(name).append(">");
            content.append("</").append(name).append(">");
        }

        User user = environmentUtils.getLoginUser(null);
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
        } else if (fileType.equals(FileType.Drl)) {
            // V6.20.0:DRL 文件 → Type.drl(树 buildData drl case 渲染)
            newFileInfo.setType(Type.drl);
        } else if (fileType.equals(FileType.RuleFlow)) {
            newFileInfo.setType(Type.flow);
        } else if (fileType.equals(FileType.Dmn)) {
            // V6.20.0 P3:DMN 文件 → Type.dmn(树 buildData dmn case 渲染)
            newFileInfo.setType(Type.dmn);
        } else if (fileType.equals(FileType.Pmml)) {
            // V6.20.0 P3:PMML 文件 → Type.pmml(树 buildData pmml case 渲染)
            newFileInfo.setType(Type.pmml);
        } else if (fileType.equals(FileType.V1Flow)) {
            // V7.0.0:V1 决策流 → Type.v1flow(树 buildData v1flow case + handleFileOpen 开画布)
            newFileInfo.setType(Type.v1flow);
        } else if (fileType.equals(FileType.V1Library)) {
            // V7.4:V1 库 → Type.v1library(树 buildData v1library + handleFileOpen 开库编辑器)
            newFileInfo.setType(Type.v1library);
        } else if (fileType.equals(FileType.V1RuleSet)) {
            // V7.5:V1 规则集 → Type.v1ruleset(树 buildData v1ruleset + handleFileOpen 开规则集编辑器)
            newFileInfo.setType(Type.v1ruleset);
        } else if (fileType.equals(FileType.V1DecisionTable)) {
            // V7.5:V1 决策表 → Type.v1decisiontable(树 buildData v1decisiontable + handleFileOpen 开决策表编辑器)
            newFileInfo.setType(Type.v1decisiontable);
        } else if (fileType.equals(FileType.V1ScoreCard)) {
            // V7.5:V1 评分卡 → Type.v1scorecard(树 buildData v1scorecard + handleFileOpen 开评分卡编辑器)
            newFileInfo.setType(Type.v1scorecard);
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
            // V5.18 修复 — 之前是直接 return,前端看到 200 + 空 body,
            // 浏览器既不弹下载也不报错,用户不知道为啥没下载。
            // 现在返 403 + JSON 错误体,前端至少能 log 出来。
            log.warn("exportProjectBackupFile denied: user={} no export permission", user.getUsername());
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"error\":\"No export permission\"}");
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
            // V5.18 修复 — 之前 readFile() 返 null(新项目没 packageConfig.xml)时
            // IOUtils.toByteArray(null) NPE → 整个导出 500,前面的 tar entries 都白写了。
            // 现在 null 时写空字符串(import 端 IOUtils.toString 处理空串 ok)。
            String packageConfigPath = "/" + projectName + "/" + PACKAGE_CONFIG_FILE;
            InputStream packageConfig = this.ruleforgeRepositoryService.readFile(packageConfigPath);
            if (packageConfig != null) {
                bytes = IOUtils.toByteArray(packageConfig);
            } else {
                log.warn("exportProjectBackupFile: packageConfig.xml not found at {}, 写空串占位(项目可能没有知识包配置)", packageConfigPath);
                bytes = new byte[0];
            }
            tarEntry = new TarArchiveEntry("packageConfig.xml");
            tarEntry.setSize(bytes.length);
            tOut.putArchiveEntry(tarEntry);
            tOut.write(bytes, 0, bytes.length);
            tOut.closeArchiveEntry();
            // todo
            ProjectEntity projectEntity = this.projectRepository.findByName(projectName);
            // V5.18 修复 — findByName 可能返 null(项目不在 DB,但 git 仓库里还有)。
            // 之前直接 projectEntity.getId() NPE。现在找不到就写空数组,import 端 batchInsertVersions
            // 处理空集合 ok,不会 NPE。
            List<ProjectVersionEntity> projectVersionEntityList = projectEntity != null
                    ? this.projectRepository.findVersionsByProjectId(projectEntity.getId(), null, false)
                    : java.util.Collections.emptyList();
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

        // V5.18 修复 — tar.gz 损坏 / 不是有效备份文件时 extraImportGzip 返 null
        // repositoryFile(找不到 systemView.json),下面 .getName() 直接 NPE → 500
        // 带 stacktrace 泄给前端。现在直接 status=false + 友好提示。
        if (repositoryFile == null) {
            log.error("importProject failed: extraImportGzip 没解析出 systemView.json,文件可能损坏或不是有效备份: {}",
                    file.getOriginalFilename());
            result.put("content", "备份文件解析失败,请确认是 exportProjectBackupFile 导出的 tar.gz");
            return result;
        }

        String projectName = repositoryFile.getName();
        if (StringUtils.isEmpty(projectName)) {
            log.error("importProject failed: systemView.json 解析成功但 projectName 为空");
            result.put("content", "备份文件 systemView.json 缺项目名,文件可能损坏");
            return result;
        }

        Long lockVersion = this.ruleforgeRepositoryService.lockPath(projectName, user);
        if (lockVersion == null) {
            result.put("content", "项目已被锁定");
            return result;
        }

        // V5.18 修复 — 用"成功标记"位跟踪 import 主流程是否跑完。
        // 之前 status=true 在 try{} 末尾,即使内部 try-catch 吞掉 delete/import 异常,
        // 也会无条件覆盖 status=true → 前端 ImportProjectDialog 永远弹"导入成功",
        // 真出错查不到(用户不知道)。
        // 现在只有 deleteProject + importFromZip + createFile + batchInsertVersions
        // 都成功才置 true,任何一步抛异常 → 留在 false + content 写具体错误。
        boolean importSucceeded = false;
        String importError = null;
        try {
            // 删除旧版本
            try {
                this.ruleforgeRepositoryService.deleteProject(projectName, user);
                log.info("{} 删除成功 {}秒", projectName, (System.currentTimeMillis() - start.getTime()) / 1000D);
            } catch (Exception e) {
                log.error("deleteFile {}秒", (System.currentTimeMillis() - start.getTime()) / 1000D, e);
                // deleteProject 失败不应该 block import — 可能是新项目本来就不存在。
                // 但记录下来让 operator 看日志。
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

                // 所有步骤成功才置 true
                importSucceeded = true;
            } catch (Exception e) {
                log.error("extraImportZipJcr", e);
                importError = "导入数据失败: " + e.getMessage();
            }

            // 清理缓存(无关 import 成功失败,缓存总是要清)
            CacheUtils.getKnowledgeCache().removeKnowledgeByProjectName(projectName);

            double processTime = (System.currentTimeMillis() - start.getTime()) / 1000D;
            if (importSucceeded) {
                log.info("{} 导入成功 {}", projectName, (req.getContextPath() + "/ruleforge/frame " + processTime + "秒"));
                result.put("status", true);
            } else {
                log.error("{} 导入失败 {}", projectName, processTime + "秒,原因: " + importError);
                result.put("status", false);
                result.put("content", importError != null ? importError : "导入失败,请查看服务器日志");
            }
        } catch (Exception e) {
            // 外层 catch(lock/unlock 路径上的意外异常),不影响 importSucceeded 状态
            log.error("importProject {}", projectName, e);
            result.put("status", importSucceeded);
            if (!importSucceeded) {
                result.put("content", "导入失败: " + e.getMessage());
            }
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
        User user = environmentUtils.getLoginUser(null);
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
        User user = environmentUtils.getLoginUser(null);
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
        User user = environmentUtils.getLoginUser(null);
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
        User user = environmentUtils.getLoginUser(null);
        return this.ruleforgeRepositoryService.createProject(projectName, user, classify);
    }

    @PostMapping("/lockFile")
    public void lockFile(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String file = req.getParameter("file");
        User user = environmentUtils.getLoginUser(new RequestContext(req, resp));
        this.ruleforgeRepositoryService.lockPath(file, user);
    }

    @PostMapping("/unlockFile")
    public void unlockFile(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String file = req.getParameter("file");
        User user = environmentUtils.getLoginUser(new RequestContext(req, resp));
        this.ruleforgeRepositoryService.unlockPath(file, user, null);
    }

    @PostMapping("/copyFile")
    public void copyFile(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String newFullPath = req.getParameter("newFullPath");
        String oldFullPath = req.getParameter("oldFullPath");
        newFullPath = Utils.decodeURL(newFullPath);
        oldFullPath = Utils.decodeURL(oldFullPath);
        try {
            InputStream inputStream = this.ruleforgeRepositoryService.readFile(oldFullPath, null);
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            inputStream.close();
            User user = environmentUtils.getLoginUser(new RequestContext(req, resp));
            this.ruleforgeRepositoryService.createFile(newFullPath, content, user);
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    @PostMapping("/fileRename")
    public void fileRename(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String path = req.getParameter("path");
        path = Utils.decodeURL(path);
        String newPath = req.getParameter("newPath");
        newPath = Utils.decodeURL(newPath);
        this.ruleforgeRepositoryService.fileRename(path, newPath);
    }

    /**
     * V5.11: 委托给 GitPathUtils.extractProjectName,与 RuleForgeRepositoryServiceImpl 共用.
     * 原 FrameController.extractProjectNameFromPath 删除.
     */

}
