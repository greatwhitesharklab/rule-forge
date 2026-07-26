"""Rolling GBDT scorer tests: cold start, time red-line (§8.3 rule 2),
training window, rolling metric series, reproducibility, performance."""

from __future__ import annotations

import time
from dataclasses import replace

import numpy as np

from scoring import RollingScorer, ScorerConfig, build_default_registry
from synth import SyntheticWorld, default_config
from synth.world import WorldData


def _world(seed: int, episodes: int, per_episode: int, **cfg: object) -> WorldData:
    return SyntheticWorld(default_config(seed=seed, **cfg)).run(episodes, per_episode)


def _scorer(data: WorldData, **cfg: object) -> RollingScorer:
    reg = build_default_registry(data.casebook.observable_names)
    return RollingScorer(reg, ScorerConfig(**cfg))


# ---------------------------------------------------------------------------
# Cold start: never guess without enough matured samples
# ---------------------------------------------------------------------------


class TestColdStart:
    def test_first_episode_is_cold(self) -> None:
        data = _world(17, 8, 100, switch_prob=0.0)
        result = _scorer(data, min_train_samples=200).run(data)
        b0 = result.batches[0]
        assert b0.cold_start is True
        assert b0.proba is None
        assert b0.n_train == 0  # delay >= 1: nothing matured at episode 0

    def test_scorer_warms_up(self) -> None:
        data = _world(17, 8, 100, switch_prob=0.0)
        result = _scorer(data, min_train_samples=100).run(data)
        warm = [b for b in result.batches if not b.cold_start]
        assert warm, "scorer never warmed up"
        for b in warm:
            assert b.proba is not None and b.proba.shape == (100,)
            assert np.all((b.proba >= 0.0) & (b.proba <= 1.0))

    def test_impossible_threshold_stays_cold(self) -> None:
        data = _world(17, 6, 100, switch_prob=0.0)
        result = _scorer(data, min_train_samples=10**9).run(data)
        assert all(b.cold_start for b in result.batches)
        assert all(b.proba is None for b in result.batches)


# ---------------------------------------------------------------------------
# §8.3 rule 2: labels with visible_episode > t must never enter training
# ---------------------------------------------------------------------------


class TestTimeRedLine:
    def test_future_label_never_enters_training(self) -> None:
        data = _world(3, 12, 200, switch_prob=0.0)
        t = 6
        # Sabotage: take a case whose outcome IS matured by t and push its
        # visibility into the future. The label physically exists in the
        # ledger array; a leaky join would pick it up.
        vis = data.ledger.visible_episode.copy()
        matured_by_t = np.where(vis <= t)[0]
        victim = int(matured_by_t[0])
        vis[victim] = t + 2
        tampered = replace(data, ledger=replace(data.ledger, visible_episode=vis))

        captured: dict[int, np.ndarray] = {}

        def hook(ep: int, x: np.ndarray, y: np.ndarray) -> None:
            captured[ep] = y.copy()

        cfg = ScorerConfig(min_train_samples=50, window_episodes=None,
                           train_hook=hook)
        _scorer(tampered, **vars(cfg)).run(tampered)

        for ep, y_train in captured.items():
            expected = tampered.ledger.outcome[tampered.ledger.visible_mask(ep)]
            assert np.array_equal(y_train, expected), f"leak at episode {ep}"
        # the victim is excluded at t but included once t+2 arrives
        assert len(captured[t]) == int((vis <= t).sum())
        assert len(captured[t + 2]) == int((vis <= t + 2).sum())

    def test_training_labels_come_only_from_matured_view_filter(self) -> None:
        # Same-seed world, no tampering: training labels at every episode must
        # equal outcome[visible_mask(episode)] exactly — nothing else.
        data = _world(23, 10, 150)
        captured: dict[int, np.ndarray] = {}
        cfg = ScorerConfig(min_train_samples=50, window_episodes=None,
                           train_hook=lambda e, x, y: captured.__setitem__(e, y.copy()))
        _scorer(data, **vars(cfg)).run(data)
        for ep, y_train in captured.items():
            expected = data.ledger.outcome[data.ledger.visible_mask(ep)]
            assert np.array_equal(y_train, expected)


# ---------------------------------------------------------------------------
# Training window (configurable recency limit)
# ---------------------------------------------------------------------------


class TestWindow:
    def test_window_caps_training_size(self) -> None:
        data = _world(29, 12, 100, switch_prob=0.0)
        result = _scorer(data, min_train_samples=50, window_episodes=3).run(data)
        warm = [b for b in result.batches if not b.cold_start]
        assert warm
        # matured cases from the last 3 episodes can never exceed 3 * 100
        assert max(b.n_train for b in warm) <= 300

    def test_no_window_uses_full_history(self) -> None:
        data = _world(29, 10, 100, switch_prob=0.0)
        result = _scorer(data, min_train_samples=50, window_episodes=None).run(data)
        warm = [b for b in result.batches if not b.cold_start]
        assert warm[-1].n_train > 500  # keeps growing beyond any small window


# ---------------------------------------------------------------------------
# Rolling metric series (retrospective AUC/KS/logloss per episode)
# ---------------------------------------------------------------------------


class TestRollingMetrics:
    def test_metrics_frame_shape_and_columns(self) -> None:
        data = _world(99, 40, 300)
        result = _scorer(data, min_train_samples=200, window_episodes=30).run(data)
        mf = result.metrics_frame(data)
        assert {"episode", "n", "auc", "ks", "logloss"} <= set(mf.columns)
        assert mf["episode"].is_unique
        assert mf["episode"].is_monotonic_increasing
        n_warm = sum(1 for b in result.batches if not b.cold_start)
        # episodes whose horizon-visible labels are single-class are dropped
        # (AUC undefined), so a small tail loss is expected
        assert n_warm - 3 <= len(mf) <= n_warm

    def test_auc_well_above_chance(self) -> None:
        data = _world(99, 40, 300)
        result = _scorer(data, min_train_samples=200, window_episodes=30).run(data)
        mf = result.metrics_frame(data)
        warm = mf[mf["episode"] >= 10]
        assert warm["auc"].mean() > 0.70
        assert mf["auc"].between(0.0, 1.0).all()
        assert mf["ks"].between(0.0, 1.0).all()
        assert (mf["logloss"] > 0.0).all()

    def test_metrics_respect_visibility_horizon(self) -> None:
        data = _world(99, 20, 200, switch_prob=0.0)
        result = _scorer(data, min_train_samples=100).run(data)
        horizon = 15
        mf = result.metrics_frame(data, upto_episode=horizon)
        last_ep = int(mf["episode"].max())
        # cases of episodes whose outcomes all mature after the horizon are
        # excluded entirely
        assert last_ep <= horizon - 1
        row = mf[mf["episode"] == last_ep].iloc[0]
        assert row["n"] <= 200


# ---------------------------------------------------------------------------
# Reproducibility + performance
# ---------------------------------------------------------------------------


class TestReproducibility:
    def test_same_seed_identical_scores(self) -> None:
        data = _world(7, 15, 200, switch_prob=0.0)
        a = _scorer(data, min_train_samples=100).run(data).scores_frame()
        b = _scorer(data, min_train_samples=100).run(data).scores_frame()
        assert np.array_equal(
            a["proba"].to_numpy(dtype=float, na_value=-1.0),
            b["proba"].to_numpy(dtype=float, na_value=-1.0),
        )


class TestPerformance:
    def test_full_volume_under_60s(self) -> None:
        data = _world(20260726, 100, 1000)
        start = time.perf_counter()
        result = _scorer(data, min_train_samples=2000, window_episodes=10).run(data)
        elapsed = time.perf_counter() - start
        assert elapsed < 60.0, f"rolling retrain too slow: {elapsed:.1f}s"
        warm = [b for b in result.batches if not b.cold_start]
        assert len(warm) >= 90
