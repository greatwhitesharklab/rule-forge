"""World-level tests: reproducibility, regime drift, outcome delay, time
red-line, and bad-rate sanity (design doc §5 P0 acceptance)."""

from __future__ import annotations

import numpy as np
import pytest

from synth import SyntheticWorld, WorldData, default_config


def _run(seed: int, episodes: int, per_episode: int, **cfg) -> WorldData:
    return SyntheticWorld(default_config(seed=seed, **cfg)).run(episodes, per_episode)


# ---------------------------------------------------------------------------
# Reproducibility (hard acceptance criterion: same seed -> row-identical)
# ---------------------------------------------------------------------------


class TestReproducibility:
    def test_same_seed_bit_identical(self) -> None:
        a = _run(42, 30, 400)
        b = _run(42, 30, 400)
        for x, y in ((a.casebook, b.casebook), (a.ledger, b.ledger), (a.truth, b.truth)):
            for f in vars(x):
                va, vb = getattr(x, f), getattr(y, f)
                if isinstance(va, np.ndarray):
                    assert np.array_equal(va, vb), f"field {f} differs"
                else:
                    assert va == vb, f"field {f} differs"
        assert a.regimes == b.regimes  # dataclass eq, regime history identical

    def test_different_seed_differs(self) -> None:
        a = _run(42, 30, 400)
        b = _run(43, 30, 400)
        assert not np.array_equal(a.casebook.observables, b.casebook.observables)
        assert not np.array_equal(a.ledger.outcome, b.ledger.outcome)

    def test_100k_cases_generate_fast_enough(self) -> None:
        # Acceptance volume: 100 episodes x 1000 cases. Just assert it runs
        # and has the right shape; wall-time is eyeball-checked by the demo.
        data = _run(7, 100, 1000)
        assert data.casebook.observables.shape == (100_000, 8)
        assert data.truth.concepts.shape == (100_000, 6)


# ---------------------------------------------------------------------------
# Outcome delay + time red-line
# ---------------------------------------------------------------------------


class TestOutcomeDelay:
    def test_delay_within_1_to_3_episodes(self) -> None:
        data = _run(11, 50, 500)
        assert set(np.unique(data.ledger.delay)) <= {1, 2, 3}
        assert data.ledger.delay.min() >= 1

    def test_visible_episode_equals_generation_plus_delay(self) -> None:
        data = _run(11, 50, 500)
        expected = data.casebook.episode + data.ledger.delay.astype(np.int32)
        assert np.array_equal(data.ledger.visible_episode, expected)

    def test_decision_snapshot_carries_no_outcome(self) -> None:
        data = _run(11, 50, 500)
        # CaseBook has no label-ish fields at all (structural red-line).
        for field_name in vars(data.casebook):
            assert "outcome" not in field_name and "label" not in field_name
        # Nothing is visible at the decision moment of episode 0.
        obs, y = data.matured_view(0)
        assert len(y) == 0

    def test_matured_view_is_time_safe(self) -> None:
        data = _run(11, 50, 500)
        for ep in (0, 5, 20, 49):
            _, y = data.matured_view(ep)
            mask = data.ledger.visible_mask(ep)
            assert len(y) == mask.sum()
            assert (data.ledger.visible_episode[mask] <= ep).all()
        # By the last episode, only the tail (delay spillover) is unmatured.
        assert data.ledger.visible_mask(49).mean() > 0.9


# ---------------------------------------------------------------------------
# Regime switching
# ---------------------------------------------------------------------------


class TestRegimeSwitching:
    def test_switch_frequency_matches_geometric_rate(self) -> None:
        data = _run(5, 2000, 5)
        freq = len(data.regimes) / 1999  # episode 0 never switches
        assert 0.07 < freq < 0.13, f"switch frequency {freq} off Geo(0.1)"

    def test_switch_actually_mutates_weights(self) -> None:
        world = SyntheticWorld(default_config(seed=9))
        base = world.current_weights.copy()
        data = world.run(400, 5)
        assert len(data.regimes) > 5  # expect ~40 switches
        for ev in data.regimes:
            assert len(ev.mutations) > 0
            for mu in ev.mutations:
                assert mu.new_weight != mu.old_weight
                assert mu.mode in ("decay", "boost", "flip")
        assert not np.allclose(world.current_weights, base)

    def test_regime_history_and_tags(self) -> None:
        data = _run(9, 200, 20)
        # Per-case regime tags are recorded and well-formed.
        assert all(t.startswith("R") for t in data.casebook.regime_tag[:100])
        # Before the first switch everything is regime R00.
        first = data.regimes[0].episode if data.regimes else 200
        pre = data.casebook.episode < first
        assert (data.casebook.regime_id[pre] == 0).all()
        # Event tags match the regime they introduce.
        for ev in data.regimes:
            assert ev.regime_tag == f"R{ev.regime_id:02d}"

    def test_static_world_when_switch_prob_zero(self) -> None:
        data = _run(3, 100, 20, switch_prob=0.0)
        assert data.regimes == ()
        assert (data.casebook.regime_id == 0).all()


# ---------------------------------------------------------------------------
# Bad-rate sanity
# ---------------------------------------------------------------------------


class TestBadRate:
    def test_bad_rate_in_reasonable_band(self) -> None:
        data = _run(21, 100, 1000)
        bad = data.ledger.outcome.mean()
        assert 0.05 < bad < 0.40, f"bad_rate {bad} degenerate"
        assert set(np.unique(data.ledger.outcome)) == {0, 1}

    def test_bad_rate_not_identical_across_regimes(self) -> None:
        # Drift must be visible in outcomes: per-regime bad rates differ.
        data = _run(21, 200, 1000)
        rates = [data.ledger.outcome[data.casebook.regime_id == r].mean()
                 for r in np.unique(data.casebook.regime_id)]
        assert max(rates) - min(rates) > 0.005
