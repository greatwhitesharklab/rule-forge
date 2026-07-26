"""Shared verdict type for the three local verifiers (design doc §3.1).

Every cloud output is a *candidate* until a local verifier accepts it. Each
verdict carries a quality score Q ∈ [0,1] destined for the reputation ledger.
This module only produces the judgement and Q; wiring Q back into the P0
ledger/slots bookkeeping is a later task.
"""

from __future__ import annotations

from dataclasses import dataclass, field

PASS = "pass"
FAIL = "fail"
QUARANTINE = "quarantine"
STATUSES = (PASS, FAIL, QUARANTINE)


@dataclass(frozen=True)
class Verdict:
    """Structured judgement: status + quality score + auditable reasons."""

    status: str
    quality: float  # Q in [0,1], for the cloud reputation ledger
    reasons: tuple[str, ...] = ()
    metrics: dict[str, float] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.status not in STATUSES:
            raise ValueError(f"unknown verdict status {self.status!r}")
        if not 0.0 <= self.quality <= 1.0:
            raise ValueError(f"quality must be in [0,1], got {self.quality}")
