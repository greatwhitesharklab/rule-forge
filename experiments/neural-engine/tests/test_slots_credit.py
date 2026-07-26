"""Reputation tests: credit_assignment weighting and outcome events."""

from __future__ import annotations

import numpy as np
import pytest

from slots import SlotConfig, SlotService


def unit(v: np.ndarray) -> np.ndarray:
    return (v / np.linalg.norm(v)).astype(np.float32)


def make_key(seed: int) -> np.ndarray:
    return unit(np.random.default_rng(seed).standard_normal(256))


def make_value(seed: int) -> np.ndarray:
    return np.random.default_rng(seed).standard_normal(1024).astype(np.float32)


@pytest.fixture
def svc(tmp_path):
    s = SlotService(tmp_path / "slots.db", SlotConfig(theta=0.1))
    yield s
    s.close()


def _attributed_slot(svc: SlotService, seed: int, case_id: str):
    """Allocate a slot and retrieve it so an attribution row exists."""
    key = make_key(seed)
    _, slot = svc.write_slot(key, make_value(seed), "r", "good", case_id)
    svc.retrieve(key, case_id=case_id)
    return slot, svc.store.attributions_for(case_id)[0]["alpha"]


def test_credit_good_increments_beta_a(svc):
    slot, alpha = _attributed_slot(svc, 1, "case-g")
    svc.credit_assignment("case-g", "good", amount_weight=100.0)
    after = svc.store.get_slot(slot.slot_id)
    assert after.beta_a == pytest.approx(1.0 + 100.0 * alpha)
    assert after.beta_b == pytest.approx(1.0)


def test_credit_bad_increments_beta_b(svc):
    slot, alpha = _attributed_slot(svc, 2, "case-b")
    svc.credit_assignment("case-b", "bad", amount_weight=50.0)
    after = svc.store.get_slot(slot.slot_id)
    assert after.beta_a == pytest.approx(1.0)
    assert after.beta_b == pytest.approx(1.0 + 50.0 * alpha)


def test_credit_default_amount_weight_is_one(svc):
    slot, alpha = _attributed_slot(svc, 3, "case-d")
    svc.credit_assignment("case-d", "good")
    after = svc.store.get_slot(slot.slot_id)
    assert after.beta_a == pytest.approx(1.0 + alpha)


def test_credit_distributes_across_multiple_attributed_slots(svc):
    # Two near-duplicate slots both attributed to the same case.
    key = make_key(4)
    near = unit(key + 0.02 * np.random.default_rng(44).standard_normal(256))
    _, s1 = svc.write_slot(key, make_value(4), "r1", "good", "case-a")
    _, s2 = svc.write_slot(near, make_value(5), "r2", "good", "case-b")
    svc.retrieve(key, case_id="case-multi")
    rows = {r["slot_id"]: r["alpha"] for r in svc.store.attributions_for("case-multi")}
    assert set(rows) == {s1.slot_id, s2.slot_id}

    svc.credit_assignment("case-multi", "good", amount_weight=10.0)

    for sid, alpha in rows.items():
        after = svc.store.get_slot(sid)
        assert after.beta_a == pytest.approx(1.0 + 10.0 * alpha)


def test_credit_appends_outcome_events_for_a_tmp(svc):
    slot, _ = _attributed_slot(svc, 6, "case-e")
    svc.credit_assignment("case-e", "bad")
    events = svc.store.recent_events(slot.slot_id, 20)
    assert events == [0]
    svc.credit_assignment("case-e", "good")
    events = svc.store.recent_events(slot.slot_id, 20)
    assert sorted(events) == [0, 1]


def test_credit_ignores_cases_without_attribution(svc):
    svc.credit_assignment("no-such-case", "good", amount_weight=100.0)  # no-op
    assert svc.store.all_slots() == []
