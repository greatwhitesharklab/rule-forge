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

    // ---- V7.7.1:isV1JsonAsset(/common/saveFile 跳过 XML 校验用)----

    @Test
    @DisplayName("isV1JsonAsset:V1 双后缀文件 → true")
    void v1DoubleSuffixJsonAssets() {
        assertThat(FileTypeUtils.isV1JsonAsset("/p/V1决策流/loan.v1flow.json")).isTrue();
        assertThat(FileTypeUtils.isV1JsonAsset("/p/V1库/lib.v1lib.json")).isTrue();
        assertThat(FileTypeUtils.isV1JsonAsset("/p/V1规则集/pre.v1rs.json")).isTrue();
        assertThat(FileTypeUtils.isV1JsonAsset("/p/V1决策表/dt.v1dt.json")).isTrue();
        assertThat(FileTypeUtils.isV1JsonAsset("/p/V1评分卡/sc.v1sc.json")).isTrue();
    }

    @Test
    @DisplayName("isV1JsonAsset:.json 兼容旧 → true")
    void bareJsonCompatTrue() {
        assertThat(FileTypeUtils.isV1JsonAsset("/proj/loan_approval.json")).isTrue();
    }

    @Test
    @DisplayName("isV1JsonAsset:老 XML 规则文件 → false(仍走 XML 校验)")
    void oldXmlRuleFilesFalse() {
        assertThat(FileTypeUtils.isV1JsonAsset("/p/x.rs.xml")).isFalse();
        assertThat(FileTypeUtils.isV1JsonAsset("/p/x.dt.xml")).isFalse();
        assertThat(FileTypeUtils.isV1JsonAsset("/p/x.sc")).isFalse();
        assertThat(FileTypeUtils.isV1JsonAsset("/p/r.drl")).isFalse();
    }

    // ---- V7.7.1:isJsonContent(V1 文件 createFile 落 DB 无后缀,补 content sniff)----

    @Test
    @DisplayName("isJsonContent:JSON 对象字面量 → true")
    void jsonObjectLiteral() {
        assertThat(FileTypeUtils.isJsonContent("{\"id\":\"demo\"}")).isTrue();
    }

    @Test
    @DisplayName("isJsonContent:JSON 数组字面量 → true")
    void jsonArrayLiteral() {
        assertThat(FileTypeUtils.isJsonContent("[1,2,3]")).isTrue();
    }

    @Test
    @DisplayName("isJsonContent:前导空白 → true")
    void jsonWithLeadingWhitespace() {
        assertThat(FileTypeUtils.isJsonContent("  \n\t{\"a\":1}")).isTrue();
    }

    @Test
    @DisplayName("isJsonContent:XML 头 → false")
    void xmlContentFalse() {
        assertThat(FileTypeUtils.isJsonContent("<?xml version=\"1.0\"?>")).isFalse();
        assertThat(FileTypeUtils.isJsonContent("<rule-set/>")).isFalse();
    }

    @Test
    @DisplayName("isJsonContent:null/空 → false")
    void jsonNullOrEmpty() {
        assertThat(FileTypeUtils.isJsonContent(null)).isFalse();
        assertThat(FileTypeUtils.isJsonContent("")).isFalse();
    }
}