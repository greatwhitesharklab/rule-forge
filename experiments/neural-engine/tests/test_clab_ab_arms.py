"""机制二:双臂配对统计 + 切换后再适应 + 小世界端到端。

结构断言:两臂同世界/同种子/同预排序候选表/同判卷 tau,唯一变量 =
策略记忆(臂 A 有 MemoryPolicy,臂 B 盲枚举)。
"""

from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
import pytest

from eval.clab_memory_ab import (
    ABConfig,
    paired_fp_stats,
    paired_speed_stats,
    readaptation,
    run_experiment,
)

HLD = [f"HLD-{i:02d}" for i in range(1, 11)]


class TestPairedSpeedStats:
    def test_all_faster_gives_positive_ci(self) -> None:
        per_seed = [
            {"A": {r: 2 for r in HLD}, "B": {r: 5 for r in HLD}},
            {"A": {r: 3 for r in HLD}, "B": {r: 6 for r in HLD}},
        ]
        out = paired_speed_stats(per_seed, heldout_ids=HLD)
        assert out["n"] == 20
        assert out["mean"] > 0
        assert out["ci95"][0] > 0  # 配对差 CI 不含 0

    def test_no_difference_ci_covers_zero(self) -> None:
        rng = np.random.default_rng(0)
        per_seed = []
        for _ in range(5):
            a = {r: int(v) for r, v in zip(HLD, rng.integers(1, 16, 10))}
            b = {r: int(v) for r, v in zip(HLD, rng.integers(1, 16, 10))}
            per_seed.append({"A": a, "B": b})
        out = paired_speed_stats(per_seed, heldout_ids=HLD)
        assert out["ci95"][0] <= out["mean"] <= out["ci95"][1]

    def test_not_found_counts_as_rounds_plus_one(self) -> None:
        # 未发现记 R+1 = 16:臂 B 全部未发现,臂 A 全部第 1 轮发现
        per_seed = [{"A": {r: 1 for r in HLD}, "B": {r: 16 for r in HLD}}]
        out = paired_speed_stats(per_seed, heldout_ids=HLD)
        assert out["mean"] == pytest.approx(15.0)


class TestPairedFpStats:
    def test_lower_fp_gives_negative_ci(self) -> None:
        out = paired_fp_stats([(2, 8), (3, 9), (1, 7), (4, 10), (2, 6)])
        assert out["mean"] < 0
        assert out["ci95"][1] < 0


class TestReadaptation:
    def _rounds(self, aucs: list[float]) -> list[dict]:
        return [{"round": i + 1, "auc_after": a} for i, a in enumerate(aucs)]

    def test_recovery_rounds(self) -> None:
        # dev=60, R=15:切换在 episode 20 → r_s = 20*15//60 + 1 = 6
        # 第 6 轮 AUC 跌,第 8 轮恢复到 pre(第 5 轮)- tol 之内 → 恢复 2 轮
        recs = self._rounds([0.70, 0.71, 0.72, 0.73, 0.70,
                             0.60, 0.65, 0.71, 0.72, 0.72,
                             0.72, 0.72, 0.72, 0.72, 0.72])
        out = readaptation(recs, {}, [20], dev_episodes=60, rounds=15, tol=0.005)
        assert out["per_switch"][0]["switch_round"] == 6
        assert out["per_switch"][0]["recovery_rounds"] == 2

    def test_no_recovery_counts_remaining_rounds(self) -> None:
        recs = self._rounds([0.72] * 5 + [0.60] * 10)
        out = readaptation(recs, {}, [20], dev_episodes=60, rounds=15, tol=0.005)
        assert out["per_switch"][0]["recovery_rounds"] == 15 - 6

    def test_new_discoveries_within_3_rounds(self) -> None:
        recs = self._rounds([0.7] * 15)
        disc = {"HLD-01": 7, "HLD-02": 9, "HLD-03": 11,  # 7,8,9 在窗内;11 在外
                "EXP-01": 8}
        out = readaptation(recs, disc, [20], dev_episodes=60, rounds=15, tol=0.005)
        assert out["per_switch"][0]["new_discoveries_3r"] == 3  # HLD-01/02 + EXP-01

    def test_switch_outside_dev_or_too_early_skipped(self) -> None:
        recs = self._rounds([0.7] * 15)
        out = readaptation(recs, {}, [0, 2, 99], dev_episodes=60, rounds=15)
        assert out["per_switch"] == []
        assert math.isnan(out["mean_recovery_rounds"])


def _tiny_cfg(tmp_path: Path, **kw) -> ABConfig:
    base = dict(
        seeds=(42,), episodes=10, per_episode=150, dev_episodes=8,
        rounds=2, top_m=4, top_k=10, dev_holdout_episodes=1,
        retire_k=2, n_null=20,
        lgbm_params={"n_estimators": 20, "num_leaves": 7,
                     "min_child_samples": 10, "learning_rate": 0.1,
                     "verbose": -1, "n_jobs": 1},
        out_dir=tmp_path,
    )
    base.update(kw)
    return ABConfig(**base)


class TestTinyPairEndToEnd:
    def test_both_arms_run_and_structural_equality(self, tmp_path) -> None:
        m = run_experiment(_tiny_cfg(tmp_path))

        assert len(m["seeds"]) == 1
        s = m["seeds"][0]
        a, b = s["arm_A"], s["arm_B"]
        # 结构等式:同种子/同候选表/同 tau;唯一变量 = memory 标志
        assert s["seed"] == 42
        assert a["memory"] is True and b["memory"] is False
        assert s["n_candidates"] > 0 and s["tau"] > 0
        # 提案量相等(同 top_m × 同轮数)
        assert a["total_proposals"] == b["total_proposals"] == 2 * 4
        # 死路机制的直接证据:臂 A 重复提案 = 0
        assert a["repeat_proposals"] == 0
        assert a["repeat_rate"] == 0.0
        # 判卷覆盖 30 条规则(未发现记 rounds+1)
        assert len(a["discovery_rounds"]) == 30
        assert len(b["discovery_rounds"]) == 30
        for dr in (*a["discovery_rounds"].values(), *b["discovery_rounds"].values()):
            assert 1 <= dr <= 3
        # 配对统计与判据结构
        assert m["speed_paired"]["n"] == 10
        assert "ci95" in m["speed_paired"] and "ci95" in m["fp_paired"]
        assert m["verdict"] in ("PASS", "FAIL")
        # 产物
        assert (tmp_path / "clab_ab_metrics.json").exists()
        assert (tmp_path / "clab_ab_curves.png").exists()
        loaded = json.loads((tmp_path / "clab_ab_metrics.json").read_text())
        assert loaded["verdict"] == m["verdict"]
