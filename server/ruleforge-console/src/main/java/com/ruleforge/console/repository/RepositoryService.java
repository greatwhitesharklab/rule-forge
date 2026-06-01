package com.ruleforge.console.repository;

import com.ruleforge.console.model.ClientConfig;
import com.ruleforge.console.model.PackageConfig;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.servlet.permission.UserPermission;

import java.io.InputStream;
import java.util.Date;
import java.util.List;

public interface RepositoryService extends RepositoryReader {

    // --- Core file operations ---

    InputStream readFile(String path, String version) throws Exception;

    boolean fileExistCheck(String filePath) throws Exception;

    RepositoryFile createProject(String projectName, User user, boolean classify) throws Exception;

    void createDir(String path, User user) throws Exception;

    void createFile(String path, String content, User user) throws Exception;

    String saveFile(String path, String content, boolean newVersion, String versionComment, User user) throws Exception;

    String saveFile(String path, String content, boolean newVersion, String versionComment, String beforeComment, String afterComment, User user) throws Exception;

    String saveFile(String path, String content, boolean newVersion, String versionComment, String beforeComment, String afterComment, User user, Date createTime) throws Exception;

    void deleteFile(String path, User user) throws Exception;

    void fileRename(String path, String newPath) throws Exception;

    boolean fileExist(String path) throws Exception;

    String getProject(String path);

    // --- Repository operations ---

    Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName) throws Exception;

    // --- Package operations ---

    PackageConfig loadPackageConfigs(String project) throws Exception;

    void updatePackageConfigs(String project, PackageConfig packageConfig) throws Exception;

    // --- Client/Security configs ---

    List<ClientConfig> loadClientConfigs(String project) throws Exception;

    List<UserPermission> loadResourceSecurityConfigs(String companyId) throws Exception;

    // --- Project listing ---

    List<String> loadProjectNames() throws Exception;

    // --- Template operations ---

    List<RepositoryFile> loadTemplates(String project) throws Exception;

    String saveTemplateFile(String path, String content) throws Exception;
}
