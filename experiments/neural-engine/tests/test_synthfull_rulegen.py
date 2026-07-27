"""Random rule generator tests: reproducibility, structural statistics,
JSON ground truth, fire-rate non-degeneracy, effect direction/strength.

Blind discipline: these tests assert STATISTICAL properties of the rule
pool only. No concrete rule content (fields, thresholds, value sets,
weights, or text of any specific seed) may appear here.
"""

from __future__ import annotations

import json

import numpy as np

from synthfull import FullWorld, default_config
from synthfull.rulegen import (
    COND_CAT,
    COND_NUM,
    COND_SEQ,
    EXPERIENCE,
    HELDOUT,
    RandomRuleGenerator,
    rules_payload,
    rules_to_json,
)

_KINDS = (COND_NUM, COND_CAT, COND_SEQ)


def _rules(seed: int, **cfg):
    return RandomRuleGenerator(default_config(seed=seed, **cfg)).generate()


# ---------------------------------------------------------------------------
# Reproducibility / seed sensitivity
# ---------------------------------------------------------------------------


class TestGeneratorReproducibility:
    def test_same_seed_identical_rules(self) -> None:
        a = rules_to_json(_rules(42))
        b = rules_to_json(_rules(42))
        assert a == b

    def test_different_seed_different_structure(self) -> None:
        payloads = {rules_to_json(_rules(s)) for s in (1, 2, 3, 4, 5)}
        assert len(payloads) == 5  # every seed produced a distinct pool

    def test_seed_changes_world_data_too(self) -> None:
        a = FullWorld(default_config(seed=42)).run(10, 200)
        b = FullWorld(default_config(seed=43)).run(10, 200)
        assert not np.array_equal(a.casebook.observables, b.casebook.observables)
        assert not np.array_equal(a.casebook.seq_events, b.casebook.seq_events)


# ---------------------------------------------------------------------------
# Structural statistics (pool shape, condition mix, effect ranges)
# ---------------------------------------------------------------------------


class TestPoolStructure:
    def test_pool_sizes_and_tags(self) -> None:
        rules = _rules(7)
        cfg = default_config()
        assert len(rules) == cfg.n_experience + cfg.n_heldout
        pools = [r.pool for r in rules]
        assert pools[: cfg.n_experience] == [EXPERIENCE] * cfg.n_experience
        assert set(pools[cfg.n_experience:]) == {HELDOUT}
        assert len({r.rule_id for r in rules}) == len(rules)

    def test_condition_counts_within_1_to_3(self) -> None:
        for seed in range(10):
            for r in _rules(seed):
                assert 1 <= len(r.conditions) <= 3

    def test_condition_kind_mix(self) -> None:
        # Over many seeds, all three condition sources appear with a
        # non-trivial share and no source dominates completely.
        kinds = [c.kind for s in range(20) for r in _rules(s) for c in r.conditions]
        total = len(kinds)
        assert total > 500
        for kind in _KINDS:
            share = kinds.count(kind) / total
            assert 0.05 < share < 0.85, f"{kind} share {share} out of band"

    def test_effect_magnitude_and_sign_mix(self) -> None:
        cfg = default_config()
        weights = np.array([r.weight for s in range(20) for r in _rules(s)])
        lo, hi = cfg.weight_mag_range
        assert (np.abs(weights) >= lo - 1e-9).all()
        assert (np.abs(weights) <= hi + 1e-9).all()
        pos_share = (weights > 0).mean()
        assert 0.3 < pos_share < 0.8  # both directions well represented

    def test_conditions_reference_known_fields(self) -> None:
        cfg = default_config()
        obs = {f.observable for f in cfg.factors}
        cats = {s.name for s in cfg.categoricals}
        for r in _rules(3):
            for c in r.conditions:
                if c.kind == COND_NUM:
                    assert c.field in obs and c.op in (">", "<")
                    assert c.threshold is not None and np.isfinite(c.threshold)
                elif c.kind == COND_CAT:
                    assert c.field in cats and c.op == "in"
                    pool = next(s.pool_size for s in cfg.categoricals
                                if s.name == c.field)
                    assert 1 <= len(c.values or ()) <= 4
                    assert all(0 <= v < pool for v in c.values or ())
                else:
                    assert c.kind == COND_SEQ and c.op in (">", "<")
                    assert c.threshold is not None and np.isfinite(c.threshold)

    def test_no_duplicate_field_within_rule(self) -> None:
        for seed in range(10):
            for r in _rules(seed):
                keys = [(c.kind, c.field) for c in r.conditions]
                assert len(set(keys)) == len(keys)

    def test_text_is_templated_and_nonempty(self) -> None:
        for r in _rules(11):
            assert isinstance(r.text, str) and len(r.text) > 8
            assert "\n" not in r.text


# ---------------------------------------------------------------------------
# JSON ground truth
# ---------------------------------------------------------------------------


class TestGroundTruthPayload:
    def test_payload_is_json_serializable(self) -> None:
        payload = rules_payload(_rules(5))
        text = json.dumps(payload, ensure_ascii=False)
        back = json.loads(text)
        assert back == payload

    def test_payload_shape(self) -> None:
        payload = rules_payload(_rules(5))
        assert payload["format"] == "synthfull.rules/v1"
        assert payload["pools"] == [EXPERIENCE, HELDOUT]
        for rd in payload["rules"]:
            assert set(rd) == {"rule_id", "pool", "conditions", "weight", "text"}
            for cd in rd["conditions"]:
                assert set(cd) == {"kind", "field", "op", "threshold", "values"}


# ---------------------------------------------------------------------------
# Fire-rate non-degeneracy + effect on outcomes (statistical only)
# ---------------------------------------------------------------------------


def _world_and_fired(seed: int, episodes: int = 50, per_episode: int = 2000):
    world = FullWorld(default_config(seed=seed))
    data = world.run(episodes, per_episode)
    fv = data.feature_view()
    fired = np.stack([r.fires(fv) for r in world.rules], axis=1)
    return world, data, fired


class TestFireRates:
    def test_no_degenerate_fire_rates(self) -> None:
        for seed in (1, 7, 21, 42):
            _, _, fired = _world_and_fired(seed)
            rates = fired.mean(axis=0)
            assert (rates > 0.0).all(), "a rule never fires (degenerate)"
            assert (rates < 1.0).all(), "a rule always fires (degenerate)"
            assert np.median(rates) > 0.005
            assert np.median(rates) < 0.9


class TestEffectsOnOutcome:
    def test_positive_vs_negative_effect_groups(self) -> None:
        # Large-sample: cases firing ONLY positive-effect rules must show a
        # clearly higher bad rate than cases firing ONLY negative-effect ones.
        diffs = []
        for seed in (1, 42, 99):
            world, data, fired = _world_and_fired(seed)
            y = data.ledger.outcome
            signs = np.array([1.0 if r.weight > 0 else -1.0 for r in world.rules])
            pos = fired[:, signs > 0].any(axis=1)
            neg = fired[:, signs < 0].any(axis=1)
            pos_only, neg_only = pos & ~neg, neg & ~pos
            assert pos_only.sum() > 500 and neg_only.sum() > 200
            diffs.append(y[pos_only].mean() - y[neg_only].mean())
        assert all(d > 0.02 for d in diffs), f"weak effect separation: {diffs}"

    def test_per_rule_signed_lift_majority_correct(self) -> None:
        # Per-rule sign check: for most rules, firing moves the bad rate in
        # the direction of the rule's effect sign.
        correct, total = 0, 0
        for seed in (1, 42):
            world, data, fired = _world_and_fired(seed, episodes=100)
            y = data.ledger.outcome.astype(float)
            for j, r in enumerate(world.rules):
                m = fired[:, j]
                if m.sum() < 300 or (~m).sum() < 300:
                    continue
                lift = y[m].mean() - y[~m].mean()
                correct += int(np.sign(lift) == np.sign(r.weight))
                total += 1
        assert total >= 40
        assert correct / total > 0.6, f"signed lift accuracy {correct}/{total}"

    def test_heldout_pool_active_in_generation(self) -> None:
        # Held-out rules fire on data too (active but undisclosed).
        world = FullWorld(default_config(seed=17))
        data = world.run(20, 1000)
        n_heldout = sum(1 for r in world.rules if r.pool == HELDOUT)
        exp_fired = data.truth.rule_fired[:, :-n_heldout]
        hld_fired = data.truth.rule_fired[:, -n_heldout:]
        assert (hld_fired.mean(axis=0) > 0).all()
        assert (exp_fired.mean(axis=0) > 0).all()
        assert {r.rule_id for r in world.experience_rules}.isdisjoint(
            r.rule_id for r in world.rules if r.pool == HELDOUT)
