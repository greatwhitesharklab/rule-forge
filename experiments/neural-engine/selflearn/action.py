"""阶段 1.5:编排器简化动作执行层(方案 B)。

把编排器的"简化动作"(选工具 + 选方向关键词)翻译成确定性执行,算出 reward。
这是 GRPO reward_func 的核心 -- reward_func 拿到 LLM 生成的动作文本,
调本模块执行 + 算 A+B。

简化动作格式(0.6B 产出,GRPO 探索空间小):
  "CART savings_months debt_to_income"
  = 选 CART 工具 + 探索 savings_months × debt_to_income 交互方向

执行层做的事(确定性,不碰 LLM):
  1. 解析动作文本 -> (tool, direction_keywords)
  2. 按 direction_keywords 构造特征表达式(云端出题的 brief)
  3. 调 ClabAutoCloud 产出候选特征(模拟云端执行)
  4. 免疫系统验证
  5. 算 reward A(发现效率) + B(避免重复)

关键设计:执行层复用现有 selflearn/loop.py 的 _judge + verify,
不重写验证逻辑,保证跟 baseline 对比公平。
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from selflearn.orchestrator import ToolOption


# CLAB 已知字段白名单(过滤 LLM 生成的噪声关键词)
# 0.6B 常产出 "动作:" / "```" / markdown 标记等,不是真实字段
_KNOWN_FIELD_KEYWORDS = frozenset({
    "income_volatility_obs", "debt_to_income_obs", "credit_history_years_reported",
    "delinquencies_reported", "months_employed", "savings_months_obs",
    "requested_loan_to_income", "platform_loans_disclosed",
    # 容忍不带 _obs 后缀的简写(0.6B 可能简写)
    "income_volatility", "debt_to_income", "credit_history_years",
    "delinquencies", "savings_months", "requested_loan_to_income",
    "platform_loans", "savings", "income", "debt",
})

# 噪声标记(0.6B 常产出,直接过滤)
_NOISE_TOKENS = frozenset({
    "动作:", "动作", "```", "json", "JSON", "输出", "解", "答",
    "CART", "GBDT", "RULES",  # 工具名不当关键词
    "cart", "gbdt", "rules",
})


@dataclass(frozen=True)
class SimpleAction:
    """编排器的简化动作(GRPO 探索空间小,易收敛)。

    格式:"<TOOL> <keyword1> <keyword2> ..."
    例:"CART savings_months debt_to_income"
    = 选 CART + 探索 savings_months × debt_to_income 交互
    """

    tool: str               # GBDT / CART / RULES(LLM 可能编别的,执行层不拒绝)
    direction_keywords: tuple[str, ...]   # 探索方向的关键词(字段名)


# 动作解析:第一段是工具,其余是关键词
_ACTION_RE = re.compile(r"^[\w]+")


def _is_valid_keyword(token: str) -> bool:
    """关键词是否有效(是已知字段,不是噪声/工具名)。"""
    if token in _NOISE_TOKENS:
        return False
    # 已知字段(含简写)
    if token in _KNOWN_FIELD_KEYWORDS:
        return True
    # 容忍未知但像字段的(token 含下划线 + 字母,长度 > 3)
    return bool(re.match(r"^[a-z][a-z_]{3,}$", token)) and token not in _NOISE_TOKENS


# 合法工具名(0.6B 可能编别的,但这些是已知的)
_KNOWN_TOOLS = frozenset({"GBDT", "CART", "RULES", "gbdt", "cart", "rules"})


def parse_simple_action(raw: str) -> SimpleAction | None:
    """从 LLM 生成的动作文本解析简化动作。

    格式:"<TOOL> <keyword1> <keyword2> ..."
    容错:
    - 0.6B 常在动作前加废话("例如:"/"考虑..."/"根据..."),扫描所有行找第一个
      以已知工具(GBDT/CART/RULES)开头的行
    - 过滤噪声关键词("动作:" / "```" / 工具名 / 非字段 token)
    - 大小写不敏感 / 多余空白
    返 None:完全无法解析(空串/无工具行)。
    """
    if not raw or not raw.strip():
        return None

    # 0.6B 常在动作前加废话,扫描所有行找第一个工具行
    lines = raw.strip().split("\n")
    action_line = None
    for line in lines:
        line = line.strip()
        if not line:
            continue
        # 去掉"动作:"/"输出:"/"答案:"等前缀
        for prefix in ("动作:", "输出:", "答案:", "例如:", "示例:"):
            if line.startswith(prefix):
                line = line[len(prefix):].strip()
        # 第一段是不是已知工具?
        match = _ACTION_RE.match(line)
        if match and match.group() in _KNOWN_TOOLS:
            action_line = line
            break
        # 或者第一段是工具但大小写不同(已知工具的大小写变体)
        if match and match.group().upper() in {"GBDT", "CART", "RULES"}:
            action_line = line
            break

    if action_line is None:
        return None

    # 第一段 = 工具
    match = _ACTION_RE.match(action_line)
    if match is None:
        return None
    tool = match.group()
    # 其余 = 关键词(按空白分割,过滤噪声)
    rest = action_line[match.end():].strip()
    raw_keywords = rest.split() if rest else []
    keywords = tuple(k for k in raw_keywords if _is_valid_keyword(k))

    return SimpleAction(tool=tool, direction_keywords=keywords)


def action_to_cloud_brief(action: SimpleAction) -> str:
    """把简化动作转成给云端的出题摘要。

    "CART savings_months debt_to_income" ->
    "找 savings_months 与 debt_to_income 相关的组合特征"
    """
    if not action.direction_keywords:
        return "找有区分度的新特征"
    if len(action.direction_keywords) == 1:
        return f"找 {action.direction_keywords[0]} 相关的有区分度特征"
    fields = " 与 ".join(action.direction_keywords)
    return f"找 {fields} 的组合特征(交互可能有效)"
