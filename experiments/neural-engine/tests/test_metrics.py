"""阶段 1.3:编排质量评估指标测试。

把 baseline 脚本里的聚合逻辑提成 selflearn/metrics.py,让 baseline 和编排器
用同一套指标计算,保证对比公平(DESIGN.md §5.1 的 B_eff/B_rep/B_feat)。

指标定义(跨所有轮聚合):
  B_eff  发现效率 = Σ 有效特征 / Σ 云端调用
  B_rep  死路重复率 = Σ 死路重复 / Σ 提案数
  B_feat 总有效特征数

聚合函数 consume list[RoundRecord](每条都带 extras),返 OrchestrationMetrics。
"""

from __future__ import annotations

import pytest

from selflearn.loop import ProposalOutcome, RoundRecord
from selflearn.metrics import OrchestrationMetrics, aggregate_metrics
from selflearn.types import RoundExtras


# ---------------------------------------------------------------------------
# 辅助构造
# ---------------------------------------------------------------------------

def _round(round_no: int, proposals, *, cloud_calls=1, dead_end_repeats=0,
           accepted=None) -> RoundRecord:
    accepted = accepted if accepted is not None else [
        p.name for p in proposals if p.verdict == "pass"
    ]
    return RoundRecord(
        round_no=round_no, task_id=f"t{round_no}", n_unexplained=10,
        auc_before=0.7, auc_after=0.72,
        proposals=proposals, accepted=accepted,
        extras=RoundExtras(cloud_calls=cloud_calls, dead_end_repeats=dead_end_repeats),
    )


def _prop(name: str, verdict: str = "pass", q: float = 0.6) -> ProposalOutcome:
    return ProposalOutcome(name=name, expression=f"df.{name}", rationale="t",
                           verdict=verdict, q=q)


# ---------------------------------------------------------------------------
# aggregate_metrics:跨轮聚合
# ---------------------------------------------------------------------------

class TestAggregateMetrics:
    """Given 多轮 RoundRecord(带 extras),When 聚合,Then 返三个指标。"""

    def test_单轮_两有效一调用_eff_2(self):
        # Given: 1 轮,2 pass / 1 cloud call
        records = [_round(1, [_prop("f1"), _prop("f2"), _prop("bad", verdict="fail")])]
        # When
        m = aggregate_metrics(records)
        # Then
        assert m.b_eff == pytest.approx(2.0)
        assert m.b_rep == pytest.approx(0.0)
        assert m.b_feat == 2

    def test_多轮_累加(self):
        # Given: 2 轮,各 2 pass / 1 call,无重复
        records = [
            _round(1, [_prop("f1"), _prop("f2")], cloud_calls=1),
            _round(2, [_prop("f3"), _prop("f4")], cloud_calls=1),
        ]
        # When
        m = aggregate_metrics(records)
        # Then: 4 有效 / 2 调用 = 2.0
        assert m.b_eff == pytest.approx(2.0)
        assert m.b_feat == 4

    def test_死路重复率_按总提案数算(self):
        # Given: 2 轮,round1 0 重复/3 提案,round2 2 重复/4 提案
        records = [
            _round(1, [_prop("f1"), _prop("f2"), _prop("f3")], dead_end_repeats=0),
            _round(2, [_prop("f4"), _prop("f5"), _prop("f6"), _prop("f7")],
                   dead_end_repeats=2),
        ]
        # When
        m = aggregate_metrics(records)
        # Then: 2 重复 / 7 提案
        assert m.b_rep == pytest.approx(2 / 7)

    def test_空列表_返零指标_防空除零(self):
        # Given: 无记录
        # When
        m = aggregate_metrics([])
        # Then
        assert m.b_eff == 0.0
        assert m.b_rep == 0.0
        assert m.b_feat == 0

    def test_质量分低于阈值的_pass_不算有效(self):
        # Given: 2 pass,但一个 q < 0.3
        records = [_round(1, [_prop("weak", q=0.2), _prop("strong", q=0.8)],
                          cloud_calls=1)]
        # When: min_q=0.3
        m = aggregate_metrics(records, min_q=0.3)
        # Then: 只算 strong
        assert m.b_feat == 1
        assert m.b_eff == pytest.approx(1.0)

    def test_extras_缺失的记录_按_cloud_calls_1_兜底(self):
        # Given: 一条老记录(无 extras,模拟 baseline 兼容)
        rec = RoundRecord(
            round_no=1, task_id="t", n_unexplained=0,
            auc_before=None, auc_after=None,
            proposals=[_prop("f1"), _prop("f2")], accepted=["f1", "f2"],
            extras=None,
        )
        # When
        m = aggregate_metrics([rec])
        # Then: 兜底 cloud_calls=1(dead_end_repeats=0),不崩
        assert m.b_eff == pytest.approx(2.0)  # 2/1
        assert m.b_rep == pytest.approx(0.0)


# ---------------------------------------------------------------------------
# OrchestrationMetrics:数据类 + 序列化 + 比较
# ---------------------------------------------------------------------------

class TestOrchestrationMetrics:
    """OrchestrationMetrics 数据类:可序列化 + 可比较(证伪判定用)。"""

    def test_as_dict_可落_jsonl(self):
        # Given
        m = OrchestrationMetrics(b_eff=1.5, b_rep=0.2, b_feat=6,
                                 total_cloud_calls=4, total_proposals=30,
                                 total_dead_end_repeats=6, n_rounds=3)
        # When
        d = m.as_dict()
        # Then
        assert d["b_eff"] == 1.5
        assert d["b_rep"] == 0.2
        assert d["b_feat"] == 6
        assert d["n_rounds"] == 3

    def test_beats_判定编排器是否超过_baseline(self):
        # Given: baseline
        baseline = OrchestrationMetrics(
            b_eff=16.6, b_rep=0.0, b_feat=83,
            total_cloud_calls=5, total_proposals=100,
            total_dead_end_repeats=0, n_rounds=5,
        )
        # When: 编排器结果
        orch = OrchestrationMetrics(
            b_eff=22.0, b_rep=0.0, b_feat=90,
            total_cloud_calls=5, total_proposals=100,
            total_dead_end_repeats=0, n_rounds=5,
        )
        # Then: 按 DESIGN.md §5.1 判定(b_eff × 1.3, b_rep ≤, b_feat ≥)
        verdict = orch.beats(baseline, eff_factor=1.3)
        assert verdict.passed is True
        assert "b_eff" not in verdict.failed  # 22.0 >= 16.6*1.3=21.58

    def test_beats_任一不达标_判失败(self):
        # Given: baseline(效率+质量都有值)
        baseline = OrchestrationMetrics(
            b_eff=16.6, b_rep=0.0, b_feat=83,
            total_cloud_calls=5, total_proposals=100,
            total_dead_end_repeats=0, n_rounds=5,
            b_strong=10, b_quality=0.3,
        )
        # When: 编排器效率不够(20<21.58)+ 质量也不够(strong=8<10)
        orch = OrchestrationMetrics(
            b_eff=20.0, b_rep=0.0, b_feat=90,
            total_cloud_calls=5, total_proposals=100,
            total_dead_end_repeats=0, n_rounds=5,
            b_strong=8, b_quality=0.25,
        )
        # Then: 两条路都没过
        verdict = orch.beats(baseline, eff_factor=1.3)
        assert verdict.passed is False
        assert "b_eff" in verdict.failed

    def test_beats_b_rep_超过_baseline_判失败(self):
        # Given: baseline
        baseline = OrchestrationMetrics(
            b_eff=16.6, b_rep=0.0, b_feat=83,
            total_cloud_calls=5, total_proposals=100,
            total_dead_end_repeats=0, n_rounds=5,
            b_strong=10, b_quality=0.3,
        )
        # When: 编排器 b_rep=0.1(效率路径挂)+ 质量也不够
        orch = OrchestrationMetrics(
            b_eff=22.0, b_rep=0.1, b_feat=90,
            total_cloud_calls=5, total_proposals=100,
            total_dead_end_repeats=10, n_rounds=5,
            b_strong=8, b_quality=0.25,
        )
        # Then: 两条路都没过(效率路径 b_rep 挂,质量路径也不够)
        verdict = orch.beats(baseline, eff_factor=1.3)
        assert verdict.passed is False

    def test_beats_质量路径通过_即使效率路径挂(self):
        """新标准 C:编排器'少而精'也能 beat baseline。"""
        # Given: baseline 效率高但质量一般
        baseline = OrchestrationMetrics(
            b_eff=16.6, b_rep=0.0, b_feat=83,
            total_cloud_calls=5, total_proposals=100,
            total_dead_end_repeats=0, n_rounds=5,
            b_strong=5, b_quality=0.25,
        )
        # When: 编排器效率低(3.6<<21.58)但质量高(strong=10>5, quality=0.4>0.25)
        orch = OrchestrationMetrics(
            b_eff=3.6, b_rep=0.0, b_feat=18,
            total_cloud_calls=5, total_proposals=25,
            total_dead_end_repeats=0, n_rounds=5,
            b_strong=10, b_quality=0.4,
        )
        # Then: 质量路径通过 -> passed=True
        verdict = orch.beats(baseline, eff_factor=1.3)
        assert verdict.passed is True
