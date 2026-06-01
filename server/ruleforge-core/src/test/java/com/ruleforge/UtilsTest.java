package com.ruleforge;

import com.ruleforge.model.GeneralEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Utils - 工具类")
class UtilsTest {

    @Nested
    @DisplayName("URL 编解码")
    class UrlCodec {

        // Given 字符串包含中文和特殊字符 "hello%20world%E4%B8%AD%E6%96%87"
        // When 调用 Utils.decodeURL
        // Then 应返回解码后的字符串 "hello world中文"
        @Test
        @DisplayName("应正确解码 URL 编码的字符串")
        void shouldDecodeUrlEncodedString() {
            String encoded = "hello%20world%E4%B8%AD%E6%96%87";
            String decoded = Utils.decodeURL(encoded);
            assertThat(decoded).isEqualTo("hello world中文");
        }

        // Given 字符串 "hello world"
        // When 调用 Utils.encodeURL
        // Then 应返回 URL 编码后的字符串
        @Test
        @DisplayName("应正确编码字符串为 URL 格式")
        void shouldEncodeStringToUrlFormat() {
            String encoded = Utils.encodeURL("hello world");
            // URLEncoder encodes space as '+'
            assertThat(encoded).isEqualTo("hello+world");
        }

        // Given 字符串 "hello"
        // When 调用 Utils.decodeURL 和 Utils.encodeURL 依次处理
        // Then 应还原为原始字符串
        @Test
        @DisplayName("编解码应可逆")
        void shouldBeReversible() {
            String original = "hello";
            String roundTrip = Utils.decodeURL(Utils.encodeURL(original));
            assertThat(roundTrip).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("对象属性操作")
    class ObjectProperty {

        // Given GeneralEntity 有属性 name="test"
        // When 调用 Utils.getObjectProperty(entity, "name")
        // Then 应返回 "test"
        @Test
        @DisplayName("应获取 GeneralEntity 的属性值")
        void shouldGetGeneralEntityProperty() {
            GeneralEntity entity = new GeneralEntity("User");
            entity.put("name", "test");
            Object value = Utils.getObjectProperty(entity, "name");
            assertThat(value).isEqualTo("test");
        }

        // Given GeneralEntity 实例
        // When 调用 Utils.setObjectProperty(entity, "age", "25") 然后 getObjectProperty
        // Then 应能正确设置并获取
        @Test
        @DisplayName("应设置并获取 GeneralEntity 的属性值")
        void shouldSetAndGetGeneralEntityProperty() {
            GeneralEntity entity = new GeneralEntity("User");
            Utils.setObjectProperty(entity, "age", "25");
            Object value = Utils.getObjectProperty(entity, "age");
            assertThat(value).isEqualTo("25");
        }

        // Given Map 实例中有 key="score", value=95
        // When 调用 Utils.getObjectProperty(map, "score")
        // Then 应返回 95
        @Test
        @DisplayName("应获取 Map 中的值")
        void shouldGetMapValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("score", 95);
            Object value = Utils.getObjectProperty(map, "score");
            assertThat(value).isEqualTo(95);
        }
    }
}
