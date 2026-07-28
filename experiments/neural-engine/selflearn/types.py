"""阶段 1.2:baseline RoundRecord 缺的、reward 计算需要的额外数据类型。

独立模块,打破循环依赖:loop.py 和 reward.py 都从这里 import RoundExtras,
避免 loop.py ↔ reward.py 互相 import。

字段语义见 reward.py 文档(DESIGN.md §4):
  cloud_calls      云端调用次数(reward A 分母)
  dead_end_repeats 命中已有死路档案的提案数(reward B 分子)
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class RoundExtras:
    """baseline RoundRecord 缺的、reward 计算需要的额外数据。

    由编排器执行层(未来的 loop 扩展 / orchestrator runner)在跑完一轮后填充,
    跟 RoundRecord 一起喂给 reward 计算。保持 RoundRecord 逻辑不变 = baseline 纯净。
    """

    cloud_calls: int
    dead_end_repeats: int
