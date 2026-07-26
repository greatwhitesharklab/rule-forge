"""Feature registry tests: definition form (design §8.2) and the single
compute path shared by training and inference (§8.3 rule 1)."""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from scoring import (
    FeatureRegistry,
    RollingScorer,
    ScorerConfig,
    build_default_registry,
    feature,
)
from synth import SyntheticWorld, default_config


def _obs_names() -> tuple[str, ...]:
    data = SyntheticWorld(default_config(seed=5)).run(episodes=2, per_episode=10)
    return data.casebook.observable_names


# ---------------------------------------------------------------------------
# Definition form: name / version / author / docstring assumption (§8.2)
# ---------------------------------------------------------------------------


class TestDefinitionForm:
    def test_every_registered_feature_has_full_provenance(self) -> None:
        reg = build_default_registry(_obs_names())
        assert len(reg) > 0
        for spec in reg.specs():
            assert spec.name and spec.version and spec.author, spec.name
            assert spec.assumption.strip(), f"{spec.name} missing assumption"
            assert spec.level in ("L0", "L1")

    def test_l0_passthroughs_cover_all_observables(self) -> None:
        names = _obs_names()
        reg = build_default_registry(names)
        l0 = [s.name for s in reg.specs() if s.level == "L0"]
        assert sorted(l0) == sorted(names)

    def test_l1_features_present(self) -> None:
        reg = build_default_registry(_obs_names())
        l1 = [s.name for s in reg.specs() if s.level == "L1"]
        assert len(l1) >= 3

    def test_duplicate_name_rejected(self) -> None:
        reg = build_default_registry(_obs_names())
        with pytest.raises(ValueError, match="duplicate"):

            @feature(name="total_leverage", version="9.9", author="x", registry=reg)
            def _dup(df: pd.DataFrame) -> pd.Series:
                """Duplicate registration must fail."""
                return df["debt_to_income_obs"]

    def test_missing_metadata_rejected(self) -> None:
        reg = FeatureRegistry()
        with pytest.raises(ValueError):

            @feature(name="no_author", version="", author="", registry=reg)
            def _bad(df: pd.DataFrame) -> pd.Series:
                """Has a docstring but empty version/author."""
                return df["debt_to_income_obs"]

    def test_missing_docstring_rejected(self) -> None:
        reg = FeatureRegistry()
        with pytest.raises(ValueError, match="assumption"):

            @feature(name="no_doc", version="1.0", author="x", registry=reg)
            def _nodoc(df: pd.DataFrame) -> pd.Series:
                return df["debt_to_income_obs"]


# ---------------------------------------------------------------------------
# Computation correctness (hand-checkable L1 values)
# ---------------------------------------------------------------------------


def _one_row() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "income_volatility_obs": [0.5],
            "debt_to_income_obs": [0.5],
            "credit_history_years_reported": [3.0],
            "delinquencies_reported": [2.0],
            "months_employed": [11.0],
            "savings_months_obs": [4.0],
            "requested_loan_to_income": [1.0],
            "platform_loans_disclosed": [2.0],
        }
    )


class TestCompute:
    def test_l0_passthrough_values(self) -> None:
        reg = build_default_registry(_obs_names())
        out = reg.compute(_one_row())
        assert out["debt_to_income_obs"].iloc[0] == 0.5
        assert out["platform_loans_disclosed"].iloc[0] == 2.0

    def test_l1_hand_computed_values(self) -> None:
        reg = build_default_registry(_obs_names())
        out = reg.compute(_one_row())
        assert out["total_leverage"].iloc[0] == pytest.approx(1.5)
        assert out["savings_runway"].iloc[0] == pytest.approx(4.0 / 0.5, rel=1e-4)
        assert out["delinquency_intensity"].iloc[0] == pytest.approx(2.0 / 4.0)
        assert out["multi_platform_leverage"].iloc[0] == pytest.approx(2.0)
        expected = np.log1p(11.0) * (1.0 - 0.5)
        assert out["job_income_stability"].iloc[0] == pytest.approx(expected)

    def test_column_order_is_registry_order(self) -> None:
        reg = build_default_registry(_obs_names())
        out = reg.compute(_one_row())
        assert tuple(out.columns) == reg.names


# ---------------------------------------------------------------------------
# §8.3 rule 1: train and inference go through the SAME compute function
# ---------------------------------------------------------------------------


class TestSingleComputePath:
    def test_scorer_uses_registry_compute_for_both_phases(self) -> None:
        data = SyntheticWorld(default_config(seed=5, switch_prob=0.0)).run(
            episodes=8, per_episode=100
        )
        reg = build_default_registry(data.casebook.observable_names)
        calls: list[tuple[int, tuple[str, ...]]] = []
        orig = reg.compute

        def spy(obs: pd.DataFrame) -> pd.DataFrame:
            out = orig(obs)
            calls.append((obs.shape[0], tuple(out.columns)))
            return out

        reg.compute = spy  # type: ignore[method-assign]
        scorer = RollingScorer(reg, ScorerConfig(min_train_samples=50))
        assert scorer._compute is spy  # the scorer holds exactly this function
        scorer.run(data)

        row_counts = {n for n, _ in calls}
        # per-episode inference batches (100 rows) and larger training frames
        assert 100 in row_counts
        assert any(n > 100 for n in row_counts)
        # every call returned the identical feature schema
        assert len({cols for _, cols in calls}) == 1
