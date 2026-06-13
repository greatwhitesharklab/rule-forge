package com.ruleforge.console.app.drl;

import com.ruleforge.ir.drl.DatatypeResolver;
import com.ruleforge.ir.drl.LibraryLoader;
import com.ruleforge.ir.drl.LibraryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

/**
 * V5.45.2 — console-app 本地 library 加载器。
 *
 * <p>从 console-app 进程本地文件系统读 library .drl 文件(parse → declare types)。
 * basePath 是触发加载的主 .drl 文件目录,libraryPath 是相对路径(同 basePath 解析)或
 * 绝对路径。
 *
 * <p>console-app 跑在 web server 进程里,project 资源在 server 工作目录或配置目录;
 * 具体 basePath 解析策略由 console-app 决定(本类只负责"读 + parse",不绑定 project
 * repository 抽象 — console-app 调用方负责传 basePath)。
 *
 * <p>失败策略:文件不存在 / IO 错 → 返空 map + log warn,不抛错(跟 V5.45.2 LibraryLoader
 * SPI 契约一致)。
 *
 * @since 5.45
 */
public class LocalLibraryLoader implements LibraryLoader {

    private static final Logger log = LoggerFactory.getLogger(LocalLibraryLoader.class);
    private final LibraryParser parser = new LibraryParser();

    @Override
    public Map<String, DatatypeResolver.TypeInfo> loadLibrary(String libraryPath, String basePath) {
        Path resolved = resolve(libraryPath, basePath);
        if (resolved == null) {
            log.warn("V5.45.2 LocalLibraryLoader: cannot resolve library path '{}' (basePath='{}')",
                libraryPath, basePath);
            return Collections.emptyMap();
        }
        if (!Files.exists(resolved)) {
            log.warn("V5.45.2 LocalLibraryLoader: library file not found: {}", resolved);
            return Collections.emptyMap();
        }
        try {
            String drlText = Files.readString(resolved, StandardCharsets.UTF_8);
            LibraryParser.LibraryParseResult r = parser.parseLibraryDrl(drlText);
            log.info("V5.45.2 LocalLibraryLoader: loaded library {} ({} types, {} inner imports)",
                resolved, r.types().size(), r.innerImports().size());
            return r.types();
        } catch (IOException e) {
            log.warn("V5.45.2 LocalLibraryLoader: failed to read library {}: {}",
                resolved, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * V5.45.2 路径解析:绝对路径直接用,相对路径相对 basePath(若 basePath 是 null
     * 或空,返 null 让 caller 走 fallback)。
     */
    private Path resolve(String libraryPath, String basePath) {
        Path lib = Paths.get(libraryPath);
        if (lib.isAbsolute()) {
            return lib;
        }
        if (basePath == null || basePath.isEmpty()) {
            return null;
        }
        return Paths.get(basePath).resolve(libraryPath).normalize();
    }
}
