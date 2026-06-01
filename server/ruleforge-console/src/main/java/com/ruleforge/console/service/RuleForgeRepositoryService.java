package com.ruleforge.console.service;

import com.ruleforge.console.repository.RepositoryReader;
import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.repository.model.Type;
import com.ruleforge.console.repository.model.VersionFile;
import com.ruleforge.console.servlet.common.RefFile;
import com.ruleforge.console.servlet.frame.ExportProject;
import com.ruleforge.console.model.PackageConfig;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.storage.model.FileDiff;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Jacky.gao
 * @author Fred
 * 2015年3月24日
 */
public interface RuleForgeRepositoryService extends RepositoryReader {

    boolean fileExistCheck(String filePath) throws Exception;

    RepositoryFile createProject(String projectName, User user, boolean classify) throws Exception;

    void createDir(String path, User user) throws Exception;

    void createFile(String path, String content, User user) throws Exception;

    String saveFile(String path, String content, boolean newVersion, String versionComment, User user) throws Exception;

    String saveFile(String path, String content, boolean newVersion, String versionComment, String beforeComment, String afterComment, User user) throws Exception;

    String saveFile(String path, String content, boolean newVersion, String versionComment, String beforeComment, String afterComment, User user, Date createTime) throws Exception;

    List<RefFile> getFlowRefs(List<String> pathList);

    String getPackageVersionDiff(String project, String version);

    String getFileVersionDiff(String filePath, String targetVersion);

    void deleteFile(String path, User user) throws Exception;

    void deleteFile(String path, User user, Type type) throws Exception;

    void deleteProject(String projectName, User user) throws Exception;

    Long lockPath(String project, User user) throws Exception;

    boolean unlockPath(String project, User user, Long versionNum) throws Exception;

    Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName) throws Exception;

    Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName, Boolean detailed) throws Exception;

    void fileRename(String path, String newPath) throws Exception;

    List<String> getReferenceFiles(String targetProject, String path, String searchText, String searchTextScript) throws Exception;

    InputStream readFile(String path, String version) throws Exception;

    InputStream readFile(String path, String version, String projectVersion) throws Exception;

    InputStream readFile(String path, String version, String projectVersion, boolean containSnapshot) throws Exception;

    VersionFile loadFileProperty(String path, String version) throws Exception;

    List<VersionFile> getVersionFiles(String path) throws Exception;

    List<VersionFile> getVersionFiles(String path, boolean desc, int page, int row, boolean containContent, boolean containLatest) throws Exception;

    Long countVersionFiles(String path) throws Exception;

    Long importFromZip(User user, MultipartFile importFile, RepositoryFile repositoryFile, Map<String, ExportProject> exportProjectMap, Boolean loadLatest) throws Exception;

    PackageConfig loadPackageConfigs(String project) throws Exception;

    void updatePackageConfigs(String project, PackageConfig packageConfig) throws Exception;

    boolean fileExist(String var1) throws Exception;

    /**
     * 获取指定项目的所有版本信息
     *
     * @param projectName 项目名称
     * @param desc        是否降序排序
     * @param page        页码
     * @param row         每页数量
     * @return 版本文件列表
     * @throws Exception 异常
     */
    List<VersionFile> getProjectVersions(String projectName, boolean desc, int page, int row) throws Exception;

    List<VersionFile> getProjectPackageVersions(String projectName, String packageId) throws Exception;

    /**
     * 创建指定项目的一个新版本（快照）。
     * 通常在发布测试或生产时调用。
     *
     * @param projectName 要创建版本的项目名称。
     * @param user        执行操作的用户。
     * @param comment     版本的注释信息。
     * @return 返回新创建的项目版本号或标识符。
     * @throws Exception 创建过程中可能发生的异常。
     */
    String createProjectVersion(String projectName, User user, String comment) throws Exception;

    String createProjectVersion(String projectName, String packageId, String projectVersion, User user, String comment, Integer status) throws Exception;

    /**
     * Get structured diff between two package versions, returning per-file diff information.
     *
     * @param project     project name
     * @param fromVersion the source version tag
     * @param toVersion   the target version tag
     * @return list of file diffs with structured change details
     * @throws Exception if diff computation fails
     */
    List<FileDiff> getPackageVersionDiffStructured(String project, String fromVersion, String toVersion) throws Exception;

    /**
     * Get structured diff for a single file between two versions.
     *
     * @param filePath    file path relative to repo root
     * @param fromVersion the source version tag
     * @param toVersion   the target version tag
     * @return file diff with structured change details, or null if not found
     * @throws Exception if diff computation fails
     */
    FileDiff getFileVersionDiffStructured(String filePath, String fromVersion, String toVersion) throws Exception;
}
