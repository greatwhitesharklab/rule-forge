"""Feature-expression verifier (design doc §3.1 row 1, §8.4 admission gate).

Pipeline: AST whitelist + forked sandbox (§3.2) -> backtest metrics on
historical (or injected) data -> leak detection -> gate.

Gate (§8.4): IV > 0.1 OR lift > 1.3, AND coverage > 5%. A feature whose
direction-free AUC exceeds ``leak_auc`` is not rejected outright — it is
quarantined for human review, since near-perfect single-feature prediction
almost always means outcome leakage (时间穿越, §8.3).

Quality score Q (for the cloud reputation ledger):
  pass        -> 0.5 + 0.5 * strength
  fail (ran)  -> 0.2 * strength
  fail (sandbox/timeout/crash) -> 0.0
  quarantine  -> 0.0 until the review clears it
where strength scales IV against 0.3 ("strong") and lift against 1.6.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import TYPE_CHECKING

import numpy as np
import pandas as pd

from .metrics import coverage, direction_free_auc, information_value, lift
from .sandbox import run_expression
from .verdict import FAIL, PASS, QUARANTINE, Verdict

if TYPE_CHECKING:
    from synth.world import WorldData

_REFERENCE_IV_STRONG = 0.3
_REFERENCE_LIFT_STRONG = 1.6


@dataclass(frozen=True)
class FeatureThresholds:
    """§8.4 admission thresholds; all comparisons are strict ('>')."""

    iv_min: float = 0.1
    lift_min: float = 1.3
    coverage_min: float = 0.05
    leak_auc: float = 0.95
    timeout_s: float = 30.0
    memory_mb: int = 512


def _strength(iv: float, lf: float) -> float:
    """How far past 'weak' the feature is, in [0,1]; NaN-safe."""
    iv_term = min(max(iv, 0.0) / _REFERENCE_IV_STRONG, 1.0) if math.isfinite(iv) else 0.0
    lift_term = (
        min(max(lf - 1.0, 0.0) / (_REFERENCE_LIFT_STRONG - 1.0), 1.0)
        if math.isfinite(lf)
        else 0.0
    )
    return max(iv_term, lift_term)


def verify_feature(
    expression: str,
    df: pd.DataFrame,
    labels: np.ndarray,
    *,
    thresholds: FeatureThresholds | None = None,
) -> Verdict:
    """Sandbox-execute ``expression`` on ``df`` and judge it against the gate."""
    th = thresholds or FeatureThresholds()
    run = run_expression(expression, df, timeout_s=th.timeout_s, memory_mb=th.memory_mb)
    if not run.ok:
        return Verdict(FAIL, 0.0, (f"sandbox: {run.error}",))

    values = run.values
    assert values is not None
    cov = coverage(values)
    iv = information_value(values, labels)
    lf = lift(values, labels)
    auc = direction_free_auc(values, labels)
    metrics = {"iv": iv, "lift": lf, "coverage": cov, "auc": auc}
    strength = _strength(iv, lf)

    if math.isfinite(auc) and auc > th.leak_auc:
        return Verdict(
            QUARANTINE,
            0.0,
            (f"leak suspect: direction-free AUC {auc:.4f} > {th.leak_auc}; "
             "isolated for human review",),
            metrics,
        )
    if cov <= th.coverage_min:
        return Verdict(
            FAIL, 0.2 * strength,
            (f"coverage {cov:.4f} <= {th.coverage_min}",), metrics,
        )
    if iv > th.iv_min or lf > th.lift_min:
        return Verdict(
            PASS, 0.5 + 0.5 * strength,
            (f"admitted: iv={iv:.4f} lift={lf:.4f} coverage={cov:.4f}",), metrics,
        )
    return Verdict(
        FAIL, 0.2 * strength,
        (f"below gate: iv {iv:.4f} <= {th.iv_min} and lift {lf:.4f} <= {th.lift_min}",),
        metrics,
    )


def backtest_frame(world: WorldData, episode: int) -> tuple[pd.DataFrame, np.ndarray]:
    """Time-safe backtest frame from a synth world (§8.3 as-of red-line).

    Uses ``WorldData.matured_view`` — the only supported join — so a feature
    is always evaluated against outcomes visible by ``episode`` and can never
    see an unmatured label.
    """
    obs, labels = world.matured_view(episode)
    df = pd.DataFrame(obs, columns=list(world.casebook.observable_names))
    return df, labels
