"""阶段 1.5:轻量 reward 代理测试(方案 B)。

核心验证:reward 有区分度(好方向 > 差方向 > 死路 > 不可解析)。
如果无区分度,GRPO 学不到东西 -- 这是阶段 1.5 证伪的前提。
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from selflearn.action import SimpleAction
from selflearn.reward_proxy import (
    DirectionValueTable,
    build_direction_value_table,
    proxy_reward,
)


def _make_table() -> DirectionValueTable:
    """构造一个有区分度的测试表(手写,不依赖 CLAB)。"""
    return DirectionValueTable(
        single_values={"savings_months": 0.5, "debt_to_income": 0.1, "income": 0.3},
        pair_values={("savings_months", "debt_to_income"): 0.8},
        dead_end_fields=frozenset({"debt_to_income"}),  # debt_to_income 已死路
    )


class TestDirectionValueTableLookup:
    """Given 方向价值表,When 查 keywords,Then 返合理价值。"""

    def test_单字段_查单字段价值(self):
        # Given
        table = _make_table()
        # When
        v = table.lookup(("savings_months",))
        # Then
        assert v == pytest.approx(0.5)

    def test_字段对_查交互价值(self):
        # Given
        table = _make_table()
        # When
        v = table.lookup(("savings_months", "debt_to_income"))
        # Then: 命中字段对(0.8),但有死路惩罚(debt_to_income 死路,扣 0.3)
        assert v == pytest.approx(0.8 - 0.3)

    def test_未知字段_返_0(self):
        # Given
        table = _make_table()
        # When
        v = table.lookup(("unknown_field",))
        # Then
        assert v == 0.0

    def test_死路字段_扣分(self):
        # Given
        table = _make_table()
        # When: debt_to_income 是死路字段
        v = table.lookup(("debt_to_income",))
        # Then: 0.1 - 0.3 = -0.2
        assert v == pytest.approx(0.1 - 0.3)

    def test_空关键词_返_0(self):
        table = _make_table()
        assert table.lookup(()) == 0.0


class TestProxyReward:
    """Given 动作 + 价值表,When 算 proxy_reward,Then 有区分度。"""

    def test_好方向_高_reward(self):
        # Given
        table = _make_table()
        action = SimpleAction(tool="CART",
                              direction_keywords=("savings_months",))
        # When
        r = proxy_reward(action, table)
        # Then: 0.5(单字段高 IV)
        assert r == pytest.approx(0.5)

    def test_差方向_低_reward(self):
        # Given
        table = _make_table()
        action = SimpleAction(tool="GBDT",
                              direction_keywords=("unknown_field",))
        # When
        r = proxy_reward(action, table)
        # Then: 0.0
        assert r == pytest.approx(0.0)

    def test_死路方向_负_reward(self):
        # Given
        table = _make_table()
        action = SimpleAction(tool="GBDT",
                              direction_keywords=("debt_to_income",))
        # When
        r = proxy_reward(action, table)
        # Then: 0.1 - 0.3 = -0.2(负,惩罚)
        assert r < 0

    def test_不可解析动作_最差_reward(self):
        # Given
        table = _make_table()
        # When
        r = proxy_reward(None, table)
        # Then: -1.0(最差,鼓励产出可解析动作)
        assert r == -1.0

    def test_区分度_好方向_大于_差方向_大于_死路_大于_不可解析(self):
        """核心:reward 有区分度(GRPO 收敛前提)。"""
        # Given
        table = _make_table()
        good = SimpleAction(tool="CART", direction_keywords=("savings_months",))
        bad = SimpleAction(tool="GBDT", direction_keywords=("unknown_field",))
        dead = SimpleAction(tool="GBDT", direction_keywords=("debt_to_income",))
        # When
        r_good = proxy_reward(good, table)
        r_bad = proxy_reward(bad, table)
        r_dead = proxy_reward(dead, table)
        r_none = proxy_reward(None, table)
        # Then: 严格递减
        assert r_good > r_bad > r_dead > r_none, (
            f"无区分度: good={r_good}, bad={r_bad}, dead={r_dead}, none={r_none}"
        )


class TestBuildDirectionValueTable:
    """Given CLAB dev 帧,When 构建价值表,Then 字段齐 + 有值。"""

    def test_从真实数据构建(self):
        # Given: 3 字段小数据
        rng = np.random.default_rng(42)
        n = 200
        df = pd.DataFrame({
            "f1": rng.normal(0, 1, n),
            "f2": rng.normal(0, 1, n),
            "f3": rng.normal(0, 1, n),
        })
        # f1 跟标签相关(有 IV),f2/f3 随机(低 IV)
        labels = (df["f1"] > 0.5).astype(int).to_numpy()
        # When
        table = build_direction_value_table(df, labels)
        # Then: 3 个单字段 + 3 个字段对
        assert len(table.single_values) == 3
        assert len(table.pair_values) == 3
        # f1 的 IV 应该最高(跟标签相关)
        assert table.single_values["f1"] > table.single_values["f2"]

    def test_带死路字段集(self):
        # Given
        rng = np.random.default_rng(0)
        df = pd.DataFrame({"f1": rng.normal(0, 1, 100), "f2": rng.normal(0, 1, 100)})
        labels = rng.integers(0, 2, 100).astype(np.int8)
        # When
        table = build_direction_value_table(
            df, labels, dead_end_fields=frozenset({"f1"})
        )
        # Then
        assert "f1" in table.dead_end_fields
