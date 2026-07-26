"""Rule-pool tests: pool sizes, held-out isolation, and statistical
validation that rules move outcomes in the direction their text states."""

from __future__ import annotations

import numpy as np

from synth import (
    EXPERIENCE_RULES,
    HELDOUT_RULES,
    SyntheticWorld,
    build_rule_pool,
    default_config,
)


def _static_world(seed: int = 31, episodes: int = 100, per_episode: int = 1000):
    """switch_prob=0: weights frozen, isolating rule -> outcome direction."""
    world = SyntheticWorld(default_config(seed=seed, switch_prob=0.0))
    return world, world.run(episodes, per_episode)


# ---------------------------------------------------------------------------
# Pool composition & held-out isolation
# ---------------------------------------------------------------------------


class TestRulePool:
    def test_pool_sizes_20_plus_10(self) -> None:
        assert len(EXPERIENCE_RULES) == 20
        assert len(HELDOUT_RULES) == 10
        assert len(build_rule_pool()) == 30

    def test_pool_ids_disjoint(self) -> None:
        exp_ids = {r.rule_id for r in EXPERIENCE_RULES}
        hld_ids = {r.rule_id for r in HELDOUT_RULES}
        assert exp_ids.isdisjoint(hld_ids)
        assert all(r.pool == "experience" for r in EXPERIENCE_RULES)
        assert all(r.pool == "heldout" for r in HELDOUT_RULES)

    def test_every_rule_has_human_readable_text(self) -> None:
        for r in build_rule_pool():
            assert r.text and len(r.text) >= 10
            assert r.conditions, "rule without conditions"

    def test_public_pool_excludes_heldout(self) -> None:
        world = SyntheticWorld(default_config(seed=1))
        public_ids = {r.rule_id for r in world.experience_rules}
        assert public_ids == {r.rule_id for r in EXPERIENCE_RULES}
        assert all(not rid.startswith("HLD") for rid in public_ids)

    def test_observables_do_not_expose_concept_truth(self) -> None:
        _, data = _static_world()
        obs, con = data.casebook.observables, data.truth.concepts
        # Observable field names are disjoint from concept names.
        assert set(data.casebook.observable_names).isdisjoint(data.truth.concept_names)
        # No observable column is (near-)identical to any concept column.
        for j in range(con.shape[1]):
            for k in range(obs.shape[1]):
                corr = abs(np.corrcoef(obs[:, k], con[:, j])[0, 1])
                assert corr < 0.98, f"observable {k} leaks concept {j} ({corr=})"

    def test_heldout_fire_state_not_readable_from_single_field(self) -> None:
        """Held-out rules drive the world but are undisclosed; no single
        decision-time field may directly reveal their firing state.

        Note: observables are intentional proxies of the latent factors
        (downstream consumers are supposed to learn from them), so moderate
        correlation with a rule's fire state is by design. Isolation means no
        single field is a near-perfect readout (empirical max ~0.57)."""
        _, data = _static_world()
        obs = data.casebook.observables
        for i, rid in enumerate(data.truth.rule_ids):
            if not rid.startswith("HLD"):
                continue
            fired = data.truth.rule_fired[:, i]
            if fired.mean() < 0.005:  # ultra-rare rules carry no signal anyway
                continue
            for k in range(obs.shape[1]):
                corr = abs(np.corrcoef(obs[:, k], fired.astype(float))[0, 1])
                assert corr < 0.7, f"{rid} readable from observable {k} ({corr=})"


# ---------------------------------------------------------------------------
# Rule direction: outcome statistics agree with rule statements
# ---------------------------------------------------------------------------


class TestRuleDirection:
    RULES_TO_CHECK = ("EXP-01", "EXP-02", "EXP-12", "EXP-03", "EXP-18")

    def test_rule_effect_direction_matches_statement(self) -> None:
        """For strong rules, cases firing the rule must show a bad rate that
        moves in the sign direction of the rule weight (text claims the same:
        违约倾向上升 <=> weight > 0)."""
        _, data = _static_world()
        y = data.ledger.outcome.astype(float)
        idx = {rid: i for i, rid in enumerate(data.truth.rule_ids)}
        weights = {r.rule_id: r.weight for r in EXPERIENCE_RULES}
        for rid in self.RULES_TO_CHECK:
            fired = data.truth.rule_fired[:, idx[rid]]
            assert fired.mean() > 0.01, f"{rid} fires too rarely to validate"
            diff = y[fired].mean() - y[~fired].mean()
            if weights[rid] > 0:
                assert diff > 0, f"{rid} (+{weights[rid]}) but diff={diff:+.4f}"
                assert "上升" in next(r.text for r in EXPERIENCE_RULES
                                      if r.rule_id == rid)
            else:
                assert diff < 0, f"{rid} ({weights[rid]}) but diff={diff:+.4f}"
                assert "下降" in next(r.text for r in EXPERIENCE_RULES
                                      if r.rule_id == rid)

    def test_heldout_rules_also_shape_outcomes(self) -> None:
        """Held-out rules are undisclosed but active in the world: their
        firing must still move the bad rate (otherwise P1's zero-shot
        generalization target would be vacuous)."""
        _, data = _static_world()
        y = data.ledger.outcome.astype(float)
        idx = {rid: i for i, rid in enumerate(data.truth.rule_ids)}
        for r in HELDOUT_RULES:
            fired = data.truth.rule_fired[:, idx[r.rule_id]]
            if fired.mean() < 0.01:
                continue
            diff = y[fired].mean() - y[~fired].mean()
            assert np.sign(diff) == np.sign(r.weight), (
                f"{r.rule_id} weight {r.weight:+.2f} but diff={diff:+.4f}")
