"""阶段 1.5:简化动作解析 + cloud brief 生成测试。"""

from __future__ import annotations

import pytest

from selflearn.action import (
    SimpleAction,
    action_to_cloud_brief,
    parse_simple_action,
)


class TestParseSimpleAction:
    """Given LLM 生成的动作文本,When 解析,Then 返 SimpleAction。"""

    def test_标准格式_工具加关键词(self):
        # Given
        raw = "CART savings_months debt_to_income"
        # When
        a = parse_simple_action(raw)
        # Then
        assert a is not None
        assert a.tool == "CART"
        assert a.direction_keywords == ("savings_months", "debt_to_income")

    def test_单关键词(self):
        # Given
        raw = "GBDT income_volatility"
        # When
        a = parse_simple_action(raw)
        # Then
        assert a.tool == "GBDT"
        assert a.direction_keywords == ("income_volatility",)

    def test_只有工具无关键词(self):
        # Given
        raw = "GBDT"
        # When
        a = parse_simple_action(raw)
        # Then: 合法(关键词为空 tuple)
        assert a is not None
        assert a.tool == "GBDT"
        assert a.direction_keywords == ()

    def test_多余空白_也解析(self):
        # Given
        raw = "  CART   savings_months    debt_to_income  "
        # When
        a = parse_simple_action(raw)
        # Then
        assert a.tool == "CART"
        assert a.direction_keywords == ("savings_months", "debt_to_income")

    def test_大小写_小写工具也识别(self):
        # Given: 小写工具名
        raw = "cart savings"
        # When
        a = parse_simple_action(raw)
        # Then: 小写 cart 也是已知工具(大小写变体)
        assert a is not None
        assert a.tool == "cart"

    def test_未知工具名_返_None(self):
        # Given: 不在 GBDT/CART/RULES 里的工具名
        raw = "XGBOOST savings_months"
        # When
        a = parse_simple_action(raw)
        # Then: 未知工具 -> None(扫描跳过这行)
        assert a is None

    def test_空串_返_None(self):
        assert parse_simple_action("") is None
        assert parse_simple_action("   ") is None

    def test_纯标点_返_None(self):
        # Given: 无字母开头
        raw = "!!! test"
        # When
        a = parse_simple_action(raw)
        # Then
        assert a is None

    # ---- 0.6B 实际输出的噪声模式(诊断 2026-07 阶段 1.5)----

    def test_重复动作行_只取第一行(self):
        """0.6B 会重复生成'动作:...'行,只取第一个。"""
        # Given
        raw = "GBDT savings_months\n动作:GBDT months_employed\n动作:GBDT delinquencies"
        # When
        a = parse_simple_action(raw)
        # Then: 只取第一行的 GBDT savings_months
        assert a is not None
        assert a.tool == "GBDT"
        assert "savings_months" in a.direction_keywords
        # 不该有"动作:"或别的行的字段
        assert "months_employed" not in a.direction_keywords
        assert "动作:" not in a.direction_keywords

    def test_行首动作前缀_去掉(self):
        """0.6B 可能在动作前加'动作:'。"""
        raw = "动作:CART savings_months debt_to_income"
        a = parse_simple_action(raw)
        assert a is not None
        assert a.tool == "CART"
        assert "savings_months" in a.direction_keywords

    def test_噪声关键词_过滤(self):
        """0.6B 产出 ``` / 动作: / 工具名当关键词,要过滤。"""
        raw = "CART savings_months ``` debt_to_income"
        a = parse_simple_action(raw)
        assert a is not None
        assert "savings_months" in a.direction_keywords
        assert "debt_to_income" in a.direction_keywords
        assert "```" not in a.direction_keywords

    def test_工具名不当关键词(self):
        """CART/GBDT 出现在关键词位置要过滤(不是字段)。"""
        raw = "CART CART savings_months"
        a = parse_simple_action(raw)
        assert a is not None
        assert "CART" not in a.direction_keywords
        assert "savings_months" in a.direction_keywords

    def test_全噪声_无有效关键词(self):
        """生成的全是噪声 -> 无工具行 -> None。"""
        raw = "``` ``` ```"
        a = parse_simple_action(raw)
        # 无已知工具行 -> None
        assert a is None

    def test_废话前置_跳过找工具行(self):
        """0.6B 常在动作前加废话(例如:/考虑.../根据...),扫描找工具行。"""
        raw = "根据以上信息,生成一个合理的动作描述。\n答案:\nCART savings_months debt_to_income"
        a = parse_simple_action(raw)
        assert a is not None
        assert a.tool == "CART"
        assert "savings_months" in a.direction_keywords

    def test_多个工具行_取第一个(self):
        """多个工具行,取第一个。"""
        raw = "GBDT income_volatility\nCART savings_months"
        a = parse_simple_action(raw)
        assert a.tool == "GBDT"
        assert "income_volatility" in a.direction_keywords

    # ---- trl 生成下 0.6B 的实际输出格式(诊断 2026-07 阶段 1.5)----

    def test_操作字段格式_解析(self):
        """trl 下 0.6B 产出 '操作: CART\\n字段: savings_months, debt_to_income'。"""
        raw = "操作: CART\n字段: savings_months, debt_to_income_obs"
        a = parse_simple_action(raw)
        assert a is not None
        assert a.tool == "CART"
        assert "savings_months" in a.direction_keywords
        assert "debt_to_income_obs" in a.direction_keywords

    def test_使用工具嵌入格式_解析(self):
        """0.6B 把工具嵌入句子:'根据...使用工具:GBDT 探索字段:months_employed'。"""
        raw = "根据上述条件,可以使用工具:GBDT  操作探索字段:months_employed, debt_to_income_obs, savings_months"
        a = parse_simple_action(raw)
        assert a is not None
        assert a.tool == "GBDT"
        assert "months_employed" in a.direction_keywords
        assert "debt_to_income_obs" in a.direction_keywords

    def test_废话前置_工具嵌入_无字段_返_None(self):
        """0.6B 废话 + 工具嵌入句子但无字段名 -> None(无探索方向不是有效动作)。"""
        raw = "现在需要根据给定的字段和残余信号,用GBDT和CART工具分别进行探索,输出两个动作"
        a = parse_simple_action(raw)
        # 有工具但无字段 -> 不是有效动作(策略 3 要求至少一个字段)
        assert a is None

    def test_trl_comp1_格式_精确解析(self):
        """trl debug 输出的 comp[1] 精确复现。"""
        raw = "操作: CART\n字段: savings_months, debt_to_income_obs\n操作: CART\n字段: income_volatilit"
        a = parse_simple_action(raw)
        assert a is not None
        assert a.tool == "CART"
        assert "savings_months" in a.direction_keywords
        assert "debt_to_income_obs" in a.direction_keywords


class TestActionToCloudBrief:
    """Given SimpleAction,When 转 cloud brief,Then 可读。"""

    def test_多关键词_组合特征(self):
        # Given
        a = SimpleAction(tool="CART",
                         direction_keywords=("savings_months", "debt_to_income"))
        # When
        brief = action_to_cloud_brief(a)
        # Then
        assert "savings_months" in brief
        assert "debt_to_income" in brief
        assert "组合" in brief or "交互" in brief

    def test_单关键词_相关特征(self):
        # Given
        a = SimpleAction(tool="GBDT", direction_keywords=("income_volatility",))
        # When
        brief = action_to_cloud_brief(a)
        # Then
        assert "income_volatility" in brief

    def test_无关键词_泛化出题(self):
        # Given
        a = SimpleAction(tool="RULES", direction_keywords=())
        # When
        brief = action_to_cloud_brief(a)
        # Then: 不崩,泛化
        assert len(brief) > 0
