"""Feature-expression verifier tests (design doc §3.1 row 1 + §8.4 gate)."""

import math

import numpy as np
import pandas as pd
import pytest

from synth.config import default_config
from synth.world import SyntheticWorld
from verify.feature import FeatureThresholds, backtest_frame, verify_feature
from verify.metrics import coverage, direction_free_auc, information_value, lift

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
