package com.ruleforge.console.storage;

import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;

import java.io.InputStream;
import java.util.List;

/**
 * 项目存储服务接口，抽象底层存储实现（数据库、文件系统、对象存储等）
 */
public interface ProjectStorageService {

    /**
     * 检查项目或文件路径是否存在
     *
     * @param path 路径 (例如 /projectName 或 /projectName/fileName.rule)
     * @return 是否存在
     * @throws Exception 存储异常
     */
    boolean exists(String path) throws Exception;

    /**
     * 创建项目版本
     *
     * @return 创建后的项目文件对象
     * @throws Exception 存储异常或项目已存在
     */
    String createProjectVersion(String projectName, String projectVersion, String createUser, String comment, Integer status) throws Exception;

    String createProjectPackageVersion(String projectName, String packageId, String packageVersion, String createUser, String comment, Integer status) throws Exception;

    /**
     * 创建目录
     *
     * @param path 目录路径
     * @param user 创建用户
     * @throws Exception 存储异常
     */
    void createDirectory(String path, User user) throws Exception;

    /**
     * 创建或更新文件（带版本控制）
     *
     * @param path           文件路径
     * @param content        文件内容
     * @param user           操作用户
     * @param newVersion     是否创建新版本 (如果存储层支持文件级版本)
     * @param versionComment 版本注释 (如果存储层支持文件级版本)
     * @return 创建或更新后的版本号/标识符
     * @throws Exception 存储异常
     */
    String saveFile(String path, String content, User user, boolean newVersion, String versionComment) throws
            Exception;

    /**
     * 读取文件内容
     *
     * @param path    文件路径
     * @param version 版本号 (可选, null 或 "latest" 表示最新)
     * @return 文件内容的输入流
     * @throws Exception 文件未找到或存储异常
     */
    InputStream readFile(String path, String version) throws Exception;

    /**
     * 删除文件或目录
     *
     * @param path 路径
     * @param user 操作用户
     * @throws Exception 存储异常
     */
    void delete(String path, User user) throws Exception;

    /**
     * 加载仓库/项目结构
     *
     * @param project        项目名称 (可选, null 表示加载所有)
     * @param user           用户
     * @param classify       是否分类 (可能需要调整)
     * @param types          文件类型过滤器 (可选)
     * @param searchFileName 文件名搜索关键字 (可选)
     * @return 仓库对象
     * @throws Exception 存储异常
     */
    Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName) throws Exception;

    /**
     * 加载项目的资源包定义
     *
     * @param project 项目名称 (可能包含版本, 如 projectName:1.0.0)
     * @return 资源包列表
     * @throws Exception 存储异常
     */
    List<ResourcePackage> loadProjectResourcePackages(String project) throws Exception;

    /**
     * 获取项目的所有版本列表
     *
     * @param projectName 项目名称
     * @return 版本号列表
     * @throws Exception 存储异常
     */
    List<String> listProjectVersions(String projectName) throws Exception;

    /**
     * 检出（获取）指定项目版本的只读视图或数据
     * 这个方法的具体实现会根据存储方式差异很大
     * 可能返回一个包含该版本所有文件内容的 Map，或者一个指向只读存储位置的引用
     *
     * @param projectName 项目名称
     * @param version     项目版本号
     * @return 特定项目版本的数据表示 (具体类型待定)
     * @throws Exception 版本未找到或存储异常
     */
    Object checkoutProjectVersion(String projectName, String version) throws Exception;

    // --- 并发控制 (可选，可以在存储层实现或在服务层实现) ---
    // Long lockProject(String projectName, User user) throws Exception;
    // boolean unlockProject(String projectName, User user, Long lockId) throws Exception;

}