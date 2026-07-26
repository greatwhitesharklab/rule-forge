"""Curve math tests (design doc §5 P1 four curves).

Small constructed datasets with hand-computable answers: the profit matrix,
the cumulative dividend curve, portfolio zero-shot AUC, reputation alignment,
and the bootstrap interval used for the PASS/FAIL verdict.
"""

from __future__ import annotations

from types import SimpleNamespace

import numpy as np

from eval.curves import (
    bootstrap_ci,
    decision_profit,
    dividend_curve,
    reputation_alignment,
    zero_shot_auc,
)


def test_decision_profit_matrix():
    decisions = np.array(["approve", "approve", "reject", "reject", "review"])
    outcomes = np.array([0.0, 1.0, 1.0, 0.0, 0.0])  # 1 = bad
    profit = decision_profit(decisions, outcomes)
    # correct approve +1, wrong approve -5, correct reject +0.2,
    # wrong reject 0, review 0.
    assert np.allclose(profit, [1.0, -5.0, 0.2, 0.0, 0.0])


def test_decision_profit_unmatured_is_zero():
    decisions = np.array(["approve", "reject"])
    outcomes = np.array([np.nan, np.nan])  # labels not yet visible
    assert np.allclose(decision_profit(decisions, outcomes), [0.0, 0.0])


def test_dividend_curve_cumulative_difference():
    sys_profit = np.array([2.0, 5.0, -1.0])
    ctl_profit = np.array([1.0, 5.0, 0.0])
    assert np.allclose(dividend_curve(sys_profit, ctl_profit), [1.0, 1.0, 0.0])


def test_zero_shot_auc_perfect_and_single_class_skip():
    proba = {
        5: np.array([0.1, 0.9, 0.2, 0.8]),
        6: np.array([0.7, 0.6, 0.4, 0.3]),
        7: np.array([0.5, 0.5, 0.5, 0.5]),
    }
    labels = {
        5: np.array([0, 1, 0, 1]),  # AUC = 1.0
        6: np.array([1, 1, 0, 0]),  # AUC = 1.0
        7: np.array([1, 1, 1, 1]),  # single class -> skipped
    }
    zs = zero_shot_auc(proba, labels, switch_episodes=[5], window=3)
    assert zs.shape == (1,)
    assert zs[0] == 1.0  # mean of episodes 5 and 6; episode 7 skipped


def test_zero_shot_auc_ordering_sensitivity():
    proba = {2: np.array([0.9, 0.1, 0.8, 0.2])}
    labels = {2: np.array([0, 1, 0, 1])}  # perfectly reversed -> AUC 0.0
    zs = zero_shot_auc(proba, labels, switch_episodes=[2], window=1)
    assert zs[0] == 0.0


def test_reputation_alignment_direction_and_mae():
    slots = [
        SimpleNamespace(slot_id=1, beta_a=1.0, beta_b=3.0, regime_tag="R00"),
        SimpleNamespace(slot_id=2, beta_a=4.0, beta_b=1.0, regime_tag="R00"),
        SimpleNamespace(slot_id=3, beta_a=1.0, beta_b=1.0, regime_tag="R00"),
    ]
    slot_profiles = {1: "profileA", 2: "profileB", 3: "profileC"}
    truth = {
        ("profileA", "R00"): (0.8, 50),  # rep_bad 0.75 vs truth 0.8 -> agree, mae .05
        ("profileB", "R00"): (0.1, 50),  # rep_bad 0.20 vs truth 0.1 -> agree, mae .10
        ("profileC", "R00"): (0.5, 3),  # below min_count -> skipped
    }
    al = reputation_alignment(slots, slot_profiles, truth, global_rate=0.3,
                              min_count=5)
    assert al.n == 2
    assert al.direction == 1.0
    assert abs(al.mae - (0.05 + 0.10) / 2) < 1e-12


def test_reputation_alignment_detects_disagreement():
    slots = [SimpleNamespace(slot_id=1, beta_a=1.0, beta_b=4.0, regime_tag="R00")]
    truth = {("p", "R00"): (0.1, 20)}  # slot leans bad (0.8), truth leans good
    al = reputation_alignment(slots, {1: "p"}, truth, global_rate=0.3)
    assert al.direction == 0.0
    assert abs(al.mae - 0.7) < 1e-12


def test_bootstrap_ci_excludes_zero_for_consistent_diffs():
    diffs = np.linspace(0.02, 0.08, 10)
    mean, lo, hi = bootstrap_ci(diffs, n_boot=2000, seed=1)
    assert mean == np.mean(diffs)
    assert lo > 0.0
    assert hi <= max(diffs) + 1e-9


def test_bootstrap_ci_straddles_zero_for_symmetric_diffs():
    diffs = np.array([-0.05, 0.05] * 5)
    mean, lo, hi = bootstrap_ci(diffs, n_boot=2000, seed=1)
    assert mean == 0.0
    assert lo < 0.0 < hi
