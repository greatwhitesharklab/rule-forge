"""阶段 1.4:编排器 LLM 调用 + 策略指令解析测试(方案 A)。

测试 OrchestratorLLM:
- 调用 LLM generate(prompt) -> 拿到原始文本
- 解析成 StrategyDirective(半结构化策略指令)
- 容错:LLM 输出带 markdown 代码块 / 多余文字 / 字段缺失

用注入的 FakeLLM(不碰真模型),验证解析逻辑的鲁棒性。
真模型测试在 test_orchestrator_real_model.py(slow_model 标记)。
"""

from __future__ import annotations

import pytest

from selflearn.orchestrator import (
    StrategyDirective,
    parse_strategy_directive,
)
from llm.local_llm import LocalLLM


class FakeLLM:
    """不加载权重的假 LLM,返预设文本。"""

    def __init__(self, output: str) -> None:
        self._output = output
        self.calls: list[str] = []

    @property
    def loaded(self) -> bool:
        return True

    def generate(self, prompt: str, max_new_tokens: int = 64,
                 temperature: float = 0.0, top_p: float = 1.0) -> str:
        self.calls.append(prompt)
        return self._output


# ---------------------------------------------------------------------------
# parse_strategy_directive:解析 LLM 输出
# ---------------------------------------------------------------------------

class TestParseStrategyDirective:
    """Given LLM 原始输出,When 解析,Then 返 StrategyDirective。"""

    def test_标准_json_解析成功(self):
        # Given
        raw = '{"explore_direction": "负债比×收入波动", "rationale": "单字段弱", "tool": "CART", "cloud_brief": "找高负债低稳定收入组合"}'
        # When
        d = parse_strategy_directive(raw)
        # Then
        assert d.explore_direction == "负债比×收入波动"
        assert d.rationale == "单字段弱"
        assert d.tool == "CART"
        assert d.cloud_brief == "找高负债低稳定收入组合"

    def test_带_markdown_代码块_也能解析(self):
        # Given: LLM 常把 JSON 包在 ```json ... ``` 里
        raw = '```json\n{"explore_direction": "test", "rationale": "r", "tool": "GBDT", "cloud_brief": "b"}\n```'
        # When
        d = parse_strategy_directive(raw)
        # Then
        assert d.explore_direction == "test"

    def test_带前后多余文字_提取_json(self):
        # Given: LLM 可能加废话
        raw = '好的,我的策略是:\n{"explore_direction": "test", "rationale": "r", "tool": "GBDT", "cloud_brief": "b"}\n以上。'
        # When
        d = parse_strategy_directive(raw)
        # Then
        assert d.explore_direction == "test"

    def test_字段缺失_返_None_不崩(self):
        # Given: LLM 漏了 cloud_brief
        raw = '{"explore_direction": "test", "rationale": "r", "tool": "GBDT"}'
        # When
        d = parse_strategy_directive(raw)
        # Then: 缺的字段为空字符串,不崩
        assert d.explore_direction == "test"
        assert d.cloud_brief == ""

    def test_完全无法解析_返_None(self):
        # Given: LLM 输出纯废话
        raw = "我觉得应该探索负债比方向"
        # When
        d = parse_strategy_directive(raw)
        # Then
        assert d is None

    def test_无效_json_返_None(self):
        # Given: JSON 语法错
        raw = '{"explore_direction": "test", "rationale": "r"'
        # When
        d = parse_strategy_directive(raw)
        # Then
        assert d is None

    def test_未知工具_规范化为字符串_不拒绝(self):
        # Given: LLM 编了个工具名
        raw = '{"explore_direction": "t", "rationale": "r", "tool": "XGBOOST", "cloud_brief": "b"}'
        # When
        d = parse_strategy_directive(raw)
        # Then: 不拒绝(编排代码层决定是否接受未知工具),解析层只管结构
        assert d.tool == "XGBOOST"
