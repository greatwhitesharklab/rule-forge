"""Curve math for the P1 acceptance experiment (design doc §5).

Operational definitions of the four required curves:

  N(n)  dividend curve: per-episode decision profit of the system arm minus
        the RAG arm, cumulatively summed. Profit matrix:
          correct approve  +1.0  (loan performs; one unit of margin)
          wrong approve    -5.0  (default; loss given default dwarfs margin,
                                  ~5:1 is a standard unsecured-lending ratio)
          correct reject   +0.2  (funds redeployed to a safe asset instead)
          wrong reject      0.0  (opportunity cost only, not booked)
          review            0.0  (abstention: neither booked nor lost)
        Cases whose outcome is still unmatured at the horizon contribute 0.
  ZS(t) portfolio zero-shot: for each regime switch s, the mean per-episode
        AUC of the arm's final P(bad) over the first `window` episodes of the
        new regime — how fast decision quality recovers after a break.
  L(t)  damage retention: on a FIXED regime-0 replay set, AUC of the
        memory-derived score after each night, divided by its post-warmup
        value. A frozen memory is flat at 1.0 by construction; the writable
        arm shows whether nightly writes erode old-regime knowledge.
  reputation convergence: per night, over slots whose canonical profile has
        enough world data, |rep_bad - empirical bad rate| (MAE) and the
        direction agreement rate against the global bad rate.

The verdict: PASS iff the mean paired ZS difference (system - RAG) is
positive AND its bootstrap 95% CI lies entirely above zero.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

import numpy as np
from sklearn.metrics import roc_auc_score

# Profit matrix (see module docstring for the rationale).
PROFIT_CORRECT_APPROVE = 1.0
PROFIT_WRONG_APPROVE = -5.0
PROFIT_CORRECT_REJECT = 0.2


def decision_profit(decisions: np.ndarray, outcomes: np.ndarray) -> np.ndarray:
    """Per-case profit; outcomes are float with NaN = unmatured (-> 0)."""
    dec = np.asarray(decisions)
    out = np.asarray(outcomes, dtype=np.float64)
    profit = np.zeros(len(dec), dtype=np.float64)
    bad = out == 1.0
    good = out == 0.0
    profit[(dec == "approve") & good] = PROFIT_CORRECT_APPROVE
    profit[(dec == "approve") & bad] = PROFIT_WRONG_APPROVE
    profit[(dec == "reject") & bad] = PROFIT_CORRECT_REJECT
    return profit


def dividend_curve(sys_profit: np.ndarray, ctl_profit: np.ndarray) -> np.ndarray:
    """Cumulative per-episode profit difference (system - control)."""
    return np.cumsum(np.asarray(sys_profit) - np.asarray(ctl_profit))


def _safe_auc(y: np.ndarray, p: np.ndarray) -> float | None:
    mask = np.isfinite(p)
    y, p = y[mask], p[mask]
    if len(y) < 2 or len(np.unique(y)) < 2 or len(np.unique(p)) < 2:
        return None
    return float(roc_auc_score(y, p))


def zero_shot_auc(
    proba_by_ep: Mapping[int, np.ndarray],
    labels_by_ep: Mapping[int, np.ndarray],
    switch_episodes: list[int],
    window: int = 3,
) -> np.ndarray:
    """Per-switch mean AUC over the first `window` episodes of each new regime.

    Single-class (or constant-score) episodes are skipped; a switch with no
    usable episode is dropped from the result.
    """
    values: list[float] = []
    for s in switch_episodes:
        aucs = [
            a
            for ep in range(s, s + window)
            if ep in proba_by_ep
            for a in [_safe_auc(labels_by_ep[ep], proba_by_ep[ep])]
            if a is not None
        ]
        if aucs:
            values.append(float(np.mean(aucs)))
    return np.asarray(values, dtype=np.float64)


@dataclass(frozen=True)
class Alignment:
    """Reputation-vs-truth agreement for one night."""

    mae: float  # mean |rep_bad - empirical bad rate|
    direction: float  # fraction of slots leaning the same way as truth
    n: int  # slots that entered the comparison


def reputation_alignment(
    slots: list,
    slot_profiles: Mapping[int, str],
    truth: Mapping[tuple[str, str], tuple[float, int]],
    global_rate: float,
    min_count: int = 5,
) -> Alignment:
    """Compare slot bad-reputations with the world ground truth.

    ``truth`` maps (canonical_text, regime_tag) -> (empirical bad rate, n);
    only profiles with n >= min_count enter. Direction agreement compares
    each side's lean against ``global_rate``.
    """
    errors: list[float] = []
    agree = 0
    for slot in slots:
        profile = slot_profiles.get(slot.slot_id)
        if profile is None:
            continue
        entry = truth.get((profile, slot.regime_tag))
        if entry is None or entry[1] < min_count:
            continue
        rate, _n = entry
        rep_bad = slot.beta_b / (slot.beta_a + slot.beta_b)
        errors.append(abs(rep_bad - rate))
        if (rep_bad >= global_rate) == (rate >= global_rate):
            agree += 1
    if not errors:
        return Alignment(mae=float("nan"), direction=float("nan"), n=0)
    return Alignment(
        mae=float(np.mean(errors)), direction=agree / len(errors), n=len(errors)
    )


def bootstrap_ci(
    diffs: np.ndarray,
    *,
    n_boot: int = 10000,
    seed: int = 20260726,
    alpha: float = 0.05,
) -> tuple[float, float, float]:
    """Percentile bootstrap CI of the mean paired difference."""
    diffs = np.asarray(diffs, dtype=np.float64)
    if diffs.size == 0:
        return float("nan"), float("nan"), float("nan")
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, diffs.size, size=(n_boot, diffs.size))
    means = diffs[idx].mean(axis=1)
    lo, hi = np.percentile(means, [100 * alpha / 2, 100 * (1 - alpha / 2)])
    return float(diffs.mean()), float(lo), float(hi)
