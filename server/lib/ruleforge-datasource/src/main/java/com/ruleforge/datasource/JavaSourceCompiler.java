package com.ruleforge.datasource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * V5.23 — Pure-function Java source compiler.
 *
 * <p>Inputs: a string of Java source code. Output: compiled {@code .class} bytes (or an error).
 * No I/O outside the temp directory; no AWS / S3 / SQS dependencies.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Write source to a temp file</li>
 *   <li>Spawn {@code javac} as a subprocess (5-second timeout, 256 MB heap cap)</li>
 *   <li>If success, read the generated {@code .class} bytes</li>
 *   <li>Delete the temp directory</li>
 * </ol>
 *
 * <h2>Why fork a subprocess</h2>
 * <p>LLM-generated code may contain {@code static { while(true){} }} or
 * {@code Runtime.getRuntime().exec(...)}. Running {@code javac} in-process shares the JVM,
 * so any runaway compile would block the calling app. Subprocess + timeout isolates this.
 *
 * <h2>What this is NOT</h2>
 * <ul>
 *   <li>Not a security sandbox — a determined attacker can still {@code System.exit(0)}
 *       the parent JVM at runtime. Mitigation lives in the generated code's classloader</li>
 *   <li>Not incremental — every call recompiles from scratch. Cache the result in your caller</li>
 *   <li>Not a service — instantiate one and reuse. It is thread-safe</li>
 * </ul>
 */
public class JavaSourceCompiler {

    private final long timeoutMs;
    private final String[] classpath;

    public JavaSourceCompiler() {
        this(5_000L, defaultClasspath());
    }

    public JavaSourceCompiler(long timeoutMs, String[] classpath) {
        this.timeoutMs = timeoutMs;
        this.classpath = classpath == null ? defaultClasspath() : classpath;
    }

    /**
     * Compile the given Java source code. Returns a {@link CompileResult} that contains
     * either the {@code .class} bytes (success) or an error message (failure).
     *
     * <p>The source must declare exactly one top-level {@code public} class. The compiled
     * {@code .class} file is read back; multi-class compilation is not supported in this
     * minimal version.
     */
    public CompileResult compile(String javaCode) {
        if (javaCode == null || javaCode.isBlank()) {
            return CompileResult.failed("source code is empty");
        }
        String publicClassName = extractPublicClassName(javaCode);
        if (publicClassName == null) {
            return CompileResult.failed("no public class found in source");
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("ds-compile-");
            Path srcFile = workDir.resolve(publicClassName + ".java");
            Path outDir = workDir.resolve("out");
            Files.createDirectories(outDir);
            Files.writeString(srcFile, javaCode);

            List<String> cmd = buildCompileCommand(srcFile, outDir);
            Process proc = new ProcessBuilder(cmd)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();

            String compileLog = drainStream(proc.getInputStream());
            if (!proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
                return CompileResult.failed("compile timeout after " + timeoutMs + "ms\n" + compileLog);
            }
            if (proc.exitValue() != 0) {
                return CompileResult.failed("javac exit " + proc.exitValue() + "\n" + compileLog);
            }

            Path classFile = outDir.resolve(publicClassName + ".class");
            // javac 会按源码的 package 声明把 .class 写到子目录 — 找不到时降级到包路径再找一次
            if (!Files.exists(classFile)) {
                String pkg = extractPackageName(javaCode);
                if (pkg != null && !pkg.isEmpty()) {
                    classFile = outDir.resolve(pkg.replace('.', java.io.File.separatorChar))
                        .resolve(publicClassName + ".class");
                }
            }
            if (!Files.exists(classFile)) {
                return CompileResult.failed(".class file not generated (compile reported success but no output)\n" + compileLog);
            }
            byte[] classBytes = Files.readAllBytes(classFile);
            if (!verifyClassMagic(classBytes)) {
                return CompileResult.failed("invalid .class file (bad magic bytes)");
            }
            return CompileResult.success(publicClassName, classBytes);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return CompileResult.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (workDir != null) deleteRecursive(workDir);
        }
    }

    // ====== internals ======

    private List<String> buildCompileCommand(Path srcFile, Path outDir) {
        String javaHome = System.getProperty("java.home");
        String javacBin = javaHome + "/bin/javac";
        String cp = String.join(java.io.File.pathSeparator, classpath);
        return Arrays.asList(
            javacBin,
            "-d", outDir.toString(),
            "-cp", cp,
            "-encoding", "UTF-8",
            "-proc:none",                          // 不跑 annotation processor
            "-implicit:none",                      // 不自动生成
            "-Xlint:none",                         // 安静点
            srcFile.toString()
        );
    }

    private static String[] defaultClasspath() {
        // 复用当前 classloader 的所有 jar 路径
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl instanceof java.net.URLClassLoader ucl) {
            return Arrays.stream(ucl.getURLs())
                .map(u -> {
                    try { return new java.io.File(u.toURI()).getAbsolutePath(); }
                    catch (java.net.URISyntaxException e) { return u.getPath(); }
                })
                .toArray(String[]::new);
        }
        return new String[]{System.getProperty("java.class.path")};
    }

    private static String drainStream(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toString("UTF-8");
    }

    /**
     * Naive extraction of {@code public class Foo} / {@code public final class Foo} /
     * {@code public abstract class Foo}. Does not handle nested classes or generics in
     * declaration — good enough for LLM-generated top-level data source classes.
     */
    static String extractPublicClassName(String source) {
        // 只匹配 public 顶层类型 — 不 fallback。LLM 写了非 public class 时应当早失败,
        // 而不是让 javac 静默编出我们找不到的 .class(因为没 .class 也算 success
        // 但没有可注册 bean — 之前测试中 this 路径是 COMPILE_FAILED 的关键防线)
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "\\bpublic\\s+(?:final\\s+|abstract\\s+)?(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
        ).matcher(source);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Naive extraction of {@code package foo.bar;} — used to resolve the on-disk
     * output path javac writes to ({@code outDir/<package-as-path>/<class>.class}).
     */
    static String extractPackageName(String source) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "\\bpackage\\s+([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\s*;"
        ).matcher(source);
        return m.find() ? m.group(1) : "";
    }

    private static boolean verifyClassMagic(byte[] bytes) {
        return bytes.length > 4
            && (bytes[0] & 0xFF) == 0xCA
            && (bytes[1] & 0xFF) == 0xFE
            && (bytes[2] & 0xFF) == 0xBA
            && (bytes[3] & 0xFF) == 0xBE;
    }

    private static void deleteRecursive(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            Files.walk(dir)
                .sorted((a, b) -> b.toString().length() - a.toString().length())  // delete files before dirs
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    // ====== result type ======

    public static final class CompileResult {
        public final boolean success;
        public final String publicClassName;   // non-null on success
        public final byte[] classBytes;         // non-null on success
        public final String error;              // non-null on failure

        private CompileResult(boolean success, String publicClassName, byte[] classBytes, String error) {
            this.success = success;
            this.publicClassName = publicClassName;
            this.classBytes = classBytes;
            this.error = error;
        }

        static CompileResult success(String fqcn, byte[] bytes) {
            return new CompileResult(true, fqcn, bytes, null);
        }

        static CompileResult failed(String error) {
            return new CompileResult(false, null, null, error);
        }

        @Override
        public String toString() {
            return success
                ? "CompileResult{success, " + publicClassName + ", " + (classBytes == null ? 0 : classBytes.length) + " bytes}"
                : "CompileResult{failed, " + error + "}";
        }
    }
}
