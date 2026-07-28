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
    容错(0.6B 在 trl 生成下产出的多种格式):
    - 标准格式:"CART savings_months debt_to_income"
    - 操作/字段格式:"操作: CART\n字段: savings_months, debt_to_income"
    - 嵌入格式:"根据...使用工具:GBDT 探索字段:months_employed"
    - 废话前置:扫描所有行找工具行或"操作:"/"字段:"行
    - 过滤噪声关键词("动作:" / "```" / 工具名 / 非字段 token)
    返 None:完全无法解析(空串/无工具/无有效关键词)。
    """
    if not raw or not raw.strip():
        return None

    text = raw.strip()

    # 策略 1:扫描"操作: <TOOL>" 或 "工具: <TOOL>" 格式(trl 下 0.6B 常产)
    tool = None
    keywords: list[str] = []
    for line in text.split("\n"):
        line = line.strip()
        if not line:
            continue
        # 找工具:"操作: CART" / "工具: GBDT" / "使用工具:GBDT"
        if tool is None:
            for prefix in ("操作:", "工具:", "使用工具:", "工具类型:"):
                if prefix in line:
                    after = line.split(prefix, 1)[1].strip()
                    for t in _KNOWN_TOOLS:
                        if t in after:
                            tool = t
                            break
                    if tool:
                        break
        # 找字段:"字段: savings_months, debt_to_income" / "探索字段: ..."
        for prefix in ("字段:", "探索字段:", "操作探索字段:"):
            if prefix in line:
                after = line.split(prefix, 1)[1].strip()
                parts = after.replace(",", " ").replace("、", " ").split()
                keywords.extend(k for k in parts if _is_valid_keyword(k))
                break

    if tool:
        return SimpleAction(tool=tool,
                            direction_keywords=tuple(k for k in keywords if _is_valid_keyword(k)))

    # 策略 2:扫描以已知工具开头的行(标准格式)
    for line in text.split("\n"):
        line = line.strip()
        if not line:
            continue
        # 去掉"动作:"/"输出:"/"答案:"等前缀
        for prefix in ("动作:", "输出:", "答案:", "例如:", "示例:"):
            if line.startswith(prefix):
                line = line[len(prefix):].strip()
        match = _ACTION_RE.match(line)
        if match and (match.group() in _KNOWN_TOOLS
                      or match.group().upper() in {"GBDT", "CART", "RULES"}):
            tool = match.group()
            rest = line[match.end():].strip()
            raw_keywords = rest.split() if rest else []
            keywords = [k for k in raw_keywords if _is_valid_keyword(k)]
            return SimpleAction(tool=tool, direction_keywords=tuple(keywords))

    # 策略 3:整文本里找已知工具 + 已知字段(最后兜底)
    found_tool = None
    for t in _KNOWN_TOOLS:
        if t in text:
            found_tool = t
            break
    if found_tool:
        found_keywords = [k for k in _KNOWN_FIELD_KEYWORDS if k in text]
        if found_keywords:
            return SimpleAction(tool=found_tool,
                                direction_keywords=tuple(found_keywords[:3]))

    return None


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
