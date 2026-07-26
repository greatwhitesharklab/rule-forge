"""Case-analysis verifier (design doc §3.1 row 2).

Scores cloud case-analysis conclusions against matured outcome labels:
approve <-> good, reject <-> bad, review counts as half credit either way.
The verdict passes when the direction-agreement ratio strictly exceeds the
configurable threshold (default 0.6). Q equals the agreement ratio itself.
"""

from __future__ import annotations

from typing import Any, Mapping, Sequence

import numpy as np

from .verdict import FAIL, PASS, Verdict

_CONCLUSIONS = ("approve", "reject", "review")
_DIRECTION_SCORE = {
    ("approve", "good"): 1.0,
    ("approve", "bad"): 0.0,
    ("reject", "bad"): 1.0,
    ("reject", "good"): 0.0,
    ("review", "good"): 0.5,
    ("review", "bad"): 0.5,
}


def _norm_outcome(value: Any) -> str:
    """Accept 'good'/'bad' or the synth ledger convention 0=good / 1=bad."""
    if value in ("good", "bad"):
        return str(value)
    if isinstance(value, (int, np.integer)) and int(value) in (0, 1):
        return "bad" if int(value) == 1 else "good"
    raise ValueError(f"unknown outcome {value!r}; expected 'good'/'bad'/0/1")


def verify_analysis(
    records: Sequence[Mapping[str, Any]],
    *,
    threshold: float = 0.6,
) -> Verdict:
    """Score [{conclusion, outcome}, ...]; pass iff agreement > threshold."""
    if not records:
        return Verdict(FAIL, 0.0, ("no records to score",), {"n": 0, "agreement": 0.0})
    scores: list[float] = []
    for i, rec in enumerate(records):
        conclusion = rec.get("conclusion")
        if conclusion not in _CONCLUSIONS:
            raise ValueError(f"records[{i}]: unknown conclusion {conclusion!r}")
        outcome = _norm_outcome(rec.get("outcome"))
        scores.append(_DIRECTION_SCORE[(str(conclusion), outcome)])
    agreement = sum(scores) / len(scores)
    status = PASS if agreement > threshold else FAIL
    reason = f"agreement {agreement:.3f} vs threshold {threshold:.3f} over {len(scores)} cases"
    return Verdict(status, agreement, (reason,), {"n": len(scores), "agreement": agreement})
