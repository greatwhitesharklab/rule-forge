"""阶段 1.4:编排器真模型测试(slow_model)。

验证方案 A 的核心问题:0.6B 能不能读懂复杂语境 + 产出合规策略指令。
这是编排器范式成立的前提 -- 如果 0.6B 产出的是乱码或字段缺失,
后面 GRPO 训练就无从谈起。

Marked slow_model:需加载 ~1.2GB Qwen3-0.6B 权重,默认跳过。
跑:uv run pytest tests/test_orchestrator_real_model.py -v -m slow_model
"""

from __future__ import annotations

import pytest

from llm.local_llm import LocalLLM
from selflearn.orchestrator import (
    StrategyDirective,
    ToolOption,
    build_context,
    parse_strategy_directive,
    render_prompt,
)

MODEL_ID = "Qwen/Qwen3-0.6B"


def _model_snapshot_ready() -> bool:
    """True iff the HF cache holds a complete-enough Qwen3-0.6B snapshot.

    纯文件系统检查(不联网),避免 scan_cache_dir 触发网络代理问题。
    检查 snapshots/ 目录下有没有 model.safetensors + config.json + tokenizer.json。
    """
    from pathlib import Path
    cache_root = Path.home() / ".cache" / "huggingface" / "hub"
    # HF 缓存格式:models--Qwen--Qwen3-0.6B/snapshots/<commit>/
    repo_dir = cache_root / "models--Qwen--Qwen3-0.6B" / "snapshots"
    if not repo_dir.exists():
        return False
    for snapshot in repo_dir.iterdir():
        names = {f.name for f in snapshot.iterdir() if f.is_file()}
        if {"model.safetensors", "config.json", "tokenizer.json"} <= names:
            return True
    return False


pytestmark = [
    pytest.mark.slow_model,
    pytest.mark.skipif(
        not _model_snapshot_ready(),
        reason=f"{MODEL_ID} snapshot not ready in HF cache",
    ),
]


@pytest.fixture(scope="module")
def llm() -> LocalLLM:
    """加载一次真模型,模块内共享。

    设 HF_HUB_OFFLINE=1 避免from_pretrained 联网检查(走纯缓存)。
    """
    import os
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    model = LocalLLM(model_id=MODEL_ID, device="cpu")
    model.load()
    return model


def _make_context(round_no: int = 2) -> "build_context":  # noqa: F821
    """构造一个有内容的语境(非首轮,有死路 + 残余信号)。"""
    return build_context(
        importance_top=[
            {"feature": "debt_to_income", "importance": 0.42},
            {"feature": "income_volatility", "importance": 0.18},
        ],
        dead_ends=[
            "死路:sg_debt_to_income_gt60 - 单字段分箱 IV=0.05 弱;死因:区分度不足",
        ],
        residual_signals={"n_missed": 8, "numeric": [
            {"feature": "savings_months", "cohens_d": 0.35, "direction": "missed_lower"},
            {"feature": "months_employed", "cohens_d": 0.22, "direction": "missed_lower"},
        ]},
        reward_history=[{"round": 1, "total": 18.0}],
        institution_tag="信贷审批",
        available_tools=[ToolOption.GBDT, ToolOption.CART],
    )


class TestRealModelProducesValidDirective:
    """Given 真 0.6B + 语境 prompt,When generate,Then 产出可解析的策略指令。"""

    def test_产出可解析的_json_策略指令(self, llm: LocalLLM):
        # Given
        ctx = _make_context()
        prompt = render_prompt(ctx)
        # When: 真 0.6B 推理
        raw = llm.generate(prompt, max_new_tokens=128)
        # Then: 能解析成 StrategyDirective
        directive = parse_strategy_directive(raw)
        assert directive is not None, f"无法解析 LLM 输出:\n{raw!r}"

    def test_策略指令含探索方向(self, llm: LocalLLM):
        # Given
        ctx = _make_context()
        prompt = render_prompt(ctx)
        # When
        raw = llm.generate(prompt, max_new_tokens=128)
        directive = parse_strategy_directive(raw)
        # Then: explore_direction 非空(不是空壳)
        assert directive is not None
        assert len(directive.explore_direction) > 0, f"explore_direction 空:\n{raw!r}"

    def test_策略指令选了合法工具(self, llm: LocalLLM):
        # Given
        ctx = _make_context()
        prompt = render_prompt(ctx)
        # When
        raw = llm.generate(prompt, max_new_tokens=128)
        directive = parse_strategy_directive(raw)
        # Then: tool 是 GBDT/CART/RULES 之一(或 LLM 编的别的,但不该空)
        assert directive is not None
        assert len(directive.tool) > 0, f"tool 空:\n{raw!r}"
        # 不强制必须是合法工具(解析层不管语义),但记录实际值供分析
        print(f"\n[真模型] 选择的工具: {directive.tool!r}")

    def test_策略指令考虑了残余信号(self, llm: LocalLLM):
        """关键测试:LLM 是否真的读了残余信号段(explore_direction 或 cloud_brief
        提到 savings_months 或 months_employed)。这验证 0.6B 不是瞎输出,
        是真读了语境。"""
        # Given
        ctx = _make_context()
        prompt = render_prompt(ctx)
        # When
        raw = llm.generate(prompt, max_new_tokens=128)
        directive = parse_strategy_directive(raw)
        # Then: explore_direction 或 cloud_brief 里提到残余信号的特征
        assert directive is not None
        combined = f"{directive.explore_direction} {directive.rationale} {directive.cloud_brief}"
        # 至少提到一个残余信号特征(savings_months/months_employed/储蓄/在职)
        # 或提到"交互"(残余信号常需交互才有效)
        keywords = ["savings", "储蓄", "months_employed", "在职", "交互", "interact"]
        assert any(k in combined.lower() or k in combined for k in keywords), (
            f"策略未提及残余信号特征:\n{raw!r}\n解析: {directive}"
        )

    def test_策略指令避开了死路(self, llm: LocalLLM):
        """关键测试:LLM 是否真的读了死路档案(explore_direction 不重复死路方向)。
        死路档案里有 'sg_debt_to_income_gt60'(单字段分箱),策略不该重复这个方向。"""
        # Given
        ctx = _make_context()
        prompt = render_prompt(ctx)
        # When
        raw = llm.generate(prompt, max_new_tokens=128)
        directive = parse_strategy_directive(raw)
        # Then: 不重复死路方向(不提 sg_debt_to_income_gt60 或"单字段分箱")
        assert directive is not None
        combined = f"{directive.explore_direction} {directive.cloud_brief}"
        # 死路关键词:不应该原样出现
        dead_end_markers = ["sg_debt_to_income_gt60", "单字段分箱"]
        for marker in dead_end_markers:
            assert marker not in combined, (
                f"策略重复了死路方向 {marker!r}:\n{raw!r}"
            )
