package com.ruleforge.console.app.v1;

import com.ruleforge.console.entity.FileEntity;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.v1.exec.V1PublishedBundle;
import com.ruleforge.console.repository.data.FileRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.exception.RuleException;
import com.ruleforge.v1.ast.NodeBase;
import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.RuleAssetIO;
import com.ruleforge.v1.ast.library.Library;
import com.ruleforge.v1.ast.library.LibraryType;
import com.ruleforge.v1.ast.library.Libraries;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V1 决策流闭包解析器(V7.6)— 把一个 {@code .v1flow.json} 解析成完整可执行的 {@link V1PublishedBundle}。
 *
 * <p>镜像前端 {@code /v1/execute} 的拼装逻辑(前端只发 asset,ruleRef/库未端到端接),搬到服务端:
 * <ol>
 *   <li>{@link #readAsset} 读 flow 文件 → {@link RuleAsset}(Jackson 多态已就绪)。</li>
 *   <li>遍历 {@code asset.nodes},凡 {@code ruleRef != null} → 读规则文件 → {@link NodeBase} 入 ruleFiles。</li>
 *   <li>{@link #resolveLibraries} 扫项目全部 {@code .v1lib.json} → 按 {@link LibraryType} 装进 {@link Libraries}。</li>
 * </ol>
 *
 * <p>库发现:MVP 按项目级 — 一个项目的全部 vl/cl/pl 库都装进 bundle(项目库规模小,全装正确且简单)。
 * 后续若需"流只引用部分库",再改 asset 加 librariesRef 显式引用。
 *
 * <p>依赖 console 的 repository 抽象(读文件 / 列项目文件 / 查项目 id),不依赖 git 细节
 * (readFile 内部 git-first / DB-fallback,对调用方透明)。
 */
@Component
public class V1BundleResolver {

    private static final Logger log = LoggerFactory.getLogger(V1BundleResolver.class);
    private static final String V1LIB_SUFFIX = ".v1lib.json";

    private final RuleForgeRepositoryService repoService;
    private final FileRepository fileRepository;
    private final ProjectRepository projectRepository;

    public V1BundleResolver(RuleForgeRepositoryService repoService,
                            FileRepository fileRepository,
                            ProjectRepository projectRepository) {
        this.repoService = repoService;
        this.fileRepository = fileRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * 解析决策流的执行闭包。
     *
     * @param flowPath 决策流全路径,如 {@code /test01/V1决策流/loan.v1flow.json}
     * @return {asset, libraries, ruleFiles} 三件套
     */
    public V1PublishedBundle resolve(String flowPath) throws Exception {
        RuleAsset asset = readAsset(flowPath);
        Map<String, NodeBase> ruleFiles = resolveRuleFiles(asset);
        Libraries libraries = resolveLibraries(projectNameOf(flowPath));
        return new V1PublishedBundle(asset, libraries, ruleFiles);
    }

    /** 读 flow 文件 → RuleAsset。 */
    private RuleAsset readAsset(String flowPath) throws Exception {
        String json = readText(flowPath);
        try {
            return RuleAssetIO.mapper().readValue(json, RuleAsset.class);
        } catch (Exception e) {
            throw new RuleException("解析决策流 JSON 失败 [" + flowPath + "]: " + e.getMessage(), e);
        }
    }

    /**
     * 遍历节点,凡 ruleRef 非空 → 读规则文件 → NodeBase。
     * ruleRef = 规则文件绝对路径(如 /proj/V1规则集/precheck.v1rs.json),顶层是 RuleSet/DecisionTable/ScoreCard Node。
     */
    private Map<String, NodeBase> resolveRuleFiles(RuleAsset asset) throws Exception {
        Map<String, NodeBase> ruleFiles = new HashMap<>();
        if (asset.getNodes() == null) {
            return ruleFiles;
        }
        for (NodeBase node : asset.getNodes().values()) {
            String ref = node.getRuleRef();
            if (ref == null || ref.trim().isEmpty()) {
                continue;
            }
            try {
                // readText + 解析同在 try 内:read 失败(文件不存在)或 parse 失败都带 ref 路径上下文
                NodeBase ruleNode = RuleAssetIO.mapper().readValue(readText(ref), NodeBase.class);
                ruleFiles.put(ref, ruleNode);
            } catch (Exception e) {
                throw new RuleException("解析规则文件失败 [" + ref + "](被 " + node.getId() + " 引用): " + e.getMessage(), e);
            }
        }
        return ruleFiles;
    }

    /**
     * 扫项目全部 .v1lib.json → Libraries(按 type 装 vl/cl/pl 槽)。
     * 解析失败的库文件跳过 + warn(不阻断发布)。
     */
    private Libraries resolveLibraries(String projectName) {
        ProjectEntity project = projectRepository.findByNameSelectId(projectName);
        if (project == null || project.getId() == null) {
            log.warn("V1 publish: 项目 [{}] 未找到,跳过库解析", projectName);
            return null;
        }
        List<FileEntity> files = fileRepository.findByProjectId(project.getId());
        Libraries libraries = new Libraries();
        boolean any = false;
        for (FileEntity f : files) {
            String path = f.getFilePath();
            if (path == null || !path.endsWith(V1LIB_SUFFIX)) {
                continue;
            }
            try {
                Library lib = RuleAssetIO.mapper().readValue(readText(path), Library.class);
                if (slotLibrary(libraries, lib)) {
                    any = true;
                }
            } catch (Exception e) {
                log.warn("V1 publish: 跳过无法解析的库文件 {} - {}", path, e.getMessage());
            }
        }
        return any ? libraries : null;
    }

    /** 按 LibraryType 装进对应槽;返回是否装入成功。 */
    private boolean slotLibrary(Libraries libraries, Library lib) {
        if (lib == null || lib.getType() == null) {
            return false;
        }
        switch (lib.getType()) {
            case VARIABLE:
                libraries.setVl(lib);
                return true;
            case CONSTANT:
                libraries.setCl(lib);
                return true;
            case PARAMETER:
                libraries.setPl(lib);
                return true;
            default:
                return false;
        }
    }

    private String readText(String path) throws Exception {
        try (InputStream is = repoService.readFile(path)) {
            if (is == null) {
                throw new RuleException("V1 文件不存在 [" + path + "]");
            }
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    /** flowPath → projectName:/test01/V1决策流/x.v1flow.json → test01。 */
    static String projectNameOf(String flowPath) {
        if (flowPath == null || flowPath.isEmpty()) {
            return "";
        }
        String p = flowPath.startsWith("/") ? flowPath.substring(1) : flowPath;
        int slash = p.indexOf('/');
        return slash > 0 ? p.substring(0, slash) : p;
    }
}
