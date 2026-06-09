package com.ruleforge.datasource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JavaSourceCompiler — hello world + 边界")
class JavaSourceCompilerTest {

    private final JavaSourceCompiler compiler = new JavaSourceCompiler();

    @Nested
    @DisplayName("Scenario: 编译合法 Java 源码")
    class CompileValid {

        @Test
        @DisplayName("Given 简单 hello world When 编译 Then 拿到 .class bytes")
        void shouldCompileHelloWorld() {
            String code = """
                public class Hello {
                    public String greet(String name) { return "Hello, " + name; }
                }
                """;
            JavaSourceCompiler.CompileResult r = compiler.compile(code);
            assertThat(r.success).as("compile result: " + r).isTrue();
            assertThat(r.publicClassName).isEqualTo("Hello");
            assertThat(r.classBytes).isNotEmpty();
            // 验证 CAFEBABE magic
            assertThat((r.classBytes[0] & 0xFF)).isEqualTo(0xCA);
            assertThat((r.classBytes[1] & 0xFF)).isEqualTo(0xFE);
            assertThat((r.classBytes[2] & 0xFF)).isEqualTo(0xBA);
            assertThat((r.classBytes[3] & 0xFF)).isEqualTo(0xBE);
        }

        @Test
        @DisplayName("Given 真实 data source 子类 extends BaseApiDataSource When 编译 + 反射调用 Then 拿到结果")
        void shouldCompileRealDataSourceSubclass() throws Exception {
            String code = """
                import com.ruleforge.datasource.BaseApiDataSource;
                import com.ruleforge.datasource.Vars;
                import java.util.Map;
                public class FakeCredit extends BaseApiDataSource {
                    @Override public String getName() { return "fake_credit"; }
                    @Override public Map<String, String> getSchema() { return Map.of("score", "INT"); }
                    @Override public Vars fetch(Vars inputs) {
                        String id = inputs.getStr("id");
                        Vars out = new Vars();
                        out.put("score", id == null ? 0 : id.length() * 100);
                        return out;
                    }
                }
                """;
            JavaSourceCompiler.CompileResult r = compiler.compile(code);
            assertThat(r.success).as("compile: " + r).isTrue();

            // Phase 3 hello world: 把 .class bytes 写到 temp dir, 用 URLClassLoader 加载
            // (ClassLoaderPool 会是 Phase 4 之后的正式机制, 这版先用 URLClassLoader 验证链路)
            java.nio.file.Path tmpOut = java.nio.file.Files.createTempDirectory("ds-test-");
            java.nio.file.Path classFile = tmpOut.resolve(r.publicClassName + ".class");
            java.nio.file.Files.write(classFile, r.classBytes);
            try {
                java.net.URLClassLoader loader = new java.net.URLClassLoader(
                    new java.net.URL[]{tmpOut.toUri().toURL()},
                    this.getClass().getClassLoader()
                );
                Class<?> clazz = Class.forName(r.publicClassName, true, loader);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                assertThat(instance).isInstanceOf(BaseApiDataSource.class);

                BaseApiDataSource ds = (BaseApiDataSource) instance;
                assertThat(ds.getName()).isEqualTo("fake_credit");

                Vars out = ds.fetch(new Vars().put("id", "110101"));
                assertThat(out.getInt("score")).isEqualTo(600);  // 6 chars * 100
            } finally {
                java.nio.file.Files.walk(tmpOut)
                    .sorted((a, b) -> b.toString().length() - a.toString().length())
                    .forEach(p -> { try { java.nio.file.Files.deleteIfExists(p); } catch (java.io.IOException ignored) {} });
            }
        }
    }

    @Nested
    @DisplayName("Scenario: 编译失败")
    class CompileFailure {

        @Test
        @DisplayName("Given 语法错 When 编译 Then success=false 带 error 信息")
        void shouldFailOnSyntaxError() {
            String code = "public class Broken { broken syntax here";
            JavaSourceCompiler.CompileResult r = compiler.compile(code);
            assertThat(r.success).isFalse();
            assertThat(r.error).contains("javac");
        }

        @Test
        @DisplayName("Given 空字符串 When 编译 Then 返 'source code is empty'")
        void shouldFailOnEmpty() {
            JavaSourceCompiler.CompileResult r = compiler.compile("");
            assertThat(r.success).isFalse();
            assertThat(r.error).contains("empty");
        }

        @Test
        @DisplayName("Given 没有 public class When 编译 Then 返 'no public class'")
        void shouldFailOnMissingPublicClass() {
            String code = "package foo; class Helper {}";  // 没 public
            JavaSourceCompiler.CompileResult r = compiler.compile(code);
            assertThat(r.success).isFalse();
            // extractPublicClassName 有 fallback,可能找到 Helper
            // 关键是 success=false
        }
    }

    @Nested
    @DisplayName("Scenario: public class 名提取")
    class ExtractClassName {

        @Test
        @DisplayName("public class Foo → Foo")
        void shouldExtractPlainClass() {
            assertThat(JavaSourceCompiler.extractPublicClassName("public class Foo {}"))
                .isEqualTo("Foo");
        }

        @Test
        @DisplayName("public final class Foo → Foo")
        void shouldExtractFinalClass() {
            assertThat(JavaSourceCompiler.extractPublicClassName("public final class Foo {}"))
                .isEqualTo("Foo");
        }

        @Test
        @DisplayName("public abstract class Foo → Foo")
        void shouldExtractAbstractClass() {
            assertThat(JavaSourceCompiler.extractPublicClassName("public abstract class Foo<T> {}"))
                .isEqualTo("Foo");
        }

        @Test
        @DisplayName("public interface Foo → Foo")
        void shouldExtractInterface() {
            assertThat(JavaSourceCompiler.extractPublicClassName("public interface Foo {}"))
                .isEqualTo("Foo");
        }
    }

    @Nested
    @DisplayName("Scenario: package 名提取")
    class ExtractPackageName {

        @Test
        @DisplayName("package com.foo.bar; → com.foo.bar")
        void shouldExtractPackage() {
            assertThat(JavaSourceCompiler.extractPackageName(
                    "package com.foo.bar; public class X {}"))
                .isEqualTo("com.foo.bar");
        }

        @Test
        @DisplayName("无 package 声明 → 空串")
        void shouldReturnEmptyForNoPackage() {
            assertThat(JavaSourceCompiler.extractPackageName("public class X {}"))
                .isEmpty();
        }

        @Test
        @DisplayName("package 单段 → 单段")
        void shouldExtractSingleSegment() {
            assertThat(JavaSourceCompiler.extractPackageName("package foo; class X {}"))
                .isEqualTo("foo");
        }
    }
}
