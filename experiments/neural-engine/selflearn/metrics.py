"""阶段 1.3:编排质量评估指标(DESIGN.md §5.1)。

把 baseline 脚本里的聚合逻辑提成独立模块,让 baseline 和编排器用同一套指标
计算,保证对比公平。

三个指标(跨所有轮聚合):
  B_eff  发现效率 = Σ 有效特征 / Σ 云端调用
  B_rep  死路重复率 = Σ 死路重复 / Σ 提案数
  B_feat 总有效特征数(固定轮数内)

"有效"= verdict=="pass" 且 质量分 q >= min_q(默认 0.3,过滤弱特征)。
阈值与 reward.py DEFAULT_MIN_Q 一致(同一语义)。
"""

from __future__ import annotations

from dataclasses import dataclass, field

from selflearn.loop import RoundRecord
from selflearn.reward import DEFAULT_MIN_Q
from selflearn.types import RoundExtras

# 兜底:extras=None 的老记录按这个算(向后兼容 baseline 早期数据)
_FALLBACK_CLOUD_CALLS = 1
_FALLBACK_DEAD_END_REPEATS = 0


@dataclass(frozen=True)
class OrchestrationMetrics:
    """编排质量指标(跨轮聚合)。baseline 和编排器都产出这个,用于证伪判定对比。"""

    b_eff: float                # 发现效率(有效特征/云端调用)
    b_rep: float                # 死路重复率(死路重复/提案数)
    b_feat: int                 # 总有效特征数
    total_cloud_calls: int
    total_proposals: int
    total_dead_end_repeats: int
    n_rounds: int

    def as_dict(self) -> dict:
        return {
            "b_eff": round(self.b_eff, 4),
            "b_rep": round(self.b_rep, 4),
            "b_feat": self.b_feat,
            "total_cloud_calls": self.total_cloud_calls,
            "total_proposals": self.total_proposals,
            "total_dead_end_repeats": self.total_dead_end_repeats,
            "n_rounds": self.n_rounds,
        }

    def beats(self, baseline: "OrchestrationMetrics", *,
              eff_factor: float = 1.3) -> "Verdict":
        """判定本指标是否 beat baseline(DESIGN.md §5.1 证伪标准)。

        判定规则:
          b_eff >= baseline.b_eff * eff_factor   (默认 1.3)
          b_rep <= baseline.b_rep                 (不高于 baseline)
          b_feat >= baseline.b_feat               (不低于 baseline)
        三项全过 = passed=True;任一不过 = failed 列出哪项。
        """
        failed: dict[str, str] = {}
        if self.b_eff < baseline.b_eff * eff_factor:
            failed["b_eff"] = (
                f"{self.b_eff:.4f} < {baseline.b_eff * eff_factor:.4f} "
                f"(baseline {baseline.b_eff:.4f} × {eff_factor})"
            )
        if self.b_rep > baseline.b_rep:
            failed["b_rep"] = (
                f"{self.b_rep:.4f} > {baseline.b_rep:.4f} "
                f"(不能高于 baseline)"
            )
        if self.b_feat < baseline.b_feat:
            failed["b_feat"] = (
                f"{self.b_feat} < {baseline.b_feat} "
                f"(不能低于 baseline)"
            )
        return Verdict(passed=not failed, failed=failed)


@dataclass(frozen=True)
class Verdict:
    """证伪判定结果。"""

    passed: bool
    failed: dict[str, str] = field(default_factory=dict)

    def as_dict(self) -> dict:
        return {"passed": self.passed, "failed": dict(self.failed)}


def aggregate_metrics(
    records: list[RoundRecord],
    *,
    min_q: float = DEFAULT_MIN_Q,
) -> OrchestrationMetrics:
    """跨轮聚合编排质量指标。

    records 里每条应带 extras(由 run_round 填充)。extras=None 的老记录
    按 _FALLBACK 兜底(cloud_calls=1, dead_end_repeats=0),不崩。
    """
    total_features_passed = 0
    total_cloud_calls = 0
    total_dead_end_repeats = 0
    total_proposals = 0

    for rec in records:
        extras = rec.extras
        cloud_calls = (extras.cloud_calls if extras is not None
                       else _FALLBACK_CLOUD_CALLS)
        dead_end_repeats = (extras.dead_end_repeats if extras is not None
                            else _FALLBACK_DEAD_END_REPEATS)

        total_features_passed += sum(
            1 for p in rec.proposals
            if p.verdict == "pass" and p.q >= min_q
        )
        total_cloud_calls += cloud_calls
        total_dead_end_repeats += dead_end_repeats
        total_proposals += len(rec.proposals)

    return OrchestrationMetrics(
        b_eff=total_features_passed / max(total_cloud_calls, 1),
        b_rep=total_dead_end_repeats / max(total_proposals, 1),
        b_feat=total_features_passed,
        total_cloud_calls=total_cloud_calls,
        total_proposals=total_proposals,
        total_dead_end_repeats=total_dead_end_repeats,
        n_rounds=len(records),
    )
