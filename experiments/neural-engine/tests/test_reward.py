"""阶段 1.1:编排器 reward 计算管道测试。

reward = A(发现效率) + B(避免重复),见 DESIGN.md §4。
A = 有效特征数 / 云端调用次数(正向)
B = -(死路重复数 / 提案数)(负向惩罚)

设计决策:
- reward 计算是纯函数,消费 RoundRecord(只读)+ RoundExtras(补 baseline 缺的字段)
- 不动 selflearn/loop.py(baseline 保持纯净),reward 是叠加层
- RoundExtras 补两个字段:cloud_calls(云端调用次数)、dead_end_repeats(命中已有死路的提案数)
"""

from __future__ import annotations

import pytest

from selflearn.loop import ProposalOutcome, RoundRecord
from selflearn.reward import (
    RewardBreakdown,
    RoundExtras,
    reward_a_discovery_efficiency,
    reward_b_dead_end_recall,
    orchestrator_reward,
)


# ---------------------------------------------------------------------------
# 辅助构造
# ---------------------------------------------------------------------------

def _proposal(name: str, verdict: str = "pass", q: float = 0.6) -> ProposalOutcome:
    return ProposalOutcome(name=name, expression=f"df.{name}", rationale="t",
                           verdict=verdict, q=q)


def _round(proposals, *, accepted=None, cloud_calls=1, dead_end_repeats=0) -> tuple[RoundRecord, RoundExtras]:
    accepted = accepted if accepted is not None else [
        p.name for p in proposals if p.verdict == "pass"
    ]
    rec = RoundRecord(
        round_no=1, task_id="t1", n_unexplained=10,
        auc_before=0.7, auc_after=0.72,
        proposals=proposals, accepted=accepted,
    )
    extras = RoundExtras(cloud_calls=cloud_calls, dead_end_repeats=dead_end_repeats)
    return rec, extras


# ---------------------------------------------------------------------------
# reward A:发现效率
# ---------------------------------------------------------------------------

class TestRewardA_DiscoveryEfficiency:
    """Given 一轮的提案裁决 + 云端调用次数,When 算发现效率,Then 返有效特征/调用次数。"""

    def test_一轮一次调用_两个有效特征_效率_2(self):
        # Given: 2 pass / 1 cloud call
        rec, extras = _round(
            [_proposal("f1"), _proposal("f2"), _proposal("bad", verdict="fail")],
            cloud_calls=1,
        )
        # When
        eff = reward_a_discovery_efficiency(rec, extras)
        # Then: 2 有效 / 1 调用 = 2.0
        assert eff == pytest.approx(2.0)

    def test_零云端调用_返_0_防空除零(self):
        # Given: 无云端调用(异常但防御)
        rec, extras = _round([_proposal("f1")], cloud_calls=0)
        # When
        eff = reward_a_discovery_efficiency(rec, extras)
        # Then: 防除零,返 0
        assert eff == 0.0

    def test_无有效特征_效率_0(self):
        # Given: 全 fail
        rec, extras = _round(
            [_proposal("f1", verdict="fail"), _proposal("f2", verdict="fail")],
            cloud_calls=1,
        )
        # When
        eff = reward_a_discovery_efficiency(rec, extras)
        # Then: 0 有效
        assert eff == 0.0

    def test_质量分低于阈值的_pass_不算有效特征(self):
        # Given: pass 但 q < 阈值(0.3)—— 弱特征不算"发现"
        rec, extras = _round(
            [_proposal("weak", q=0.2), _proposal("strong", q=0.8)],
            cloud_calls=1,
        )
        # When: 阈值 0.3
        eff = reward_a_discovery_efficiency(rec, extras, min_q=0.3)
        # Then: 只算 strong,效率 = 1/1
        assert eff == pytest.approx(1.0)

    def test_多次调用_效率按总调用分摊(self):
        # Given: 3 有效 / 2 调用
        rec, extras = _round(
            [_proposal("f1"), _proposal("f2"), _proposal("f3")],
            cloud_calls=2,
        )
        # When
        eff = reward_a_discovery_efficiency(rec, extras)
        # Then: 3/2 = 1.5
        assert eff == pytest.approx(1.5)


# ---------------------------------------------------------------------------
# reward B:避免重复
# ---------------------------------------------------------------------------

class TestRewardB_DeadEndRecall:
    """Given 一轮的死路重复情况,When 算避免重复分,Then 返负惩罚。"""

    def test_零重复_惩罚_0(self):
        # Given: 5 提案,0 命中已有死路
        rec, extras = _round([_proposal(f"f{i}") for i in range(5)], dead_end_repeats=0)
        # When
        pen = reward_b_dead_end_recall(rec, extras)
        # Then: 无惩罚
        assert pen == 0.0

    def test_全部重复_惩罚_负_1(self):
        # Given: 5 提案,5 全命中死路(最差)
        rec, extras = _round([_proposal(f"f{i}") for i in range(5)], dead_end_repeats=5)
        # When
        pen = reward_b_dead_end_recall(rec, extras)
        # Then: -5/5 = -1.0
        assert pen == pytest.approx(-1.0)

    def test_半数重复_惩罚_负_0_5(self):
        # Given: 4 提案,2 命中死路
        rec, extras = _round([_proposal(f"f{i}") for i in range(4)], dead_end_repeats=2)
        # When
        pen = reward_b_dead_end_recall(rec, extras)
        # Then: -2/4 = -0.5
        assert pen == pytest.approx(-0.5)

    def test_零提案_惩罚_0_防空除零(self):
        # Given: 无提案
        rec, extras = _round([], dead_end_repeats=0)
        # When
        pen = reward_b_dead_end_recall(rec, extras)
        # Then: 防除零
        assert pen == 0.0


# ---------------------------------------------------------------------------
# 组合 reward
# ---------------------------------------------------------------------------

class TestOrchestratorReward:
    """Given A+B 两个信号,When 组合,Then 加权求和 + 返 breakdown。"""

    def test_组合返_breakdown_含_各分量(self):
        # Given: 2 有效 / 1 调用,A=2.0;0 重复,B=0
        rec, extras = _round([_proposal("f1"), _proposal("f2")], cloud_calls=1)
        # When
        br: RewardBreakdown = orchestrator_reward(rec, extras)
        # Then: breakdown 含 A/B/total
        assert br.reward_a == pytest.approx(2.0)
        assert br.reward_b == pytest.approx(0.0)
        assert br.total == pytest.approx(2.0 * 1.0 + 0.0 * 1.0)  # 默认权重 1:1

    def test_权重可调(self):
        # Given: A=2.0, B=-0.5
        rec, extras = _round(
            [_proposal("f1"), _proposal("f2")], cloud_calls=1, dead_end_repeats=1,
        )
        # 注意:2 提案 1 重复 → B = -0.5
        # When: w_a=2, w_b=3
        br = orchestrator_reward(rec, extras, w_a=2.0, w_b=3.0)
        # Then: total = 2*2.0 + 3*(-0.5) = 4.0 - 1.5 = 2.5
        assert br.total == pytest.approx(2.5)

    def test_breakdown_可序列化为_dict(self):
        # Given
        rec, extras = _round([_proposal("f1")], cloud_calls=1)
        # When
        br = orchestrator_reward(rec, extras)
        d = br.as_dict()
        # Then: dict 含所有字段,可落 jsonl
        assert {"reward_a", "reward_b", "total", "w_a", "w_b"} <= set(d.keys())
        assert isinstance(d["total"], float)
