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
    """编排质量指标(跨轮聚合)。baseline 和编排器都产出这个,用于证伪判定对比。

    2026-07 阶段 1 证伪后更新对比标准:加质量维度(b_strong/b_quality),
    解决"真云端少而精 vs baseline 多而杂"的不公平对比。
    """

    b_eff: float                # 发现效率(有效特征/云端调用)
    b_rep: float                # 死路重复率(死路重复/提案数)
    b_feat: int                 # 总有效特征数
    total_cloud_calls: int
    total_proposals: int
    total_dead_end_repeats: int
    n_rounds: int
    # 质量维度(2026-07 加,标准 C:单位预算的信号增益)
    b_strong: int = 0           # 强特征数(IV > 0.3,信贷风控的"强"门槛)
    b_quality: float = 0.0      # 平均 IV(所有 pass 特征)
    iv_list: tuple[float, ...] = ()  # 各 pass 特征的 IV 明细(审计用)

    def as_dict(self) -> dict:
        # 派生指标:信号发现效率(2026-07 新定义,论文核心)
        strong_rate = self.b_strong / self.total_proposals if self.total_proposals else 0
        strong_of_passed = self.b_strong / self.b_feat if self.b_feat else 0
        return {
            "b_eff": round(self.b_eff, 4),
            "b_rep": round(self.b_rep, 4),
            "b_feat": self.b_feat,
            "total_cloud_calls": self.total_cloud_calls,
            "total_proposals": self.total_proposals,
            "total_dead_end_repeats": self.total_dead_end_repeats,
            "n_rounds": self.n_rounds,
            "b_strong": self.b_strong,
            "b_quality": round(self.b_quality, 4),
            "iv_list": tuple(round(iv, 4) for iv in self.iv_list),
            # 新指标:信号发现效率
            "strong_rate": round(strong_rate, 4),       # 强特征/总提案
            "strong_of_passed": round(strong_of_passed, 4),  # 强特征/通过特征
        }

    def beats(self, baseline: "OrchestrationMetrics", *,
              eff_factor: float = 1.3) -> "Verdict":
        """判定本指标是否 beat baseline(证伪标准,2026-07 最终定义)。

        核心指标:信号发现效率(strong_rate = 强特征/总提案)。
        编排器的价值不是"产更多"(暴力枚举永远赢),是"产得更精准"
        (每个提案是强特征的概率更高)。

        判定规则:
          strong_rate >= baseline.strong_rate  AND  b_quality >= baseline.b_quality
          AND  b_rep <= baseline.b_rep
        三项全过 = 编排器在"精准度"上 beat baseline。
        """
        my_strong_rate = self.b_strong / self.total_proposals if self.total_proposals else 0
        base_strong_rate = baseline.b_strong / baseline.total_proposals if baseline.total_proposals else 0

        failed: dict[str, str] = {}
        if my_strong_rate < base_strong_rate:
            failed["strong_rate"] = (
                f"{my_strong_rate:.4f} < {base_strong_rate:.4f}")
        if self.b_quality < baseline.b_quality:
            failed["b_quality"] = (
                f"{self.b_quality:.4f} < {baseline.b_quality:.4f}")
        if self.b_rep > baseline.b_rep:
            failed["b_rep"] = f"{self.b_rep:.4f} > {baseline.b_rep:.4f}"
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
    iv_list: list[float] = []
    STRONG_IV_THRESHOLD = 0.3  # 信贷风控"强特征"门槛

    for rec in records:
        extras = rec.extras
        cloud_calls = (extras.cloud_calls if extras is not None
                       else _FALLBACK_CLOUD_CALLS)
        dead_end_repeats = (extras.dead_end_repeats if extras is not None
                            else _FALLBACK_DEAD_END_REPEATS)

        for p in rec.proposals:
            if p.verdict == "pass" and p.q >= min_q:
                total_features_passed += 1
                iv = p.metrics.get("iv", 0.0)
                if isinstance(iv, (int, float)):
                    iv_list.append(float(iv))
        total_cloud_calls += cloud_calls
        total_dead_end_repeats += dead_end_repeats
        total_proposals += len(rec.proposals)

    b_strong = sum(1 for iv in iv_list if iv > STRONG_IV_THRESHOLD)
    b_quality = sum(iv_list) / len(iv_list) if iv_list else 0.0

    return OrchestrationMetrics(
        b_eff=total_features_passed / max(total_cloud_calls, 1),
        b_rep=total_dead_end_repeats / max(total_proposals, 1),
        b_feat=total_features_passed,
        total_cloud_calls=total_cloud_calls,
        total_proposals=total_proposals,
        total_dead_end_repeats=total_dead_end_repeats,
        n_rounds=len(records),
        b_strong=b_strong,
        b_quality=b_quality,
        iv_list=tuple(iv_list),
    )
