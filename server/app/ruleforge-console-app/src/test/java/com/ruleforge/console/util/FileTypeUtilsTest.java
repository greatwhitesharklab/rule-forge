package com.ruleforge.console.util;

import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.20.0:FileTypeUtils 加 .drl 映射 → FileType.Drl / Type.drl.
 * 锁住 DRL 端到端接入的转换层契约。
 */
@DisplayName("FileTypeUtils .drl 映射 (V6.20.0)")
class FileTypeUtilsTest {

    @Test
    @DisplayName("getFileTypeByFileName(\"x.drl\") == FileType.Drl")
    void drlExtensionMapsToFileTypeDrl() {
        assertThat(FileTypeUtils.getFileTypeByFileName("rule01.drl")).isEqualTo(FileType.Drl);
    }

    @Test
    @DisplayName("mapFileNameToType(\"x.drl\") == Type.drl")
    void drlExtensionMapsToTypeDrl() {
        assertThat(FileTypeUtils.mapFileNameToType("rule01.drl")).isEqualTo(Type.drl);
    }

    @Test
    @DisplayName("getFileTypeByFileName 大小写不敏感(\"X.DRL\")")
    void drlExtensionCaseInsensitive() {
        assertThat(FileTypeUtils.getFileTypeByFileName("Rule01.DRL")).isEqualTo(FileType.Drl);
    }

    @Test
    @DisplayName("getFileTypeByFileName 嵌套路径(\"/p/r/rule.drl\") == Drl")
    void drlExtensionNestedPath() {
        assertThat(FileTypeUtils.getFileTypeByFileName("/project/rules/rule01.drl")).isEqualTo(FileType.Drl);
    }

    @Test
    @DisplayName("mapFileNameToType 嵌套路径 → Type.drl")
    void drlExtensionNestedPathMapsToType() {
        assertThat(FileTypeUtils.mapFileNameToType("/project/rules/rule01.drl")).isEqualTo(Type.drl);
    }

    @Test
    @DisplayName("getFileTypeByFileName 空串 → null")
    void emptyStringReturnsNull() {
        assertThat(FileTypeUtils.getFileTypeByFileName("")).isNull();
    }

    @Test
    @DisplayName("getFileTypeByFileName null → null")
    void nullReturnsNull() {
        assertThat(FileTypeUtils.getFileTypeByFileName(null)).isNull();
    }

    // V7.0.0:V1 决策流(.json)→ FileType.V1Flow / Type.v1flow(树发现 + handleFileOpen 开画布)
    @Test
    @DisplayName("V7.0.0:getFileTypeByFileName(\"loan.json\") == FileType.V1Flow")
    void jsonExtensionMapsToFileTypeV1Flow() {
        assertThat(FileTypeUtils.getFileTypeByFileName("loan_approval.json")).isEqualTo(FileType.V1Flow);
    }

    @Test
    @DisplayName("V7.0.0:mapFileNameToType(\"loan.json\") == Type.v1flow")
    void jsonExtensionMapsToTypeV1Flow() {
        assertThat(FileTypeUtils.mapFileNameToType("/proj/loan_approval.json")).isEqualTo(Type.v1flow);
    }
}