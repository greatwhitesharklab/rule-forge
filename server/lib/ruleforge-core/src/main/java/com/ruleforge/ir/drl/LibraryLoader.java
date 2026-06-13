package com.ruleforge.ir.drl;

import java.util.Map;

/**
 * V5.45.2 — Library 加载 SPI。
 *
 * <p>KnowledgeBuilder 在 parse .drl 顶层 {@code import} 段时,对每条 library 路径调
 * {@link #loadLibrary(String, String)} 拉取 library 文件内容(parse 成 DRL 4
 * declare 段),把 declare 出来的 type 列表注册进 {@link DatatypeResolver}。
 *
 * <p><b>两 impl</b>(跨 app 一致性):
 * <ul>
 *   <li>{@code LocalLibraryLoader}(console-app)— 从本地 repository filesystem 读 .drl
 *   <li>{@code RemoteLibraryLoader}(executor-app)— 调 console 端 {@code /fileSource}
 *       REST 端点拉 .drl
 * </ul>
 *
 * <p><b>无 loader 行为</b>:若 KnowledgeBuilder 没注入 loader,import 段被忽略 —
 * 跟 V5.44.3 行为一致(import 列表收集进 DatatypeResolver 但不实际加载),所以
 * 老的 unit test / 集成测试不挂。
 *
 * <p><b>递归 + 环检测</b>:loader **不**负责递归 import(单层拉一次)。
 * KnowledgeBuilder 拿到第一层 declare types 后,LibraryParser 会自己 parse library
 * 文件内容,提取**该 library 内部**的 import 段(由 KnowledgeBuilder BFS
 * 加载,V5.45.2 LibraryParseResult.innerImports API 暴露)。
 *
 * <p><b>错误策略</b>:loader 拉文件失败(网络超时 / 文件不存在)返空 map,KnowledgeBuilder
 * 走 fallback(import 段保留在 resolver 列表,V5.44.3 行为)— 不抛错,不中断 build。
 * 失败 case 留给 caller 自己 log warn。
 *
 * @since 5.45
 */
public interface LibraryLoader {

    /**
     * 拉一个 library 文件,parse 出 declare 段 type 列表。
     *
     * @param libraryPath .drl 顶层 import 段里的路径(双引号包住,V5.44.3 形式)
     * @param basePath 触发加载的 .drl 文件所在目录(相对路径解析的基准;
     *                 V5.45.2 简单实现:只支持绝对路径 / 同 basePath 相对路径,跨
     *                 project 不支持)
     * @return library 内的 declare 段 TypeInfo map(key=type name, value=TypeInfo);
     *         加载失败 / 文件不存在返空 map(不是 null,调用方可以无脑 iterate)
     */
    Map<String, DatatypeResolver.TypeInfo> loadLibrary(String libraryPath, String basePath);
}
