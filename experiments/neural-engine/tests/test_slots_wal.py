"""WAL tests: every mutation is replayable; state rebuilds from snapshot+WAL."""

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


def _run_workload(db_path) -> None:
    """A mixed write-path workload; every op must hit the WAL."""
    svc = SlotService(db_path, SlotConfig(theta=0.1))
    k1, k3 = make_key(1), make_key(3)
    _, s1 = svc.write_slot(k1, make_value(1), "rule one", "good", "case-1")
    svc.retrieve(k1, case_id="case-1")
    svc.credit_assignment("case-1", "bad", amount_weight=20.0)  # s1 leans bad now
    svc.reinforce(s1.slot_id, make_value(2), "case-2")
    svc.compete(s1.slot_id, k1, make_value(4), "rule one-b", "case-3")
    svc.allocate(k3, make_value(5), "rule three", "case-4", regime_tag="2026-h1")
    svc.set_status(s1.slot_id, "shadow")
    svc.retrieve(k3, case_id="case-5")
    svc.credit_assignment("case-5", "good", amount_weight=7.5)
    svc.persist()
    svc.close()


def _state(svc: SlotService) -> dict:
    slots = sorted(svc.store.all_slots(), key=lambda s: s.slot_id)
    return {
        "slots": [
            (
                s.slot_id,
                s.key_vec.tobytes(),
                s.value_vec.tobytes(),
                s.value_text,
                s.beta_a,
                s.beta_b,
                s.status,
                s.regime_tag,
                s.created_at,
                s.last_used_at,
                s.use_count,
                tuple(s.provenance),
            )
            for s in slots
        ],
        "attribution": [
            (r["case_id"], r["slot_id"], r["alpha"], r["a_sem"], r["a_rep"], r["a_tmp"])
            for r in svc.store.all_attributions()
        ],
        "events": [
            (s.slot_id, tuple(svc.store.recent_events(s.slot_id, 100))) for s in slots
        ],
    }


def test_rebuild_from_empty_db_plus_wal(tmp_path):
    original_db = tmp_path / "original.db"
    _run_workload(original_db)
    wal_path = str(original_db) + ".wal.jsonl"

    original = SlotService(original_db, SlotConfig(theta=0.1))
    try:
        expected = _state(original)
    finally:
        original.close()

    rebuilt = SlotService.rebuild(tmp_path / "rebuilt.db", wal_path, SlotConfig(theta=0.1))
    try:
        actual = _state(rebuilt)
    finally:
        rebuilt.close()

    assert actual == expected
    assert len(actual["slots"]) == 3  # allocate + compete + allocate
    assert len(actual["attribution"]) == 2  # two retrieves above THETA


def test_wal_records_all_write_ops(tmp_path):
    import json

    db = tmp_path / "ops.db"
    svc = SlotService(db)
    key = make_key(10)
    _, slot = svc.write_slot(key, make_value(10), "rule", "good", "case-1")
    svc.retrieve(key, case_id="case-1")
    svc.credit_assignment("case-1", "good", amount_weight=2.0)
    svc.reinforce(slot.slot_id, make_value(11), "case-2")
    svc.set_status(slot.slot_id, "cooling")
    svc.close()

    ops = [
        json.loads(line)["op"]
        for line in open(str(db) + ".wal.jsonl", encoding="utf-8")
    ]
    assert ops == ["allocate", "attribution", "credit", "reinforce", "status"]
