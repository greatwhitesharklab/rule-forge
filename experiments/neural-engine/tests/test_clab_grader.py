"""CLAB 发现力判卷器测试:命中/方向反/假阳性/阈值边界 + 阈值标定合理性。

判卷器是机制一实验的测量侧:唯一允许读 ground truth 规则池的组件。
判定契约:
  重新发现 = 过验证门槛的特征与某【保留池】规则 fire 相关 > tau 且方向一致;
  已知发现 = 最佳命中落在【经验池】规则;
  假阳性   = 与全部 30 条规则 fire 相关 <= tau(或方向不一致)。
"""

from __future__ import annotations

import numpy as np
import pytest

from eval.clab_discovery import (
    RuleFire,
    RuleGrader,
    calibrate_threshold,
    pointbiserial,
)


def _synthetic_pool(n: int = 4000, seed: int = 7):
    """12 条独立潜变量尾部规则(8 经验 + 4 保留),结局由 fire 加性驱动。"""
    rng = np.random.default_rng(seed)
    zs = rng.normal(size=(12, n))
    fires = []
    logit = np.full(n, -1.0)
    for i in range(8):
        fire = zs[i] > 1.4
        w = 0.5 if i % 2 == 0 else -0.4
        fires.append(RuleFire(f"EXP-{i+1:02d}", "experience", w, fire))
        logit = logit + w * 3.0 * fire
    for j in range(4):
        fire = zs[8 + j] < -1.4
        w = 0.5 if j % 2 == 0 else -0.4
        fires.append(RuleFire(f"HLD-{j+1:02d}", "heldout", w, fire))
        logit = logit + w * 3.0 * fire
    logit = logit + rng.normal(0.0, 0.3, n)
    y = (rng.random(n) < 1.0 / (1.0 + np.exp(-logit))).astype(np.int8)
    return tuple(fires), y, rng


def _noisy_proxy(fire: np.ndarray, rng: np.random.Generator, flip_p: float = 0.05):
    """理想特征:fire 指示变量加比特翻转噪声(已知命中某规则的候选)。"""
    return (fire ^ (rng.random(len(fire)) < flip_p)).astype(np.float64)


class TestPointbiserial:
    def test_perfect_agreement(self) -> None:
        fire = np.array([True, False, True, False] * 100)
        assert pointbiserial(fire.astype(float), fire) == pytest.approx(1.0)

    def test_independent_is_near_zero(self) -> None:
        rng = np.random.default_rng(0)
        fire = rng.random(5000) < 0.1
        noise = rng.normal(size=5000)
        assert abs(pointbiserial(noise, fire)) < 0.05

    def test_constant_feature_is_nan_safe(self) -> None:
        fire = np.array([True, False] * 50)
        assert not np.isfinite(pointbiserial(np.ones(100), fire))


class TestRuleGrader:
    def test_known_discovery_hits_experience_rule(self) -> None:
        fires, y, rng = _synthetic_pool()
        grader = RuleGrader(fires, y, tau=0.20)
        proxy = _noisy_proxy(fires[0].fire, rng)  # EXP-01, w=+0.5
        res = grader.grade("feat_exp01", proxy)
        assert res.category == "known"
        assert res.best_rule_id == "EXP-01"
        assert res.direction_ok
        assert res.best_corr > 0.20

    def test_rediscovery_hits_heldout_rule(self) -> None:
        fires, y, rng = _synthetic_pool()
        grader = RuleGrader(fires, y, tau=0.20)
        proxy = _noisy_proxy(fires[8].fire, rng)  # HLD-01, w=+0.5
        res = grader.grade("feat_hld01", proxy)
        assert res.category == "rediscovery"
        assert res.best_rule_id == "HLD-01"
        assert res.direction_ok

    def test_negative_weight_rule_direction(self) -> None:
        fires, y, rng = _synthetic_pool()
        grader = RuleGrader(fires, y, tau=0.20)
        proxy = _noisy_proxy(fires[9].fire, rng)  # HLD-02, w=-0.4
        res = grader.grade("feat_hld02", proxy)
        assert res.category == "rediscovery"
        assert res.best_rule_id == "HLD-02"

    def test_false_positive_when_below_threshold(self) -> None:
        fires, y, rng = _synthetic_pool()
        grader = RuleGrader(fires, y, tau=0.20)
        noise = rng.normal(size=len(y))
        res = grader.grade("feat_noise", noise)
        assert res.category == "false_positive"
        assert res.best_rule_id is None

    def test_direction_mismatch_is_not_a_discovery(self) -> None:
        fires, y, rng = _synthetic_pool()
        proxy = _noisy_proxy(fires[0].fire, rng)  # 与 EXP-01(w>0)正相关
        # 构造与特征负相关的标签:方向一致性必须否决这次命中
        y_flip = (proxy < np.median(proxy)).astype(np.int8)
        grader = RuleGrader(fires, y_flip, tau=0.20)
        res = grader.grade("feat_wrong_dir", proxy)
        assert res.category == "false_positive"
        assert not res.direction_ok
        assert "direction" in res.note

    def test_threshold_is_strict(self) -> None:
        fires, y, rng = _synthetic_pool()
        proxy = _noisy_proxy(fires[0].fire, rng)
        c = pointbiserial(proxy, fires[0].fire)
        assert c > 0
        at = RuleGrader(fires, y, tau=c)  # 相关恰好 == tau:严格 > 不过线
        assert at.grade("feat_edge", proxy).category == "false_positive"
        below = RuleGrader(fires, y, tau=c - 1e-9)
        assert below.grade("feat_edge", proxy).category == "known"

    def test_constant_feature_is_false_positive(self) -> None:
        fires, y, _ = _synthetic_pool()
        grader = RuleGrader(fires, y, tau=0.20)
        res = grader.grade("feat_const", np.ones(len(y)))
        assert res.category == "false_positive"


class TestThresholdCalibration:
    def test_hit_side_above_null_side(self) -> None:
        fires, y, _ = _synthetic_pool(n=6000)
        cal = calibrate_threshold(fires, y, seed=11, n_null=60)
        # 理想特征(命中经验规则)的相关分布必须整体高于标定阈值
        assert cal.hit_median > cal.tau
        assert cal.hit_min > cal.null_p99 * 0.5  # 命中侧与零侧可区分
        # tau 取自零侧分布高端(保守)
        assert cal.tau >= cal.null_p99 - 1e-12
        assert cal.tau >= 0.05

    def test_ideal_proxies_mostly_pass_gate(self) -> None:
        fires, y, _ = _synthetic_pool(n=6000)
        cal = calibrate_threshold(fires, y, seed=11, n_null=20)
        # §8.4 门槛是坏样本富集取向(lift 只测 bad 富集方向):强正权重规则
        # 的理想特征必须过门;稀有保护性规则(prevalence 低、降低 bad 率)
        # 难过门 —— 这是门槛的真实不对称性,标定要如实报告
        assert cal.hit_gate_pass >= 3
        assert cal.hit_gate_pass <= cal.n_hit

    def test_deterministic_same_seed(self) -> None:
        fires, y, _ = _synthetic_pool(n=3000)
        a = calibrate_threshold(fires, y, seed=5, n_null=20)
        b = calibrate_threshold(fires, y, seed=5, n_null=20)
        assert a.tau == b.tau

    def test_as_dict_roundtrip(self) -> None:
        fires, y, _ = _synthetic_pool(n=3000)
        cal = calibrate_threshold(fires, y, seed=5, n_null=20)
        d = cal.as_dict()
        for key in ("tau", "hit_min", "hit_median", "null_p99", "null_max",
                    "n_hit", "n_null", "hit_gate_pass"):
            assert key in d
