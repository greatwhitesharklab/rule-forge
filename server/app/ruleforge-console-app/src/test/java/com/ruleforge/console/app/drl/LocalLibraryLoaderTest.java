package com.ruleforge.console.app.drl;

import com.ruleforge.ir.drl.DatatypeResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.45.2 — LocalLibraryLoader BDD。
 *
 * <p>锁 3 件事:
 * <ol>
 *   <li>相对路径(basePath + libraryPath)解析正确,parse 后 types 含 library declare 段</li>
 *   <li>绝对路径独立 basePath 解析(parse 同样 work)</li>
 *   <li>文件不存在 / IO 错返空 map + log warn,不抛错</li>
 * </ol>
 */
@DisplayName("V5.45.2 — LocalLibraryLoader BDD")
class LocalLibraryLoaderTest {

    @Nested
    @DisplayName("Given basePath + 相对 library 路径,When loadLibrary,Then 解析 + parse")
    class RelativePath {

        @Test
        @DisplayName("相对路径 libs/x.drl + basePath /tmp → /tmp/libs/x.drl 解析 + parse types")
        void relative(@TempDir Path tmp) throws IOException {
            Path libDir = Files.createDirectories(tmp.resolve("libs"));
            Files.writeString(libDir.resolve("x.drl"),
                "declare Applicant\n    age : int\nend\n");

            LocalLibraryLoader loader = new LocalLibraryLoader();
            Map<String, DatatypeResolver.TypeInfo> types = loader.loadLibrary("libs/x.drl", tmp.toString());

            assertEquals(1, types.size());
            assertNotNull(types.get("Applicant"));
            assertEquals(List.of("age"), types.get("Applicant").getFields());
        }
    }

    @Nested
    @DisplayName("Given 绝对 library 路径,When loadLibrary,Then 独立 basePath 解析")
    class AbsolutePath {

        @Test
        @DisplayName("绝对路径 /tmp/libs/y.drl + basePath /other → 直接读 /tmp/libs/y.drl")
        void absolute(@TempDir Path tmp) throws IOException {
            Path libDir = Files.createDirectories(tmp.resolve("libs"));
            Files.writeString(libDir.resolve("y.drl"),
                "declare Person\n    name : String\nend\n");

            LocalLibraryLoader loader = new LocalLibraryLoader();
            Path absoluteLib = libDir.resolve("y.drl");
            Map<String, DatatypeResolver.TypeInfo> types = loader.loadLibrary(absoluteLib.toString(), "/other");

            assertEquals(1, types.size());
            assertNotNull(types.get("Person"));
        }
    }

    @Nested
    @DisplayName("Given library 文件不存在 / IO 错,When loadLibrary,Then 返空 map 不抛错")
    class MissingFile {

        @Test
        @DisplayName("library 路径指向不存在的文件 → 返空 map + log warn")
        void missing(@TempDir Path tmp) {
            LocalLibraryLoader loader = new LocalLibraryLoader();
            Map<String, DatatypeResolver.TypeInfo> types = loader.loadLibrary("libs/missing.drl", tmp.toString());
            assertTrue(types.isEmpty());
        }
    }
}
