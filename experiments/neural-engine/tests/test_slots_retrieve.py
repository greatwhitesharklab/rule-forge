"""Read-path tests: retrieve weight formula, THETA gate, attribution log."""

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
    cfg = SlotConfig(gamma_exp=1.0, theta=0.1)
    s = SlotService(tmp_path / "slots.db", cfg)
    yield s
    s.close()


def test_weight_formula_numeric(svc):
    key = make_key(1)
    _, slot = svc.write_slot(key, make_value(1), "r1", "good", "case-1")
    svc.store.update_betas(slot.slot_id, 3.0, 1.0)  # a_rep = 0.75 (audit only)
    svc.store.add_event(slot.slot_id, 1, "2026-01-01T00:00:00+00:00")  # window [1]

    hits = svc.retrieve(key, case_id="case-x")

    assert len(hits) == 1
    got_slot, weight = hits[0]
    assert got_slot.slot_id == slot.slot_id
    row = svc.store.attributions_for("case-x")[0]
    # P1.1: weight = a_sem * a_tmp**GAMMA (reputation-free); a_tmp = 2/3.
    assert row["a_sem"] == pytest.approx(1.0, rel=1e-5)  # cosine(key, key)
    assert row["a_rep"] == pytest.approx(0.75)  # still logged for audit
    assert row["a_tmp"] == pytest.approx(2.0 / 3.0)
    assert weight == pytest.approx(row["a_sem"] * (2.0 / 3.0), rel=1e-6)
    assert row["alpha"] == pytest.approx(weight, rel=1e-9)


def test_bad_reputation_does_not_silence_slot(svc):
    """P1.1 root cause #1: a bad-reputation slot must still be retrieved so
    memory can WARN, not only vouch."""
    key = make_key(2)
    _, slot = svc.write_slot(key, make_value(2), "r1", "good", "case-1")
    svc.store.update_betas(slot.slot_id, 1.0, 9.0)  # a_rep = 0.1 (leans bad)
    for _ in range(3):
        svc.store.add_event(slot.slot_id, 0, "2026-01-01T00:00:00+00:00")
    # weight = 1.0 * (1/5) = 0.2 > theta=0.1 — reputation plays no part.

    hits = svc.retrieve(key, case_id="case-y")

    assert len(hits) == 1
    assert hits[0][0].slot_id == slot.slot_id
    row = svc.store.attributions_for("case-y")[0]
    assert row["a_rep"] == pytest.approx(0.1)  # bad rep visible to the consumer
    assert row["alpha"] == pytest.approx(0.2)


def test_gate_filters_stale_or_irrelevant_slots(svc):
    """The gate still filters — on staleness (a_tmp) and semantic distance,
    not on reputation."""
    key = make_key(22)
    _, slot = svc.write_slot(key, make_value(22), "r1", "good", "case-1")
    for _ in range(20):
        svc.store.add_event(slot.slot_id, 0, "2026-01-01T00:00:00+00:00")
    # a_tmp = 1/22 ~= 0.045 -> weight 0.045 < theta=0.1: stale slot is silent.

    hits = svc.retrieve(key, case_id="case-z")

    assert hits == []
    assert svc.store.attributions_for("case-z") == []


def test_attribution_log_persisted(svc):
    key = make_key(3)
    _, slot = svc.write_slot(key, make_value(3), "r1", "good", "case-1")
    svc.retrieve(key, case_id="case-z")

    rows = svc.store.attributions_for("case-z")
    assert len(rows) == 1
    assert rows[0]["slot_id"] == slot.slot_id
    assert rows[0]["case_id"] == "case-z"
    # retrieval hit bumps use bookkeeping
    after = svc.store.get_slot(slot.slot_id)
    assert after.use_count == 1
    assert after.last_used_at is not None


def test_a_tmp_sliding_window(svc):
    """a_tmp = Laplace-smoothed hit-rate over the last `a_tmp_window` events."""
    key = make_key(4)
    _, slot = svc.write_slot(key, make_value(4), "r1", "good", "case-1")
    # Empty window -> uninformative 0.5.
    svc.retrieve(key, case_id="case-empty")
    assert svc.store.attributions_for("case-empty")[0]["a_tmp"] == pytest.approx(0.5)

    # 25 events: 20 good then 5 bad; window keeps the last 20 -> 15 good, 5 bad.
    ts = "2026-01-01T00:00:00+00:00"
    for _ in range(20):
        svc.store.add_event(slot.slot_id, 1, ts)
    for _ in range(5):
        svc.store.add_event(slot.slot_id, 0, ts)
    svc.retrieve(key, case_id="case-full")
    assert svc.store.attributions_for("case-full")[0]["a_tmp"] == pytest.approx(16.0 / 22.0)


def test_retrieve_ranks_multiple_slots(svc):
    k1, k2 = make_key(5), make_key(6)
    _, s1 = svc.write_slot(k1, make_value(5), "r1", "good", "case-1")
    _, s2 = svc.write_slot(k2, make_value(6), "r2", "good", "case-2")
    svc.store.update_betas(s2.slot_id, 9.0, 1.0)  # reputation is audit-only now

    hits = svc.retrieve(k2, case_id="case-m")

    ids = [s.slot_id for s, _ in hits]
    assert ids[0] == s2.slot_id  # exact semantic match ranks first
    assert s1.slot_id not in ids  # unrelated vector scores below THETA
