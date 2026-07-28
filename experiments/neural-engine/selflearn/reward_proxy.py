"""阶段 1.5:GRPO 训练用的轻量 reward 代理(方案 B)。

真执行(跑 GBDT + 免疫系统)太慢,GRPO 训练不现实。用预计算的"方向价值表"
估 reward -- 字段组合的历史 IV 越高,该方向的 reward 越高。

代理逻辑:
  1. 预计算:每个单字段的 IV + 每个字段对的交互 IV(从 CLAB dev 帧)
  2. 编排器动作 -> 解析出 direction_keywords
  3. 查表:keywords 命中的字段/字段对的 IV -> reward
  4. 死路惩罚:keywords 命中死路档案 -> reward 扣分(reward B 语义)

这是 reward shaping,让 GRPO 能快速跑。真正的好坏评估留给阶段 1 终证伪。
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd

from verify.metrics import information_value, lift, lift_good

from .action import SimpleAction


@dataclass(frozen=True)
class DirectionValueTable:
    """预计算的方向价值表(GRPO reward 代理用)。

    单字段价值:{field_name: iv_value}
    字段对价值:{(field_a, field_b): iv_value}  (交互特征的 IV)
    死路字段集:已进死路档案的字段名(命中则 reward 扣分)
    """

    single_values: dict[str, float] = field(default_factory=dict)
    pair_values: dict[tuple[str, str], float] = field(default_factory=dict)
    dead_end_fields: frozenset[str] = field(default_factory=frozenset)

    def lookup(self, keywords: tuple[str, ...]) -> float:
        """查 keywords 命中的方向价值(IV 代理)。

        - 单关键词:查单字段价值
        - 多关键词:查字段对价值(没命中则取单字段平均)
        - 命中死路:扣分(reward B 语义)
        """
        if not keywords:
            return 0.0

        # 死路惩罚:任一 keyword 命中死路字段 -> 扣分
        dead_hits = sum(1 for k in keywords if k in self.dead_end_fields)

        if len(keywords) == 1:
            base = self.single_values.get(keywords[0], 0.0)
        else:
            # 多关键词:查字段对
            pair_ivs = []
            for i, ka in enumerate(keywords):
                for kb in keywords[i + 1:]:
                    # 两个方向都查(字段对无序)
                    v = (self.pair_values.get((ka, kb), 0.0)
                         or self.pair_values.get((kb, ka), 0.0))
                    pair_ivs.append(v)
            if pair_ivs and any(v > 0 for v in pair_ivs):
                base = max(pair_ivs)  # 取最强的交互
            else:
                # 没命中字段对,退化为单字段平均
                singles = [self.single_values.get(k, 0.0) for k in keywords]
                base = sum(singles) / len(singles) if singles else 0.0

        # 死路惩罚:每个死路命中扣 0.3(reward B 的代理)
        penalty = dead_hits * 0.3
        return base - penalty


def build_direction_value_table(
    df: pd.DataFrame,
    labels: np.ndarray,
    *,
    dead_end_fields: frozenset[str] = frozenset(),
    max_pairs: int = 28,  # 8 字段两两组合 28 对,全算
) -> DirectionValueTable:
    """从 CLAB dev 帧预计算方向价值表。

    单字段 IV:每个字段的 information_value。
    字段对 IV:两个字段的乘积交互的 information_value(交互特征代理)。
    """
    fields = list(df.columns)
    single_values: dict[str, float] = {}
    for f in fields:
        single_values[f] = float(information_value(df[f].to_numpy(), labels))

    pair_values: dict[tuple[str, str], float] = {}
    for i, fa in enumerate(fields):
        for fb in fields[i + 1:]:
            if len(pair_values) >= max_pairs:
                break
            interaction = (df[fa] * df[fb]).to_numpy()
            pair_values[(fa, fb)] = float(information_value(interaction, labels))

    return DirectionValueTable(
        single_values=single_values,
        pair_values=pair_values,
        dead_end_fields=dead_end_fields,
    )


def proxy_reward(
    action: SimpleAction,
    table: DirectionValueTable,
    *,
    w_a: float = 1.0,
    w_b: float = 1.0,
    explored_fields: frozenset[str] | None = None,
    novelty_bonus: float = 0.15,
) -> float:
    """GRPO 训练用的轻量 reward 代理(方案 B,2026-07 加探索多样性)。

    = w_a * 方向价值(IV 代理) + w_b * (-死路惩罚) + 探索多样性 bonus
    不跑 GBDT/免疫系统,毫秒级,GRPO 训练友好。

    探索多样性(2026-07 加,修根因 3):如果 action 选的字段里有没有探索过的
    (不在 explored_fields 里),给 novelty_bonus 奖励。这防止编排器反复
    聚焦同一方向(模式坍塌)。

    无法解析的动作(action is None)-> 返负 reward(鼓励产出可解析动作)。
    """
    if action is None:
        return -1.0  # 不可解析 = 最差

    value = table.lookup(action.direction_keywords)

    # 探索多样性 bonus:选了没探索过的字段给奖励
    novelty = 0.0
    if explored_fields is not None and action.direction_keywords:
        new_fields = sum(
            1 for kw in action.direction_keywords
            if kw not in explored_fields
        )
        novelty = novelty_bonus * (new_fields / len(action.direction_keywords))

    return w_a * value + w_b * 0.0 + novelty  # 死路惩罚已在 lookup 里算了
