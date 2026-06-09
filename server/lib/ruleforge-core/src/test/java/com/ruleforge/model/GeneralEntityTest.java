package com.ruleforge.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GeneralEntity - 通用实体")
class GeneralEntityTest {

    @Nested
    @DisplayName("属性存取")
    class PropertyAccess {

        // Given new GeneralEntity("User")
        // When 调用 put("name", "张三") 然后 get("name")
        // Then 应返回 "张三"
        @Test
        @DisplayName("应存取属性值")
        void shouldStoreAndRetrieveProperty() {
            // Given
            GeneralEntity entity = new GeneralEntity("User");
            // When
            entity.put("name", "张三");
            // Then
            assertThat(entity.get("name")).isEqualTo("张三");
        }

        // Given new GeneralEntity("User")
        // When 调用 getTargetClass()
        // Then 应返回 "User"
        @Test
        @DisplayName("应返回目标类名")
        void shouldReturnTargetClassName() {
            // Given
            GeneralEntity entity = new GeneralEntity("User");
            // When & Then
            assertThat(entity.getTargetClass()).isEqualTo("User");
        }

        // Given 两个 GeneralEntity("User") 都 put("name", "张三")
        // When 调用 equals
        // Then 应相等
        @Test
        @DisplayName("相同类名和内容的实体应相等")
        void shouldBeEqualWhenSameClassAndContent() {
            // Given
            GeneralEntity entity1 = new GeneralEntity("User");
            entity1.put("name", "张三");
            GeneralEntity entity2 = new GeneralEntity("User");
            entity2.put("name", "张三");
            // When & Then
            assertThat(entity1).isEqualTo(entity2);
        }

        // Given GeneralEntity("User") 和 GeneralEntity("Order")
        // When 调用 equals
        // Then 应不相等
        @Test
        @DisplayName("不同类名的实体应不相等")
        void shouldNotBeEqualWhenDifferentClass() {
            // Given
            GeneralEntity userEntity = new GeneralEntity("User");
            GeneralEntity orderEntity = new GeneralEntity("Order");
            // When & Then
            assertThat(userEntity).isNotEqualTo(orderEntity);
        }

        // Given new GeneralEntity("Empty")
        // When 未 put 任何属性
        // Then isEmpty() 应为 true
        @Test
        @DisplayName("空实体应返回 isEmpty=true")
        void shouldBeEmptyWhenNoProperties() {
            // Given
            GeneralEntity entity = new GeneralEntity("Empty");
            // When & Then
            assertThat(entity.isEmpty()).isTrue();
        }
    }
}
