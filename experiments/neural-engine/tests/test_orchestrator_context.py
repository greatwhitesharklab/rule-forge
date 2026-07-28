"""阶段 1.4:编排器语境组装测试(方案 A -- 语境->指令,不执行)。

测试 OrchestratorContext 组装器:从 loop 状态采集六项语境(DESIGN.md §3.2),
格式化成 prompt 字符串,喂给 LLM。

六项语境:
  1. GBDT 重要性 top 特征
  2. 死路档案
  3. 残余信号分析
  4. 历史 reward 曲线
  5. 机构语境标签
  6. 可用工具列表

本测试只验证语境组装 + prompt 格式化,不碰 LLM 执行(那是 test_orchestrator_llm 的事)。
"""

from __future__ import annotations

import pytest

from selflearn.orchestrator import (
    OrchestratorContext,
    ToolOption,
    build_context,
    render_prompt,
)


class TestBuildContext:
    """Given loop 状态,When 组装语境,Then 六项齐 + 字段对。"""

    def test_组装含六项语境(self):
        # Given: loop 状态的各个数据源
        ctx = build_context(
            importance_top=[
                {"feature": "debt_to_income", "importance": 0.42},
                {"feature": "income_volatility", "importance": 0.18},
            ],
            dead_ends=["死路:f1 - 弱区分;死因:区分度不足"],
            residual_signals={"n_missed": 8, "numeric": [
                {"feature": "savings_months", "cohens_d": 0.35, "direction": "missed_lower"}
            ]},
            reward_history=[{"round": 1, "total": 18.0}, {"round": 2, "total": 19.0}],
            institution_tag="信贷审批",
            available_tools=[ToolOption.GBDT, ToolOption.CART],
        )
        # Then: 六项齐
        assert len(ctx.importance_top) == 2
        assert len(ctx.dead_ends) == 1
        assert ctx.residual_signals["n_missed"] == 8
        assert len(ctx.reward_history) == 2
        assert ctx.institution_tag == "信贷审批"
        assert ToolOption.GBDT in ctx.available_tools
        assert ToolOption.CART in ctx.available_tools

    def test_空死路档案_也合法(self):
        # Given: 首轮,无死路
        ctx = build_context(
            importance_top=[],
            dead_ends=[],
            residual_signals={"n_missed": 0},
            reward_history=[],
            institution_tag="信贷审批",
            available_tools=[ToolOption.GBDT],
        )
        # Then: 不崩,空列表合法
        assert ctx.dead_ends == []
        assert ctx.reward_history == []


class TestRenderPrompt:
    """Given 组装好的语境,When 渲染成 prompt,Then 含关键段 + 可读。"""

    def test_prompt_含所有段标题(self):
        # Given
        ctx = build_context(
            importance_top=[{"feature": "f1", "importance": 0.5}],
            dead_ends=["死路:f1 - 弱"],
            residual_signals={"n_missed": 5, "numeric": []},
            reward_history=[{"round": 1, "total": 18.0}],
            institution_tag="信贷审批",
            available_tools=[ToolOption.GBDT, ToolOption.CART],
        )
        # When
        prompt = render_prompt(ctx)
        # Then: 含六项的段标识(中文,0.6B 中文语境友好)
        assert "机构语境" in prompt
        assert "GBDT 重要性" in prompt
        assert "死路档案" in prompt
        assert "残余信号" in prompt
        assert "历史 reward" in prompt
        assert "可用工具" in prompt

    def test_prompt_含工具说明(self):
        # Given
        ctx = build_context(
            importance_top=[], dead_ends=[], residual_signals={"n_missed": 0},
            reward_history=[], institution_tag="信贷审批",
            available_tools=[ToolOption.GBDT, ToolOption.CART],
        )
        # When
        prompt = render_prompt(ctx)
        # Then: 工具段含说明(帮 LLM 理解每个工具的适用场景)
        assert "GBDT" in prompt
        assert "CART" in prompt
        # 工具带说明文字(不是光名字)
        assert "可解释" in prompt or "解释" in prompt  # CART 的说明

    def test_prompt_末尾含输出格式要求(self):
        # Given
        ctx = build_context(
            importance_top=[], dead_ends=[], residual_signals={"n_missed": 0},
            reward_history=[], institution_tag="信贷审批",
            available_tools=[ToolOption.GBDT],
        )
        # When
        prompt = render_prompt(ctx)
        # Then: prompt 末尾要求 LLM 输出 JSON 策略指令(确定性解析的前提)
        assert "JSON" in prompt or "json" in prompt
        assert "explore_direction" in prompt or "策略" in prompt

    def test_prompt_可读_不超长(self):
        # Given: 满语境
        ctx = build_context(
            importance_top=[{"feature": f"f{i}", "importance": 0.1 * i} for i in range(10)],
            dead_ends=[f"死路:f{i} - 原因{i}" for i in range(5)],
            residual_signals={"n_missed": 8, "numeric": [
                {"feature": f"v{i}", "cohens_d": 0.1 * i} for i in range(5)
            ]},
            reward_history=[{"round": i, "total": 18.0 + i} for i in range(1, 6)],
            institution_tag="信贷审批",
            available_tools=[ToolOption.GBDT, ToolOption.CART, ToolOption.RULES],
        )
        # When
        prompt = render_prompt(ctx)
        # Then: 不超 2000 字符(0.6B context 友好;阶段 1 验证够不够)
        assert len(prompt) < 2000, f"prompt {len(prompt)} 字符过长"
