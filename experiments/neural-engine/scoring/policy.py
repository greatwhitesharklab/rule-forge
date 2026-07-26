"""Three-way decision policy: approve / review / reject (P1 backbone).

This is the decision interface used by the later "system vs pure RAG"
contrast experiment: a probability plus a threshold pair yields a per-case
decision. Cases without a score (cold start, NaN) are routed to review —
never auto-decided.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class Policy:
    """P(bad) thresholds: <= approve_threshold -> approve,
    >= reject_threshold -> reject, otherwise human review."""

    approve_threshold: float = 0.10
    reject_threshold: float = 0.35

    def __post_init__(self) -> None:
        if not (0.0 <= self.approve_threshold <= self.reject_threshold <= 1.0):
            raise ValueError(
                "require 0 <= approve_threshold <= reject_threshold <= 1, "
                f"got ({self.approve_threshold}, {self.reject_threshold})"
            )


def decide(proba: np.ndarray, policy: Policy | None = None) -> np.ndarray:
    """Map P(bad) per case to {"approve", "review", "reject"}.

    NaN scores (cold start) map to "review": the system abstains instead of
    guessing.
    """
    pol = policy or Policy()
    p = np.asarray(proba, dtype=np.float64)
    out = np.full(p.shape, "review", dtype="<U7")
    valid = ~np.isnan(p)
    out[valid & (p <= pol.approve_threshold)] = "approve"
    out[valid & (p >= pol.reject_threshold)] = "reject"
    return out
