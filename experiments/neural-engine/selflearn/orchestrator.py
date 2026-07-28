"""阶段 1.4:编排器语境组装 + prompt 渲染(方案 A -- 语境->指令,不执行)。

从 loop 状态采集六项语境(DESIGN.md §3.2),格式化成 prompt 喂给 0.6B LLM。
本模块只管"组装语境 + 渲染 prompt",不碰 LLM 执行(那是 OrchestratorLLM 的事)
也不碰指令翻译执行(那是阶段 1.4 后半段的事)。

六项语境:
  1. GBDT 重要性 top 特征     -- 知道系统现在靠什么
  2. 死路档案                  -- 哪些方向试过失败(记忆核心)
  3. 残余信号分析              -- GBDT 漏掉什么(指路)
  4. 历史 reward 曲线          -- 自我反思
  5. 机构语境标签              -- 机构绑定
  6. 可用工具列表              -- 中编排要选工具
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from enum import Enum


class ToolOption(str, Enum):
    """编排器可选的决策工具(中编排:决定调什么)。"""

    GBDT = "GBDT"       # 集成树,准但黑箱
    CART = "CART"       # 单树,可解释,可抽规则
    RULES = "RULES"     # 规则引擎(RuleForge V1 流),硬约束护栏


# 工具说明(帮 LLM 理解适用场景,中编排选工具的依据)
_TOOL_DESCRIPTIONS: dict[ToolOption, str] = {
    ToolOption.GBDT: "集成树,准确率高但黑箱,适合纯预测",
    ToolOption.CART: "单决策树,可解释,可抽 if-then 规则,监管友好",
    ToolOption.RULES: "规则引擎,确定性硬约束,适合合规护栏(人工写,非 AI 学)",
}


@dataclass(frozen=True)
class OrchestratorContext:
    """编排器决策语境(六项,DESIGN.md §3.2)。"""

    importance_top: list[dict]           # [{"feature": str, "importance": float}]
    dead_ends: list[str]                 # 死路档案 value_text 列表
    residual_signals: dict               # residual_signal_analysis 的输出
    reward_history: list[dict]           # [{"round": int, "total": float}]
    institution_tag: str                 # 机构语境标签
    available_tools: tuple[ToolOption, ...]


def build_context(
    *,
    importance_top: list[dict],
    dead_ends: list[str],
    residual_signals: dict,
    reward_history: list[dict],
    institution_tag: str,
    available_tools: list[ToolOption],
) -> OrchestratorContext:
    """从 loop 状态采集六项语境,组装成 OrchestratorContext。"""
    return OrchestratorContext(
        importance_top=importance_top,
        dead_ends=dead_ends,
        residual_signals=residual_signals,
        reward_history=reward_history,
        institution_tag=institution_tag,
        available_tools=tuple(available_tools),
    )


def render_prompt(ctx: OrchestratorContext) -> str:
    """把语境渲染成 prompt 字符串(中文,0.6B 中文语境友好)。

    结构:六段语境 + 输出格式要求。控制长度 < 2000 字符(0.6B context 友好)。
    """
    lines: list[str] = []

    # 机构语境
    lines.append(f"【机构语境】{ctx.institution_tag}")
    lines.append("")

    # GBDT 重要性 top(截 top 5,控长度)
    lines.append("【GBDT 重要性 top】")
    for t in ctx.importance_top[:5]:
        lines.append(f"  {t['feature']}: {t['importance']:.4f}")
    if not ctx.importance_top:
        lines.append("  (首轮,无历史)")
    lines.append("")

    # 死路档案(截 top 5,控长度)
    lines.append("【死路档案】(已试过失败的方向,出题时避开)")
    for d in ctx.dead_ends[:5]:
        lines.append(f"  {d}")
    if not ctx.dead_ends:
        lines.append("  (无)")
    lines.append("")

    # 残余信号(GBDT 漏掉的)
    lines.append("【残余信号】(GBDT 漏掉的方向,优先探索)")
    n_missed = ctx.residual_signals.get("n_missed", 0)
    lines.append(f"  漏网坏账数: {n_missed}")
    for sig in ctx.residual_signals.get("numeric", [])[:3]:
        feat = sig.get("feature", "?")
        d = sig.get("cohens_d", 0)
        direction = sig.get("direction", "?")
        lines.append(f"  {feat}: Cohen's d={d:.3f} ({direction})")
    lines.append("")

    # 历史 reward(自我反思)
    lines.append("【历史 reward】(最近轮次的编排效果)")
    for r in ctx.reward_history[-5:]:
        lines.append(f"  round {r['round']}: total={r['total']:+.2f}")
    if not ctx.reward_history:
        lines.append("  (首轮,无历史)")
    lines.append("")

    # 可用工具
    lines.append("【可用工具】(选一个用于本轮决策)")
    for tool in ctx.available_tools:
        desc = _TOOL_DESCRIPTIONS.get(tool, "")
        lines.append(f"  {tool.value}: {desc}")
    lines.append("")

    # 输出格式要求(确定性解析的前提)
    lines.append("【输出要求】请输出一个 JSON 策略指令,包含以下字段:")
    lines.append('  {"explore_direction": "探索方向简述",')
    lines.append('   "rationale": "为什么选这个方向",')
    lines.append('   "tool": "GBDT 或 CART 或 RULES",')
    lines.append('   "cloud_brief": "给云端大模型的出题摘要"}')
    lines.append("只输出 JSON,不要其他文字。")

    return "\n".join(lines)


# ---------------------------------------------------------------------------
# 策略指令解析(阶段 1.4 方案 A 第二块)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class StrategyDirective:
    """LLM 产出的策略指令(解析后)。

    对应 DESIGN.md §3.1 的半结构化策略指令。解析层只管结构,不管语义合理性
    (工具是否合法、方向是否在死路里 -- 那是执行层的事)。
    """

    explore_direction: str    # 探索方向简述
    rationale: str            # 为什么选这个方向
    tool: str                 # GBDT / CART / RULES(或 LLM 编的别的)
    cloud_brief: str          # 给云端大模型的出题摘要


# 匹配 { 开头到 } 结尾的 JSON 块(容忍前后多余文字 + markdown 代码块)
_JSON_BLOCK_RE = re.compile(r"\{[^{}]*\}", re.DOTALL)


def parse_strategy_directive(raw: str) -> StrategyDirective | None:
    """从 LLM 原始输出解析策略指令。

    容错:
    - markdown 代码块(```json ... ```)-> 提取里面的 JSON
    - 前后多余文字 -> 提取第一个 {...} 块
    - 字段缺失 -> 缺的填空字符串,不崩
    - 完全无法解析 -> 返 None(调用方决定重试还是兜底)

    返 None 的情况:无 JSON 块 / JSON 语法错 / 不是 dict。
    """
    if not raw or not raw.strip():
        return None

    # 先试直接解析(最干净的情况)
    try:
        obj = json.loads(raw.strip())
        if isinstance(obj, dict):
            return _directive_from_dict(obj)
    except json.JSONDecodeError:
        pass

    # 直接解析失败,提取第一个 {...} 块(容忍 markdown/多余文字)
    match = _JSON_BLOCK_RE.search(raw)
    if match is None:
        return None
    try:
        obj = json.loads(match.group())
    except json.JSONDecodeError:
        return None
    if not isinstance(obj, dict):
        return None
    return _directive_from_dict(obj)


def _directive_from_dict(obj: dict) -> StrategyDirective:
    """从 dict 构造 StrategyDirective,缺字段填空字符串。"""
    return StrategyDirective(
        explore_direction=str(obj.get("explore_direction", "")),
        rationale=str(obj.get("rationale", "")),
        tool=str(obj.get("tool", "")),
        cloud_brief=str(obj.get("cloud_brief", "")),
    )
