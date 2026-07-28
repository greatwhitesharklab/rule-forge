"""阶段 1.1:编排器 reward 计算管道(DESIGN.md §4)。

reward 不是"决策对不对",是"编排后系统有没有变强"。两个即时信号:

  A 发现效率 = 有效特征数 / 云端调用次数        (正向,越多越好)
  B 避免重复 = -(死路重复数 / 提案数)            (负向惩罚,重复越少分越高)

组合:total = w_a * A + w_b * B(默认权重 1:1,阶段 1 调)。

设计决策:
- 纯函数,消费 RoundRecord(只读)+ RoundExtras(补 baseline 缺的字段)
- 不动 selflearn/loop.py(baseline 保持纯净),reward 是叠加层
- RoundExtras 补两个字段:
    cloud_calls      云端调用次数(reward A 分母,RoundRecord 没记)
    dead_end_repeats 命中已有死路档案的提案数(reward B 分子)

为什么不用 AUC 当 reward:见 DESIGN.md §4.4 —— GBDT 吃满信号后 AUC 无增量,
RL 无法收敛。编排质量(A/B)不依赖 AUC 涨。
"""

from __future__ import annotations

from dataclasses import dataclass

from selflearn.loop import RoundRecord
from selflearn.types import RoundExtras

# PASS 提案的质量分阈值:低于此不算"有效发现"(弱特征混进 reward 会虚高)。
# 默认 0.3 对应 verify.feature._strength 的"弱→中"分界(iv_term/lift_term 起步点)。
DEFAULT_MIN_Q = 0.3


@dataclass(frozen=True)
class RewardBreakdown:
    """一轮编排的 reward 分解,用于训练 + 审计落盘。"""

    reward_a: float       # 发现效率(正向)
    reward_b: float       # 避免重复(负向惩罚,≤ 0)
    total: float          # 加权组合
    w_a: float
    w_b: float

    def as_dict(self) -> dict[str, float]:
        return {
            "reward_a": self.reward_a,
            "reward_b": self.reward_b,
            "total": self.total,
            "w_a": self.w_a,
            "w_b": self.w_b,
        }


def reward_a_discovery_efficiency(
    record: RoundRecord,
    extras: RoundExtras,
    *,
    min_q: float = DEFAULT_MIN_Q,
) -> float:
    """A:发现效率 = 有效特征数 / 云端调用次数。

    "有效"= verdict=="pass" 且 质量分 q >= min_q(过滤弱特征)。
    防除零:cloud_calls=0 时返 0.0(无调用无效率可言)。
    """
    if extras.cloud_calls <= 0:
        return 0.0
    effective = sum(
        1 for p in record.proposals
        if p.verdict == "pass" and p.q >= min_q
    )
    return effective / extras.cloud_calls


def reward_b_dead_end_recall(
    record: RoundRecord,
    extras: RoundExtras,
) -> float:
    """B:避免重复 = -(死路重复数 / 提案数)。

    值域 [-1, 0]:0 = 无重复(最好),-1 = 全重复(最差)。
    防除零:无提案时返 0.0。
    """
    n_proposals = len(record.proposals)
    if n_proposals == 0:
        return 0.0
    return -extras.dead_end_repeats / n_proposals


def orchestrator_reward(
    record: RoundRecord,
    extras: RoundExtras,
    *,
    w_a: float = 1.0,
    w_b: float = 1.0,
    min_q: float = DEFAULT_MIN_Q,
) -> RewardBreakdown:
    """组合 reward:w_a * A + w_b * B。

    默认权重 1:1;阶段 1 通过 reward shaping 调。
    返 RewardBreakdown(含各分量),便于训练监控 + 审计落盘。
    """
    a = reward_a_discovery_efficiency(record, extras, min_q=min_q)
    b = reward_b_dead_end_recall(record, extras)
    return RewardBreakdown(
        reward_a=a, reward_b=b,
        total=w_a * a + w_b * b,
        w_a=w_a, w_b=w_b,
    )
