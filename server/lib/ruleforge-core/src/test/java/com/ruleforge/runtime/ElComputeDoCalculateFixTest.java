package com.ruleforge.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.9.35 — {@link ElCompute#doCompute(String)} 算术契约 BDD。
 *
 * <p>锁 V6.9.35 修复 (ElCompute.java L161-174: doCalculate else 分支
 * {@code result = this.calculate(left, op, left)} 死分支 — left 被当作 right
 * 传入, 导致 {@code 2+3=6 / 5-2=0} 等算术结果错误) 的行为正确性: pop 顺序为
 * <em>right 在上, left 在下</em> (跟 L146-147 doCalculate(2) 分支保持一致),
 * 修复后 {@code calculate(left, op, right)} 才能给出正确算术。
 */
@DisplayName("V6.9.35 — ElCompute.doCalculate() 算术契约")
class ElComputeDoCalculateFixTest {

    @Nested
    @DisplayName("GIVEN doCompute 算术表达式")
    class Arithmetic {

        @Test
        @DisplayName("WHEN 2+3 THEN 5 (V6.9.35 修复后; 修复前是 6)")
        void twoPlusThree() {
            Object result = new ElCompute().doCompute("2+3");
            assertThat(((BigDecimal) result).intValue()).isEqualTo(5);
        }

        @Test
        @DisplayName("WHEN 5-2 THEN 3 (V6.9.35 修复后; 修复前是 0)")
        void fiveMinusTwo() {
            Object result = new ElCompute().doCompute("5-2");
            assertThat(((BigDecimal) result).intValue()).isEqualTo(3);
        }

        @Test
        @DisplayName("WHEN 10-3-2 THEN 5 (V6.9.35 修复后; 修复前是 5*2=10 或其他错误值)")
        void tenMinusThreeMinusTwo() {
            Object result = new ElCompute().doCompute("10-3-2");
            assertThat(((BigDecimal) result).intValue()).isEqualTo(5);
        }

        @Test
        @DisplayName("WHEN 22/2-(5+(1*2))-2*2 THEN 0 (V6.9.35 修复后)")
        void mainExpression() {
            Object result = new ElCompute().doCompute("22/2-(5+(1*2))-2*2");
            assertThat(((BigDecimal) result).intValue()).isEqualTo(0);
        }
    }
}
