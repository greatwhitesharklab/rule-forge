"""FullWorld tests: reproducibility, Zipf categoricals, behavior sequences,
regime drift, outcome delay / time red-line, bad-rate sanity, performance.

Blind discipline: statistical assertions only — no concrete rule content.
"""

from __future__ import annotations

import time

import numpy as np

from synthfull import (
    EVENT_VOCAB,
    MAX_SEQ_LEN,
    FullWorld,
    WorldData,
    default_config,
)

SUBMIT = EVENT_VOCAB.index("submit")


def _run(seed: int, episodes: int, per_episode: int, **cfg) -> WorldData:
    return FullWorld(default_config(seed=seed, **cfg)).run(episodes, per_episode)


# ---------------------------------------------------------------------------
# Reproducibility + performance
# ---------------------------------------------------------------------------


class TestReproducibility:
    def test_same_seed_bit_identical(self) -> None:
        a = _run(42, 20, 500)
        b = _run(42, 20, 500)
        for x, y in ((a.casebook, b.casebook), (a.ledger, b.ledger), (a.truth, b.truth)):
            for f in vars(x):
                va, vb = getattr(x, f), getattr(y, f)
                if isinstance(va, np.ndarray):
                    assert np.array_equal(va, vb), f"field {f} differs"
                else:
                    assert va == vb, f"field {f} differs"
        assert a.regimes == b.regimes

    def test_100k_cases_under_10_seconds(self) -> None:
        t0 = time.perf_counter()
        data = _run(7, 100, 1000)
        elapsed = time.perf_counter() - t0
        n = 100_000
        assert data.casebook.observables.shape == (n, 8)
        assert data.casebook.seq_events.shape == (n, MAX_SEQ_LEN)
        assert data.casebook.device_id.shape == (n,)
        assert elapsed < 10.0, f"100k cases took {elapsed:.1f}s (target < 10s)"


# ---------------------------------------------------------------------------
# Categorical modality: Zipf heavy tail
# ---------------------------------------------------------------------------


class TestCategoricals:
    @staticmethod
    def _top_share(values: np.ndarray, pool: int, frac: float) -> float:
        counts = np.bincount(values, minlength=pool)
        k = max(1, int(round(pool * frac)))
        return float(np.sort(counts)[::-1][:k].sum() / len(values))

    def test_device_distribution_is_heavy_tailed(self) -> None:
        cfg = default_config()
        pool = next(s.pool_size for s in cfg.categoricals if s.name == "device_id")
        data = _run(11, 50, 2000)
        share = self._top_share(data.casebook.device_id, pool, 0.01)
        # Uniform would give ~0.01; Zipf must be far above, yet not collapse.
        assert share > 0.3, f"top-1% device share {share} not heavy-tailed"
        assert share < 0.95, f"top-1% device share {share} degenerate"

    def test_all_categoricals_skewed_and_in_pool(self) -> None:
        cfg = default_config()
        data = _run(13, 50, 2000)
        for spec in cfg.categoricals:
            values = data.casebook.categorical(spec.name)
            assert values.min() >= 0 and values.max() < spec.pool_size
            share = self._top_share(values, spec.pool_size, 0.01)
            assert share > 5 * 0.01, f"{spec.name} looks uniform"
            assert len(np.unique(values)) > min(spec.pool_size, 500) * 0.5

    def test_big_pool_tail_is_long(self) -> None:
        data = _run(13, 50, 2000)
        uniq = len(np.unique(data.casebook.device_id))
        assert 2_000 < uniq < 50_000  # long tail visited, pool not exhausted


# ---------------------------------------------------------------------------
# Sequence modality
# ---------------------------------------------------------------------------


class TestSequences:
    def test_padding_and_terminal_submit_invariants(self) -> None:
        data = _run(17, 20, 1000)
        ev, dur, ln = (data.casebook.seq_events, data.casebook.seq_durations,
                       data.casebook.seq_len)
        n = ev.shape[0]
        pos = np.arange(ev.shape[1])[None, :]
        valid = pos < ln[:, None]
        assert (ev[valid] >= 0).all()
        assert (ev[~valid] == -1).all()
        assert (dur[valid] > 0).all()
        assert (dur[~valid] == 0).all()
        assert (ev[np.arange(n), ln - 1] == SUBMIT).all()

    def test_lengths_within_mode_ranges(self) -> None:
        data = _run(17, 20, 1000)
        modes = data.config.modes
        for mid, spec in enumerate(modes):
            m = data.truth.seq_mode == mid
            assert m.any(), f"mode {spec.name} never sampled"
            ln = data.casebook.seq_len[m]
            assert ln.min() >= spec.len_range[0]
            assert ln.max() <= spec.len_range[1]

    def test_modes_are_behaviorally_distinct(self) -> None:
        data = _run(17, 50, 2000)
        totals = data.casebook.seq_durations.sum(axis=1)
        names = data.truth.mode_names
        mean_dur = {names[mid]: totals[data.truth.seq_mode == mid].mean()
                    for mid in range(len(names))}
        # Instant fillers are an order of magnitude faster than hesitant ones.
        assert mean_dur["instant"] * 10 < mean_dur["hesitant"]
        assert mean_dur["instant"] < mean_dur["normal"] < mean_dur["hesitant"]

    def test_latent_mode_not_on_feature_side(self) -> None:
        data = _run(17, 10, 200)
        # The behavior mode is latent: no mode field may leak into CaseBook.
        for field_name in vars(data.casebook):
            assert "mode" not in field_name
        feats, _ = data.matured_view(5)
        assert all("mode" not in k for k in feats)


# ---------------------------------------------------------------------------
# Outcome delay + time red-line
# ---------------------------------------------------------------------------


class TestOutcomeDelay:
    def test_delay_within_configured_range(self) -> None:
        data = _run(11, 50, 500)
        assert data.ledger.delay.min() >= 1
        assert data.ledger.delay.max() <= 3

    def test_visible_episode_equals_generation_plus_delay(self) -> None:
        data = _run(11, 50, 500)
        expected = data.casebook.episode + data.ledger.delay.astype(np.int32)
        assert np.array_equal(data.ledger.visible_episode, expected)

    def test_matured_view_is_time_safe(self) -> None:
        data = _run(11, 50, 500)
        for field_name in vars(data.casebook):
            assert "outcome" not in field_name and "label" not in field_name
        feats, y = data.matured_view(0)
        assert len(y) == 0  # nothing visible at decision time of episode 0
        for ep in (0, 5, 20, 49):
            feats, y = data.matured_view(ep)
            mask = data.ledger.visible_mask(ep)
            assert len(y) == mask.sum()
            assert feats["observables"].shape[0] == mask.sum()
            assert feats["seq_events"].shape[0] == mask.sum()
            assert (data.ledger.visible_episode[mask] <= ep).all()
        assert data.ledger.visible_mask(49).mean() > 0.9


# ---------------------------------------------------------------------------
# Regime switching
# ---------------------------------------------------------------------------


class TestRegimeSwitching:
    def test_switch_frequency_matches_geometric_rate(self) -> None:
        data = _run(5, 2000, 5)
        freq = len(data.regimes) / 1999
        assert 0.07 < freq < 0.13, f"switch frequency {freq} off Geo(0.1)"

    def test_switch_mutates_a_partial_subset_of_weights(self) -> None:
        world = FullWorld(default_config(seed=9))
        base = world.current_weights.copy()
        data = world.run(400, 5)
        assert len(data.regimes) > 5
        n_rules = len(world.rules)
        for ev in data.regimes:
            assert 0 < len(ev.mutations) < n_rules  # partial, never all
            for mu in ev.mutations:
                assert mu.new_weight != mu.old_weight
                assert mu.mode in ("decay", "boost", "flip")
        assert not np.allclose(world.current_weights, base)

    def test_drift_touches_multiple_condition_kinds(self) -> None:
        # Categorical/sequence rules participate in regime drift too.
        world = FullWorld(default_config(seed=9))
        data = world.run(400, 5)
        first_kind = {r.rule_id: r.conditions[0].kind for r in world.rules}
        touched = {first_kind[mu.rule_id]
                   for ev in data.regimes for mu in ev.mutations}
        assert len(touched) >= 2

    def test_regime_history_and_tags(self) -> None:
        data = _run(9, 200, 20)
        assert all(t.startswith("R") for t in data.casebook.regime_tag[:100])
        first = data.regimes[0].episode if data.regimes else 200
        pre = data.casebook.episode < first
        assert (data.casebook.regime_id[pre] == 0).all()
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
        data = _run(21, 200, 1000)
        rates = [data.ledger.outcome[data.casebook.regime_id == r].mean()
                 for r in np.unique(data.casebook.regime_id)]
        assert max(rates) - min(rates) > 0.005
