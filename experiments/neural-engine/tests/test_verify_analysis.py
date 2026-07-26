"""Case-analysis verifier tests (design doc §3.1 row 2): conclusion x outcome."""

import pytest

from verify.analysis import verify_analysis


class TestDirectionScores:
    @pytest.mark.parametrize(
        "conclusion, outcome, score, expected_status",
        [
            ("approve", "good", 1.0, "pass"),
            ("approve", "bad", 0.0, "fail"),
            ("reject", "bad", 1.0, "pass"),
            ("reject", "good", 0.0, "fail"),
            ("review", "good", 0.5, "fail"),  # half credit, below 0.6
            ("review", "bad", 0.5, "fail"),
        ],
    )
    def test_all_direction_combinations(
        self, conclusion: str, outcome: str, score: float, expected_status: str
    ) -> None:
        v = verify_analysis([{"conclusion": conclusion, "outcome": outcome}])
        assert v.quality == score
        assert v.status == expected_status
        assert v.metrics["n"] == 1

    def test_review_counts_as_half_credit(self) -> None:
        records = [{"conclusion": "review", "outcome": "good"}] * 4
        v = verify_analysis(records, threshold=0.4)
        assert v.metrics["agreement"] == pytest.approx(0.5)
        assert v.status == "pass"

    def test_aggregate_against_threshold(self) -> None:
        records = [
            {"conclusion": "approve", "outcome": "good"},
            {"conclusion": "reject", "outcome": "bad"},
            {"conclusion": "approve", "outcome": "bad"},
            {"conclusion": "review", "outcome": "good"},
        ]
        # scores: 1 + 1 + 0 + 0.5 -> agreement 0.625
        assert verify_analysis(records, threshold=0.6).status == "pass"
        assert verify_analysis(records, threshold=0.7).status == "fail"

    def test_threshold_boundary_is_strict(self) -> None:
        records = [{"conclusion": "review", "outcome": "good"}] * 2
        v = verify_analysis(records, threshold=0.5)  # agreement == threshold
        assert v.status == "fail"  # gate is strict ">"

    def test_int_outcomes_accepted(self) -> None:
        # synth ledger convention: 1 = bad, 0 = good.
        v = verify_analysis([{"conclusion": "approve", "outcome": 0}])
        assert v.quality == 1.0
        v = verify_analysis([{"conclusion": "approve", "outcome": 1}])
        assert v.quality == 0.0

    def test_empty_records_fail(self) -> None:
        v = verify_analysis([])
        assert v.status == "fail"
        assert v.quality == 0.0
        assert v.metrics["n"] == 0

    def test_unknown_conclusion_raises(self) -> None:
        with pytest.raises(ValueError):
            verify_analysis([{"conclusion": "maybe", "outcome": "good"}])

    def test_unknown_outcome_raises(self) -> None:
        with pytest.raises(ValueError):
            verify_analysis([{"conclusion": "approve", "outcome": "unknown"}])
