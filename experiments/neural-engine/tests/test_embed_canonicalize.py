"""Canonicalizer tests: determinism, binning correctness, CaseBook integration.

The canonicalizer turns one CaseBook row (8 observable fields) into a stable
Chinese phrase text — reproducibility here is what makes slot value_text
auditable (same row -> same text, forever).
"""

from __future__ import annotations

import pytest

from embed.canonicalize import (
    FIELD_MAP,
    bin_label,
    canonicalize,
    case_row_from_casebook,
    experience_text,
)
from synth import SyntheticWorld, default_config


def _row(**overrides: float) -> dict[str, float]:
    base = {
        "income_volatility_obs": 0.3,
        "debt_to_income_obs": 0.4,
        "credit_history_years_reported": 6.0,
        "delinquencies_reported": 0.0,
        "months_employed": 40.0,
        "savings_months_obs": 4.0,
        "requested_loan_to_income": 0.5,
        "platform_loans_disclosed": 1.0,
    }
    base.update(overrides)
    return base


class TestDeterminism:
    def test_same_row_same_text(self) -> None:
        row = _row()
        assert canonicalize(row) == canonicalize(dict(row))

    def test_field_order_independent(self) -> None:
        row = _row()
        shuffled = dict(reversed(list(row.items())))
        assert canonicalize(row) == canonicalize(shuffled)

    def test_distinct_bins_distinct_text(self) -> None:
        assert canonicalize(_row()) != canonicalize(_row(debt_to_income_obs=2.5))


class TestBinning:
    @pytest.mark.parametrize(
        ("field", "value", "label"),
        [
            ("debt_to_income_obs", 0.1, "低"),
            ("debt_to_income_obs", 0.4, "中等"),
            ("debt_to_income_obs", 0.8, "偏高"),
            ("debt_to_income_obs", 1.5, "高"),
            ("debt_to_income_obs", 2.5, "极高"),
            ("delinquencies_reported", 0.0, "无"),
            ("delinquencies_reported", 1.0, "一次"),
            ("delinquencies_reported", 5.0, "多次"),
            ("platform_loans_disclosed", 0.0, "无"),
            ("platform_loans_disclosed", 6.0, "多头"),
            ("months_employed", 6.0, "不足一年"),
            ("months_employed", 200.0, "十年以上"),
            ("credit_history_years_reported", 1.0, "极短"),
            ("credit_history_years_reported", 25.0, "很长"),
        ],
    )
    def test_bin_edges(self, field: str, value: float, label: str) -> None:
        spec = next(s for s in FIELD_MAP if s.name == field)
        assert bin_label(spec, value) == label

    def test_bin_upper_bound_is_inclusive(self) -> None:
        spec = next(s for s in FIELD_MAP if s.name == "debt_to_income_obs")
        assert bin_label(spec, 0.2) == "低"
        assert bin_label(spec, 0.200001) == "中等"

    def test_all_eight_fields_appear(self) -> None:
        text = canonicalize(_row())
        for spec in FIELD_MAP:
            assert spec.cn_label in text


class TestValidation:
    def test_missing_field_raises(self) -> None:
        row = _row()
        del row["savings_months_obs"]
        with pytest.raises(KeyError, match="savings_months_obs"):
            canonicalize(row)

    def test_unknown_field_raises(self) -> None:
        with pytest.raises(KeyError, match="unknown_field"):
            canonicalize(_row(unknown_field=1.0))


class TestCaseBookIntegration:
    def test_row_from_casebook_roundtrip(self) -> None:
        world = SyntheticWorld(default_config(seed=7, switch_prob=0.0))
        data = world.run(episodes=2, per_episode=20)
        row = case_row_from_casebook(data.casebook, 0)
        assert set(row) == set(data.casebook.observable_names)
        text = canonicalize(row)
        assert isinstance(text, str) and text

    def test_same_casebook_row_same_text(self) -> None:
        world = SyntheticWorld(default_config(seed=7, switch_prob=0.0))
        data = world.run(episodes=2, per_episode=20)
        a = canonicalize(case_row_from_casebook(data.casebook, 3))
        b = canonicalize(case_row_from_casebook(data.casebook, 3))
        assert a == b


class TestExperienceText:
    def test_outcome_statement_appended(self) -> None:
        text = experience_text(_row(), "bad")
        assert text.endswith("结局:违约")
        assert canonicalize(_row()) in text

    def test_good_outcome(self) -> None:
        assert experience_text(_row(), "good").endswith("结局:正常还款")

    def test_no_outcome_is_plain_canonical(self) -> None:
        assert experience_text(_row(), None) == canonicalize(_row())
