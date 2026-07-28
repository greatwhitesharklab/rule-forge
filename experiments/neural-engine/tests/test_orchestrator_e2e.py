"""阶段 1.4:编排器端到端管道测试(方案 A -- 语境->指令,不执行)。

验证完整管道:语境组装 -> render_prompt -> LLM generate -> parse_strategy_directive。
用 FakeLLM(不碰真模型),验证管道闭环 + 策略指令可解析。

真模型测试在 test_orchestrator_real_model.py(slow_model 标记)。
"""

from __future__ import annotations

import pytest

from selflearn.orchestrator import (
    OrchestratorContext,
    StrategyDirective,
    ToolOption,
    build_context,
    parse_strategy_directive,
    render_prompt,
)


class FakeLLM:
    """不加载权重的假 LLM,返预设文本。"""

    def __init__(self, output: str) -> None:
        self._output = output
        self.last_prompt: str | None = None

    @property
    def loaded(self) -> bool:
        return True

    def generate(self, prompt: str, max_new_tokens: int = 128,
                 temperature: float = 0.0, top_p: float = 1.0) -> str:
        self.last_prompt = prompt
        return self._output


def _make_context() -> OrchestratorContext:
    return build_context(
        importance_top=[{"feature": "debt_to_income", "importance": 0.42}],
        dead_ends=["死路:sg_f1 - 弱;死因:区分度不足"],
        residual_signals={"n_missed": 5, "numeric": [
            {"feature": "savings_months", "cohens_d": 0.35, "direction": "missed_lower"}
        ]},
        reward_history=[{"round": 1, "total": 18.0}],
        institution_tag="信贷审批",
        available_tools=[ToolOption.GBDT, ToolOption.CART],
    )


class TestEndToEndPipeline:
    """Given 语境 + FakeLLM,When 跑完整管道,Then 拿到 StrategyDirective。"""

    def test_完整管道_语境到指令(self):
        # Given
        ctx = _make_context()
        llm = FakeLLM(
            '{"explore_direction": "储蓄月数×负债比交互", '
            '"rationale": "savings_months 残余信号强(d=0.35),需交互", '
            '"tool": "CART", '
            '"cloud_brief": "找低储蓄×高负债的组合特征"}'
        )
        # When: 完整管道
        prompt = render_prompt(ctx)
        raw = llm.generate(prompt, max_new_tokens=128)
        directive = parse_strategy_directive(raw)
        # Then
        assert directive is not None
        assert directive.explore_direction == "储蓄月数×负债比交互"
        assert directive.tool == "CART"
        assert "储蓄" in directive.cloud_brief or "负债" in directive.cloud_brief
        # LLM 收到了完整 prompt(含六项语境)
        assert "机构语境" in llm.last_prompt
        assert "死路档案" in llm.last_prompt

    def test_LLM_输出乱码_管道返_None_不崩(self):
        # Given
        ctx = _make_context()
        llm = FakeLLM("我觉得应该看负债比")  # 非 JSON
        # When
        prompt = render_prompt(ctx)
        raw = llm.generate(prompt)
        directive = parse_strategy_directive(raw)
        # Then: 解析失败返 None,管道不崩(调用方决定重试/兜底)
        assert directive is None

    def test_LLM_输出带_markdown_管道仍可解析(self):
        # Given: LLM 包了代码块
        ctx = _make_context()
        llm = FakeLLM(
            '```json\n{"explore_direction": "test", "rationale": "r", '
            '"tool": "GBDT", "cloud_brief": "b"}\n```'
        )
        # When
        prompt = render_prompt(ctx)
        raw = llm.generate(prompt)
        directive = parse_strategy_directive(raw)
        # Then
        assert directive is not None
        assert directive.explore_direction == "test"
