"""Three-way decision policy tests (approve / review / reject backbone)."""

from __future__ import annotations

import numpy as np
import pytest

from scoring import Policy, decide


class TestPolicyValidation:
    def test_approve_above_reject_rejected(self) -> None:
        with pytest.raises(ValueError):
            Policy(approve_threshold=0.5, reject_threshold=0.3)

    def test_out_of_range_rejected(self) -> None:
        with pytest.raises(ValueError):
            Policy(approve_threshold=-0.1, reject_threshold=0.5)
        with pytest.raises(ValueError):
            Policy(approve_threshold=0.1, reject_threshold=1.5)


class TestDecideBoundaries:
    def test_three_way_boundaries(self) -> None:
        policy = Policy(approve_threshold=0.10, reject_threshold=0.35)
        proba = np.array([0.05, 0.10, 0.20, 0.349, 0.35, 0.90])
        out = decide(proba, policy)
        assert list(out) == ["approve", "approve", "review", "review",
                             "reject", "reject"]

    def test_nan_goes_to_review(self) -> None:
        # cold-start scores (NaN) must never be auto-approved or auto-rejected
        out = decide(np.array([np.nan, 0.0, 1.0]), Policy())
        assert out[0] == "review"
        assert out[1] == "approve"
        assert out[2] == "reject"

    def test_default_policy(self) -> None:
        out = decide(np.array([0.5]))
        assert out[0] in ("approve", "review", "reject")
