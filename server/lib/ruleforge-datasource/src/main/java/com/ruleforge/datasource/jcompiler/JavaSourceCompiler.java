package com.ruleforge.datasource.jcompiler;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V5.23 — Compiles a single public Java source to a single public .class byte array.
 *
 * <p>Uses the JDK's built-in {@code javax.tools.JavaCompiler} via
 * {@link ProcessBuilder} forking javac in a child JVM. This isolates compilation
 * failures (bad sources, infinite recursion, OOM) from the host JVM.
 *
 * <p>Hard 5s timeout — bad source that hangs the compiler gets killed.
 *
 * <p>Output bytes include the public class only (no inner/anonymous classes
 * in scope). Caller is expected to provide exactly one public top-level class.
 *
 * <p>Magic bytes 0xCAFEBABE are returned as-is — caller can verify them.
 */
@Slf4j
public class JavaSourceCompiler {

    /** Hard cap on how long a single compile can run. */
    public static final long COMPILE_TIMEOUT_SECONDS = 5L;

    /** Max compiled .class size — defense against DoS / runaway. */
    public static final int MAX_CLASS_BYTES = 256 * 1024; // 256 KB

    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile(
        "\\bpublic\\s+(?:final\\s+|abstract\\s+)?(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
    );

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
        "\\bpackage\\s+([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\s*;"
    );

    private final long timeoutSeconds;
    private final int maxClassBytes;

    public JavaSourceCompiler() {
        this(COMPILE_TIMEOUT_SECONDS, MAX_CLASS_BYTES);
    }

    public JavaSourceCompiler(long timeoutSeconds, int maxClassBytes) {
        this.timeoutSeconds = timeoutSeconds;
        this.maxClassBytes = maxClassBytes;
    }

    public CompileResult compile(String javaSource) {
        if (javaSource == null || javaSource.isBlank()) {
            return CompileResult.failure("source is empty");
        }

        String publicClassName = extractPublicClassName(javaSource);
        if (publicClassName == null) {
            return CompileResult.failure("no public top-level class found");
        }

        String packageName = extractPackageName(javaSource);
        String fqcn = packageName.isEmpty() ? publicClassName : packageName + "." + publicClassName;

        Path workDir;
        try {
            workDir = Files.createTempDirectory("ruleforge-compile-");
        } catch (IOException e) {
            return CompileResult.failure("cannot create temp dir: " + e.getMessage());
        }

        // javac enforces: public class name == filename. We name the file
        // after the public class so user-supplied names just work.
        Path sourceFile = workDir.resolve(publicClassName + ".java");
        Path outDir = workDir.resolve("out");
        try {
            Files.createDirectories(outDir);
            Files.writeString(sourceFile, javaSource, StandardCharsets.UTF_8);
        } catch (IOException e) {
            cleanup(workDir);
            return CompileResult.failure("cannot write source: " + e.getMessage());
        }

        // javac with -d <outDir> writes .class to <outDir>/<package-as-path>/<class>.class
        // -cp sets the boot classpath to include this lib (so user code can resolve IJavaDataSource)
        ProcessBuilder pb = new ProcessBuilder(
            locateJavac(),
            "-d", outDir.toAbsolutePath().toString(),
            "-encoding", "UTF-8",
            "-cp", currentCompileClasspath(),
            sourceFile.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            cleanup(workDir);
            return CompileResult.failure("cannot start javac: " + e.getMessage());
        }

        String output;
        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                cleanup(workDir);
                return CompileResult.failure("compile timeout after " + timeoutSeconds + "s");
            }
            output = readAll(process.getInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            cleanup(workDir);
            return CompileResult.failure("compile interrupted");
        } catch (IOException e) {
            process.destroyForcibly();
            cleanup(workDir);
            return CompileResult.failure("cannot read javac output: " + e.getMessage());
        }

        if (process.exitValue() != 0) {
            cleanup(workDir);
            return CompileResult.failure("javac exit " + process.exitValue() + ": " + truncate(output, 2000));
        }

        // javac wrote to <outDir>/<pkg>/<class>.class; if no package, it's flat
        Path classFile = outDir.resolve(publicClassName + ".class");
        if (!Files.exists(classFile) && !packageName.isEmpty()) {
            classFile = outDir.resolve(packageName.replace('.', File.separatorChar))
                .resolve(publicClassName + ".class");
        }
        if (!Files.exists(classFile)) {
            cleanup(workDir);
            return CompileResult.failure("compiled .class not found at " + classFile);
        }

        byte[] classBytes;
        try {
            classBytes = Files.readAllBytes(classFile);
        } catch (IOException e) {
            cleanup(workDir);
            return CompileResult.failure("cannot read compiled .class: " + e.getMessage());
        } finally {
            cleanup(workDir);
        }

        if (classBytes.length == 0) {
            return CompileResult.failure("compiled .class is empty");
        }
        if (classBytes.length > maxClassBytes) {
            return CompileResult.failure("compiled .class size " + classBytes.length
                + " exceeds cap " + maxClassBytes);
        }
        if (!isValidMagicBytes(classBytes)) {
            return CompileResult.failure("compiled bytes do not start with 0xCAFEBABE");
        }

        return CompileResult.success(fqcn, publicClassName, classBytes);
    }

    /** Extracts the single public top-level class name from Java source. */
    static String extractPublicClassName(String source) {
        if (source == null) return null;
        Matcher m = PUBLIC_CLASS_PATTERN.matcher(source);
        return m.find() ? m.group(1) : null;
    }

    /** Extracts the {@code package xyz.foo.bar;} declaration, or empty string. */
    static String extractPackageName(String source) {
        if (source == null) return "";
        Matcher m = PACKAGE_PATTERN.matcher(source);
        return m.find() ? m.group(1) : "";
    }

    private static boolean isValidMagicBytes(byte[] bytes) {
        return bytes.length >= 4
            && (bytes[0] & 0xFF) == 0xCA
            && (bytes[1] & 0xFF) == 0xFE
            && (bytes[2] & 0xFF) == 0xBA
            && (bytes[3] & 0xFF) == 0xBE;
    }

    private static String locateJavac() {
        // Prefer JAVA_HOME/bin/javac (the toolchain matches the running JVM)
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            File javac = new File(javaHome, "bin/javac");
            if (javac.exists() && javac.canExecute()) {
                return javac.getAbsolutePath();
            }
        }
        // Fall back to PATH-resolved javac
        return "javac";
    }

    /**
     * Builds the javac -cp value. The LLM-generated class needs to resolve
     * IJavaDataSource (in this lib) + JDK types + anything else on the host's
     * classpath. We pass the entire system classpath so user code is unconstrained
     * beyond what the host JVM has.
     */
    private static String currentCompileClasspath() {
        StringBuilder sb = new StringBuilder();
        // Start with java.class.path (host JVM's classpath)
        String javaCp = System.getProperty("java.class.path");
        if (javaCp != null && !javaCp.isEmpty()) {
            sb.append(javaCp);
        }
        // Add URLs from system loader if URLClassLoader
        ClassLoader system = ClassLoader.getSystemClassLoader();
        if (system instanceof java.net.URLClassLoader ucl) {
            for (var url : ucl.getURLs()) {
                if ("file".equals(url.getProtocol())) {
                    if (sb.length() > 0) sb.append(java.io.File.pathSeparatorChar);
                    sb.append(url.getPath());
                }
            }
        }
        return sb.toString();
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try {
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static void cleanup(Path dir) {
        if (dir == null) return;
        try {
            if (Files.exists(dir)) {
                // recursive delete via walk
                try (var stream = Files.walk(dir)) {
                    stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
                }
            }
        } catch (IOException e) {
            log.debug("cleanup failed: {}", e.getMessage());
        }
    }

    /** Outcome of {@link #compile(String)}. */
    public static final class CompileResult {
        public final boolean success;
        public final String fqcn;
        public final String publicClassName;
        public final byte[] classBytes;
        public final String error;

        private CompileResult(boolean success, String fqcn, String publicClassName,
                              byte[] classBytes, String error) {
            this.success = success;
            this.fqcn = fqcn;
            this.publicClassName = publicClassName;
            this.classBytes = classBytes;
            this.error = error;
        }

        static CompileResult success(String fqcn, String publicClassName, byte[] bytes) {
            return new CompileResult(true, fqcn, publicClassName, bytes, null);
        }

        static CompileResult failure(String error) {
            return new CompileResult(false, null, null, null, error);
        }
    }
}
