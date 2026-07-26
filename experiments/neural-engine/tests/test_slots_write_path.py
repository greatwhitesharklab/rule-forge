"""Write-path tests: allocate / reinforce / compete (design doc §1.2)."""

from __future__ import annotations

import numpy as np
import pytest

from slots import SlotService


def unit(v: np.ndarray) -> np.ndarray:
    return (v / np.linalg.norm(v)).astype(np.float32)


def make_key(seed: int) -> np.ndarray:
    return unit(np.random.default_rng(seed).standard_normal(256))


def make_value(seed: int) -> np.ndarray:
    return np.random.default_rng(seed).standard_normal(1024).astype(np.float32)


def similar(key: np.ndarray, seed: int, eps: float = 0.02) -> np.ndarray:
    return unit(key + eps * np.random.default_rng(seed).standard_normal(256))


@pytest.fixture
def svc(tmp_path):
    s = SlotService(tmp_path / "slots.db")
    yield s
    s.close()


# ------------------------------------------------------------------ allocate


def test_allocate_prior_status_and_provenance(svc):
    op, slot = svc.write_slot(make_key(1), make_value(1), "rule one", "good", "case-1")
    assert op == "allocate"
    assert slot.status == "candidate"
    # P1.1: Beta(λ(1-p), λp) prior, default λ=4, p=0.1 -> (3.6, 0.4).
    assert slot.beta_a == pytest.approx(3.6)
    assert slot.beta_b == pytest.approx(0.4)
    assert slot.provenance == ["case-1"]
    assert slot.key_vec.shape == (256,)
    assert slot.key_vec.dtype == np.float32
    assert slot.value_vec.shape == (1024,)
    assert slot.use_count == 0
    # key_vec is stored L2-normalized
    assert float(np.linalg.norm(slot.key_vec)) == pytest.approx(1.0, rel=1e-5)


def test_allocate_prior_injection(svc):
    """set_outcome_prior re-centers the new-slot prior (Bayesian shrinkage)."""
    svc.set_outcome_prior(0.25)
    _, slot = svc.write_slot(make_key(2), make_value(2), "r", "good", "case-1")
    assert slot.beta_a == pytest.approx(4.0 * 0.75)
    assert slot.beta_b == pytest.approx(4.0 * 0.25)
    with pytest.raises(ValueError):
        svc.set_outcome_prior(1.5)


def test_dissimilar_case_allocates_new_slot(svc):
    svc.write_slot(make_key(1), make_value(1), "r1", "good", "case-1")
    op, slot = svc.write_slot(make_key(2), make_value(2), "r2", "good", "case-2")
    assert op == "allocate"
    assert len(svc.store.all_slots()) == 2


# ----------------------------------------------------------------- reinforce


def test_reinforce_ema_and_provenance(svc):
    key = make_key(10)
    v1, v2 = make_value(10), make_value(11)
    _, slot = svc.write_slot(key, v1, "r1", "good", "case-1")

    op, slot2 = svc.write_slot(similar(key, 99), v2, "r1b", "good", "case-2")

    assert op == "reinforce"
    assert slot2.slot_id == slot.slot_id
    assert len(svc.store.all_slots()) == 1
    expected = (0.9 * v1 + 0.1 * v2).astype(np.float32)  # EMA alpha=0.1
    np.testing.assert_allclose(
        svc.store.get_slot(slot.slot_id).value_vec, expected, rtol=1e-5, atol=1e-6
    )
    assert svc.store.get_slot(slot.slot_id).provenance == ["case-1", "case-2"]


def test_reinforce_does_not_change_reputation_or_status(svc):
    key = make_key(12)
    _, slot = svc.write_slot(key, make_value(12), "r1", "good", "case-1")
    svc.write_slot(similar(key, 98), make_value(13), "r1b", "bad", "case-2")
    after = svc.store.get_slot(slot.slot_id)
    assert after.beta_a == pytest.approx(3.6)  # reputation only via credit
    assert after.beta_b == pytest.approx(0.4)
    assert after.status == "candidate"


# -------------------------------------------------------------------- compete


def test_compete_triggers_on_outcome_conflict(svc):
    key = make_key(20)
    _, slot = svc.write_slot(key, make_value(20), "r1", "good", "case-1")
    # Drive reputation bad-leaning: rep = 1/6 <= 0.5 - rep_gap(0.2).
    svc.store.update_betas(slot.slot_id, 1.0, 5.0)

    op, comp = svc.write_slot(similar(key, 97), make_value(21), "r2", "good", "case-2")

    assert op == "compete"
    assert comp.slot_id != slot.slot_id
    assert len(svc.store.all_slots()) == 2  # competing slots coexist
    assert comp.status == "candidate"
    assert comp.beta_a == pytest.approx(3.6)  # competing slot starts at prior
    assert comp.beta_b == pytest.approx(0.4)
    assert comp.provenance == ["case-2"]


def test_compete_triggers_for_good_leaning_slot_with_bad_outcome(svc):
    key = make_key(22)
    _, slot = svc.write_slot(key, make_value(22), "r1", "good", "case-1")
    svc.store.update_betas(slot.slot_id, 5.0, 1.0)  # rep = 5/6 >= 0.7

    op, _ = svc.write_slot(similar(key, 96), make_value(23), "r2", "bad", "case-2")
    assert op == "compete"


def test_fresh_slot_prior_lean_conflicts_with_bad_outcome(svc):
    key = make_key(24)
    _, slot = svc.write_slot(key, make_value(24), "r1", "good", "case-1")
    # P1.1: fresh slot rep = 1 - p = 0.9 -> leans GOOD under the 0.5-centered
    # rep_gap rule, so a first bad outcome contradicts it -> compete.
    op, comp = svc.write_slot(similar(key, 95), make_value(25), "r1b", "bad", "case-2")
    assert op == "compete"
    assert comp.slot_id != slot.slot_id


def test_fresh_slot_reinforces_when_outcome_agrees(svc):
    key = make_key(25)
    _, slot = svc.write_slot(key, make_value(25), "r1", "good", "case-1")
    # Fresh slot leans good (prior); a good outcome agrees -> reinforce.
    op, slot2 = svc.write_slot(similar(key, 94), make_value(26), "r1b", "good", "case-2")
    assert op == "reinforce"
    assert slot2.slot_id == slot.slot_id


def test_no_compete_when_outcome_agrees_with_lean(svc):
    key = make_key(26)
    _, slot = svc.write_slot(key, make_value(26), "r1", "good", "case-1")
    svc.store.update_betas(slot.slot_id, 1.0, 5.0)  # leans bad, new case is bad
    op, _ = svc.write_slot(similar(key, 94), make_value(27), "r1b", "bad", "case-2")
    assert op == "reinforce"


# ------------------------------------------------------------------ status API


def test_status_transition_api(svc):
    _, slot = svc.write_slot(make_key(30), make_value(30), "r1", "good", "case-1")
    svc.set_status(slot.slot_id, "shadow")
    assert svc.store.get_slot(slot.slot_id).status == "shadow"
    svc.set_status(slot.slot_id, "active")
    assert svc.store.get_slot(slot.slot_id).status == "active"


def test_status_rejects_invalid_value(svc):
    _, slot = svc.write_slot(make_key(31), make_value(31), "r1", "good", "case-1")
    with pytest.raises(ValueError):
        svc.set_status(slot.slot_id, "promoted")
