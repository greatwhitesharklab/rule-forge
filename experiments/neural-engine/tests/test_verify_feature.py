"""Feature-expression verifier tests (design doc §3.1 row 1 + §8.4 gate)."""

import math

import numpy as np
import pandas as pd
import pytest

from synth.config import default_config
from synth.world import SyntheticWorld
from verify.feature import FeatureThresholds, backtest_frame, verify_feature
from verify.metrics import coverage, direction_free_auc, information_value, lift, lift_good

# 20-row known distribution: x=0 -> 8 good / 2 bad; x=1 -> 2 good / 8 bad.
KNOWN_VALUES = [0.0] * 10 + [1.0] * 10
KNOWN_LABELS = [0] * 8 + [1] * 2 + [1] * 8 + [0] * 2


class TestMetricMath:
    def test_iv_exact_known_distribution(self) -> None:
        # dist_bad = (0.2, 0.8), dist_good = (0.8, 0.2)
        # IV = 2 * (0.8 - 0.2) * ln(0.8 / 0.2) = 1.2 * ln(4)
        iv = information_value(KNOWN_VALUES, KNOWN_LABELS)
        assert math.isclose(iv, 1.2 * math.log(4.0), rel_tol=1e-9)

    def test_lift_exact_known_distribution(self) -> None:
        # overall bad rate 0.5; best bin bad rate 0.8 -> lift 1.6
        assert lift(KNOWN_VALUES, KNOWN_LABELS) == pytest.approx(1.6)

    def test_coverage_counts_nan_as_missing(self) -> None:
        assert coverage([1.0, np.nan, 2.0, np.inf]) == pytest.approx(0.5)

    def test_auc_direction_free_perfect_both_ways(self) -> None:
        labels = [0, 0, 1, 1]
        assert direction_free_auc([0.1, 0.2, 0.8, 0.9], labels) == pytest.approx(1.0)
        assert direction_free_auc([0.9, 0.8, 0.2, 0.1], labels) == pytest.approx(1.0)

    def test_auc_random_near_half(self) -> None:
        rng = np.random.default_rng(0)
        values = rng.normal(size=2000)
        labels = (rng.random(2000) < 0.3).astype(np.int8)
        auc = direction_free_auc(values, labels)
        assert 0.4 < auc < 0.6

    def test_auc_single_class_is_nan(self) -> None:
        assert math.isnan(direction_free_auc([1.0, 2.0], [1, 1]))


class TestLiftGood:
    """Symmetric (protective-direction) lift, design doc §9.4 fix."""

    def test_lift_good_exact_known_distribution(self) -> None:
        # Jeffreys-smoothed: rates (2.5/11, 8.5/11), overall 10.5/21 = 0.5
        # lift_good = 0.5 / (2.5/11) = 2.2
        assert lift_good(KNOWN_VALUES, KNOWN_LABELS) == pytest.approx(2.2)

    def test_zero_bad_bin_stays_finite_via_smoothing(self) -> None:
        # Segment 0 is perfectly protective (0/50 bad); without smoothing the
        # min bin rate would be exactly 0 and lift_good would diverge.
        values = [0.0] * 50 + [1.0] * 50
        labels = [0] * 50 + [1] * 20 + [0] * 30
        # (20.5/101) / (0.5/51) = 20.70297...
        lg = lift_good(values, labels)
        assert math.isfinite(lg)
        assert lg == pytest.approx(1045.5 / 50.5)

    def test_single_bin_is_neutral(self) -> None:
        # One bin only: min rate == overall rate -> exactly 1.0.
        assert lift_good([1.0] * 10, [0, 1] * 5) == pytest.approx(1.0)

    def test_no_bads_is_nan(self) -> None:
        assert math.isnan(lift_good([1.0, 2.0], [0, 0]))


def _noisy_frame(n: int = 3000, seed: int = 11) -> tuple[pd.DataFrame, np.ndarray]:
    rng = np.random.default_rng(seed)
    z = rng.normal(size=n)
    p_bad = 1.0 / (1.0 + np.exp(-(-1.5 + 1.3 * z)))
    y = (rng.random(n) < p_bad).astype(np.int8)
    return pd.DataFrame({"z": z}), y


class TestVerifyFeature:
    def test_predictive_feature_passes(self) -> None:
        df, y = _noisy_frame()
        v = verify_feature("df.z", df, y)
        assert v.status == "pass"
        assert v.metrics["iv"] > 0.1
        assert v.metrics["coverage"] == 1.0
        assert 0.5 < v.quality <= 1.0

    def test_noise_feature_fails_below_gate(self) -> None:
        rng = np.random.default_rng(3)
        n = 2000
        df = pd.DataFrame({"noise": rng.normal(size=n)})
        y = (rng.random(n) < 0.2).astype(np.int8)
        v = verify_feature("df.noise", df, y)
        assert v.status == "fail"
        assert v.metrics["iv"] <= 0.1
        assert 0.0 <= v.quality <= 0.2

    def test_lift_only_path_passes(self) -> None:
        # Rare strong segment: lift > 1.3 but IV < 0.1.
        flag = [1.0] * 100 + [0.0] * 900
        labels = [1] * 15 + [0] * 85 + [1] * 90 + [0] * 810
        df = pd.DataFrame({"flag": flag})
        v = verify_feature("df.flag", df, np.array(labels, dtype=np.int8))
        assert v.metrics["iv"] < 0.1
        assert v.metrics["lift"] > 1.3
        assert v.status == "pass"

    def test_leak_triggers_quarantine_not_reject(self) -> None:
        rng = np.random.default_rng(5)
        y = (rng.random(500) < 0.3).astype(np.int8)
        df = pd.DataFrame({"leak": y.astype(float)})
        v = verify_feature("df.leak", df, y)
        assert v.status == "quarantine"
        assert v.quality == 0.0
        assert v.metrics["auc"] > 0.95
        assert any("leak" in r for r in v.reasons)

    def test_strong_but_not_leaked_feature_not_quarantined(self) -> None:
        df, y = _noisy_frame()
        v = verify_feature("df.z", df, y)
        assert v.status != "quarantine"
        assert v.metrics["auc"] < 0.95

    def test_low_coverage_fails(self) -> None:
        df = pd.DataFrame({"x": [-1.0] * 96 + [5.0] * 4})
        y = np.array([0, 1] * 50, dtype=np.int8)
        v = verify_feature("df.x.where(df.x > 0)", df, y)
        assert v.status == "fail"
        assert v.metrics["coverage"] <= 0.05
        assert any("coverage" in r for r in v.reasons)

    def test_non_executable_expression_fails_zero_quality(self) -> None:
        df = pd.DataFrame({"a": [1.0, 2.0]})
        y = np.array([0, 1], dtype=np.int8)
        v = verify_feature("df.nope + 1.0", df, y)
        assert v.status == "fail"
        assert v.quality == 0.0
        assert any("sandbox" in r for r in v.reasons)

    def test_thresholds_configurable(self) -> None:
        df, y = _noisy_frame()
        strict = FeatureThresholds(iv_min=99.0, lift_min=99.0)
        v = verify_feature("df.z", df, y, thresholds=strict)
        assert v.status == "fail"

    def test_protective_feature_passes_via_lift_good(self) -> None:
        # Small perfectly-protective segment (20/1000 cases, 0 bads):
        # IV ~ 0.054 < 0.1, lift_bad ~ 1.05 < 1.3 — the old gate was blind to
        # this (design doc §9.4); lift_good ~ 8.4 must admit it.
        flag = [1.0] * 20 + [0.0] * 980
        labels = [0] * 20 + [1] * 200 + [0] * 780
        df = pd.DataFrame({"flag": flag})
        v = verify_feature("df.flag", df, np.array(labels, dtype=np.int8))
        assert v.metrics["iv"] < 0.1
        assert v.metrics["lift_bad"] < 1.3
        assert v.metrics["lift_good"] > 1.3
        assert v.status == "pass"
        assert any("lift_good" in r for r in v.reasons)
        # Legacy "lift" key remains as the bad-direction alias.
        assert v.metrics["lift"] == v.metrics["lift_bad"]

    def test_weak_both_directions_fails_and_names_new_criterion(self) -> None:
        rng = np.random.default_rng(3)
        n = 2000
        df = pd.DataFrame({"noise": rng.normal(size=n)})
        y = (rng.random(n) < 0.2).astype(np.int8)
        v = verify_feature("df.noise", df, y)
        assert v.status == "fail"
        assert v.metrics["lift_bad"] <= 1.3
        assert v.metrics["lift_good"] <= 1.3
        assert any("lift_good" in r and "below gate" in r for r in v.reasons)


class TestSynthWorldBacktest:
    def test_backtest_frame_is_time_safe_join(self) -> None:
        world = SyntheticWorld(default_config(seed=7))
        data = world.run(6, 200)
        df, labels = backtest_frame(data, episode=5)
        assert list(df.columns) == list(data.casebook.observable_names)
        assert len(df) == len(labels) == int(data.ledger.visible_mask(5).sum())

    def test_verify_expression_on_world_data(self) -> None:
        world = SyntheticWorld(default_config(seed=7))
        data = world.run(6, 200)
        df, labels = backtest_frame(data, episode=5)
        v = verify_feature("df.income_volatility_obs", df, labels)
        assert v.metrics["coverage"] == 1.0
        assert math.isfinite(v.metrics["iv"])
        assert v.status in ("pass", "fail", "quarantine")
