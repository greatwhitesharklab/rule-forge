"""Backtest metrics for feature verification (design doc §3.1 / §8.4).

Label convention follows the synth OutcomeLedger: 1 = bad (default), 0 = good.

- IV (information value): quantile-binned WOE sum; zero cells get a +0.5
  smoothing so IV stays finite, untouched otherwise.
- lift: the highest per-bin bad rate divided by the overall bad rate.
- coverage: fraction of finite (non-NaN, non-inf) feature values.
- direction-free AUC: max(auc, 1-auc) so a reversed oracle is still flagged.
"""

from __future__ import annotations

import math

import numpy as np
import pandas as pd


def _prepare(values: np.ndarray, labels: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Drop rows with non-finite feature values; return (values, labels)."""
    v = np.asarray(values, dtype=np.float64)
    y = np.asarray(labels).astype(np.int8)
    mask = np.isfinite(v)
    return v[mask], y[mask]


def coverage(values: np.ndarray) -> float:
    """Fraction of finite values in the raw feature output."""
    v = np.asarray(values, dtype=np.float64)
    if v.size == 0:
        return 0.0
    return float(np.isfinite(v).mean())


def _bins(v: np.ndarray, max_bins: int) -> pd.Series:
    """Exact-value bins when few uniques, else quantile bins."""
    s = pd.Series(v)
    if s.nunique() <= max_bins:
        return s
    return pd.qcut(s, max_bins, duplicates="drop")


def information_value(
    values: np.ndarray, labels: np.ndarray, *, max_bins: int = 10
) -> float:
    """IV = sum over bins of (dist_bad - dist_good) * ln(dist_bad / dist_good)."""
    v, y = _prepare(values, labels)
    if v.size == 0:
        return 0.0
    grouped = pd.DataFrame({"b": _bins(v, max_bins), "y": y}).groupby("b", observed=True)["y"]
    bad = grouped.sum().astype(float)
    good = grouped.count().astype(float) - bad
    if bad.sum() == 0 or good.sum() == 0:
        return 0.0  # single-class sample carries no discrimination signal
    zero = (bad == 0) | (good == 0)
    bad.loc[zero] += 0.5  # smoothing only where a cell is empty
    good.loc[zero] += 0.5
    dist_bad = bad / bad.sum()
    dist_good = good / good.sum()
    woe = np.log(dist_bad / dist_good)
    return float(((dist_bad - dist_good) * woe).sum())


def lift(values: np.ndarray, labels: np.ndarray, *, max_bins: int = 10) -> float:
    """Best-bin bad rate / overall bad rate; NaN when no bads in the sample."""
    v, y = _prepare(values, labels)
    if v.size == 0 or y.sum() == 0:
        return float("nan")
    grouped = pd.DataFrame({"b": _bins(v, max_bins), "y": y}).groupby("b", observed=True)["y"]
    bin_rates = grouped.sum() / grouped.count()
    return float((bin_rates / y.mean()).max())


def direction_free_auc(values: np.ndarray, labels: np.ndarray) -> float:
    """Rank-based AUC folded to [0.5, 1]; NaN when the sample is single-class."""
    v, y = _prepare(values, labels)
    n_pos = int(y.sum())
    n_neg = int(v.size) - n_pos
    if n_pos == 0 or n_neg == 0:
        return float("nan")
    ranks = pd.Series(v).rank().to_numpy()  # average ranks for ties
    auc = (ranks[y == 1].sum() - n_pos * (n_pos + 1) / 2.0) / (n_pos * n_neg)
    result = max(float(auc), 1.0 - float(auc))
    return result if math.isfinite(result) else float("nan")
