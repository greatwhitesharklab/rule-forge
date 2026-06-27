package com.ruleforge.console.app.v1;

import com.ruleforge.console.app.v1.V1PublishEntity;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.exception.RuleException;
import com.ruleforge.v1.ast.RuleAssetIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * V1 原生发布服务(V7.6)— 决策流 draft → published。
 *
 * <p>发布 = {@link V1BundleResolver} 解析闭包 → 序列化 bundle 入 {@code rf_v1_publish}(不可变快照)
 * → git 打 tag {@code v1pub/{project}/{flow}/{version}}(源码追溯,best-effort)。
 * 砍掉老 .rp 管线:无审批/影子/批测,状态只有 draft / published,再发布覆盖当前版本。
 *
 * <p>bundle 存 DB 而非 git 文件 — 不污染项目树,executor 经 {@code GET /v1/publish/bundle} 直取。
 * 历史 = git tags(MVP,本表只记当前版本;历史版本经 git tag 重建)。
 */
@Service
public class V1PublishService {

    private static final Logger log = LoggerFactory.getLogger(V1PublishService.class);

    private final V1BundleResolver bundleResolver;
    private final V1PublishMapper publishMapper;
    private final GitStorageService gitStorageService;

    public V1PublishService(V1BundleResolver bundleResolver,
                            V1PublishMapper publishMapper,
                            GitStorageService gitStorageService) {
        this.bundleResolver = bundleResolver;
        this.publishMapper = publishMapper;
        this.gitStorageService = gitStorageService;
    }

    /**
     * 发布决策流:解析闭包 → 冻结 bundle → 记 rf_v1_publish → git tag。
     *
     * @param flowPath  决策流全路径
     * @param username  发布人(login user,null 则记 null)
     * @return 发布结果(版本号 + git tag + 状态)
     */
    public PublishResult publish(String flowPath, String username) throws Exception {
        String project = V1BundleResolver.projectNameOf(flowPath);
        V1PublishedBundle bundle = bundleResolver.resolve(flowPath);
        String bundleJson = RuleAssetIO.mapper().writeValueAsString(bundle);

        V1PublishEntity existing = publishMapper.selectByFlow(project, flowPath);
        String version = nextVersion(existing);
        String gitTag = createGitTagBestEffort(project, flowPath, version);

        V1PublishEntity row = existing != null ? existing : new V1PublishEntity();
        row.setProject(project);
        row.setFlowPath(flowPath);
        row.setCurrentVersion(version);
        row.setCurrentGitTag(gitTag);
        row.setPublishBundle(bundleJson);
        row.setPublishUser(username);
        row.setPublishTime(new Date());
        if (existing != null) {
            publishMapper.updateById(row);
        } else {
            publishMapper.insert(row);
        }
        log.info("V1 publish: {} 发布成功 version={} gitTag={}", flowPath, version, gitTag);
        return new PublishResult(version, gitTag, "published");
    }

    /** 取已发布 bundle(当前版本);未发布返 null。 */
    public V1PublishedBundle getPublishedBundle(String flowPath) {
        V1PublishEntity row = publishMapper.selectByFlow(V1BundleResolver.projectNameOf(flowPath), flowPath);
        if (row == null) {
            return null;
        }
        try {
            return RuleAssetIO.mapper().readValue(row.getPublishBundle(), V1PublishedBundle.class);
        } catch (Exception e) {
            throw new RuleException("发布 bundle 反序列化失败 [" + flowPath + "]: " + e.getMessage(), e);
        }
    }

    /** 发布状态:draft(未发布)/ published(含当前版本号)。 */
    public PublishStatus status(String flowPath) {
        V1PublishEntity row = publishMapper.selectByFlow(V1BundleResolver.projectNameOf(flowPath), flowPath);
        if (row == null) {
            return new PublishStatus("draft", null, null);
        }
        return new PublishStatus("published", row.getCurrentVersion(), row.getPublishTime());
    }

    /** 版本号:首次 1.0.0;再发布 fix 位 +1(1.0.0 → 1.0.1)。格式不符重置 1.0.0。 */
    static String nextVersion(V1PublishEntity existing) {
        if (existing == null || existing.getCurrentVersion() == null) {
            return "1.0.0";
        }
        String[] p = existing.getCurrentVersion().split("\\.");
        if (p.length != 3) {
            return "1.0.0";
        }
        try {
            return p[0] + "." + p[1] + "." + (Integer.parseInt(p[2]) + 1);
        } catch (NumberFormatException e) {
            return "1.0.0";
        }
    }

    /**
     * git tag 源码追溯(best-effort):repo 不存在或 tag 失败 → 返 null,bundle 已在 DB 不受影响。
     * tag = {@code v1pub/{project}/{flowBaseName}/{version}},git ref 允许 "/"。
     */
    private String createGitTagBestEffort(String project, String flowPath, String version) {
        try {
            if (!gitStorageService.repoExists(project)) {
                return null;
            }
            String baseName = flowPath;
            int slash = baseName.lastIndexOf('/');
            if (slash >= 0) {
                baseName = baseName.substring(slash + 1);
            }
            // 剥全部扩展名(.v1flow.json 双后缀)→ 裸名(loan.v1flow.json → loan);首 '.' 前
            int dot = baseName.indexOf('.');
            if (dot > 0) {
                baseName = baseName.substring(0, dot);
            }
            String tag = "v1pub/" + project + "/" + baseName + "/" + version;
            gitStorageService.createTag(project, tag, "main");
            return tag;
        } catch (Exception e) {
            log.warn("V1 publish: git tag 创建失败(best-effort,bundle 已存 DB)- {}", e.getMessage());
            return null;
        }
    }

    /** 发布结果(version / gitTag / status)。 */
    public static class PublishResult {
        private final String version;
        private final String gitTag;
        private final String status;

        public PublishResult(String version, String gitTag, String status) {
            this.version = version;
            this.gitTag = gitTag;
            this.status = status;
        }

        public String getVersion() {
            return version;
        }

        public String getGitTag() {
            return gitTag;
        }

        public String getStatus() {
            return status;
        }
    }

    /** 发布状态(status / currentVersion / publishTime)。 */
    public static class PublishStatus {
        private final String status;
        private final String currentVersion;
        private final Date publishTime;

        public PublishStatus(String status, String currentVersion, Date publishTime) {
            this.status = status;
            this.currentVersion = currentVersion;
            this.publishTime = publishTime;
        }

        public String getStatus() {
            return status;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public Date getPublishTime() {
            return publishTime;
        }
    }
}
