"""Persistence tests: SQLite + FAISS roundtrip across service restarts."""

from __future__ import annotations

import numpy as np
import pytest

from slots import SlotConfig, SlotService
from slots.store import from_blob, to_blob


def unit(v: np.ndarray) -> np.ndarray:
    return (v / np.linalg.norm(v)).astype(np.float32)


def make_key(seed: int) -> np.ndarray:
    return unit(np.random.default_rng(seed).standard_normal(256))


def make_value(seed: int) -> np.ndarray:
    return np.random.default_rng(seed).standard_normal(1024).astype(np.float32)


def test_blob_roundtrip():
    vec = np.random.default_rng(0).standard_normal(256).astype(np.float32)
    back = from_blob(to_blob(vec))
    assert back.dtype == np.float32
    np.testing.assert_array_equal(back, vec)


def test_sqlite_faiss_roundtrip(tmp_path):
    db = tmp_path / "slots.db"
    svc = SlotService(db, SlotConfig(theta=0.1))
    keys = [make_key(i) for i in range(3)]
    ids = []
    for i, key in enumerate(keys):
        _, slot = svc.write_slot(key, make_value(i), f"rule {i}", "good", f"case-{i}")
        ids.append(slot.slot_id)
    svc.store.update_betas(ids[0], 4.0, 2.0)
    svc.persist()
    svc.close()

    # Reopen: metadata from SQLite, vector index from the persisted FAISS file.
    svc2 = SlotService(db, SlotConfig(theta=0.1))
    try:
        slots = {s.slot_id: s for s in svc2.store.all_slots()}
        assert set(slots) == set(ids)
        assert slots[ids[0]].beta_a == pytest.approx(4.0)
        assert slots[ids[0]].value_text == "rule 0"
        np.testing.assert_array_equal(slots[ids[1]].key_vec, keys[1])

        # FAISS index survived the restart and still resolves the same slot.
        hits = svc2.store.search(keys[2], k=1)
        assert hits[0][0] == ids[2]

        # Full read path works after reopen.
        results = svc2.retrieve(keys[0], case_id="case-reopen")
        assert results and results[0][0].slot_id == ids[0]
    finally:
        svc2.close()


def test_reopen_without_faiss_file_rebuilds_index(tmp_path):
    db = tmp_path / "slots.db"
    svc = SlotService(db)
    key = make_key(7)
    _, slot = svc.write_slot(key, make_value(7), "rule", "good", "case-1")
    svc.persist()
    svc.close()

    (tmp_path / "slots.db.faiss").unlink()  # lose the index, keep the metadata

    svc2 = SlotService(db)
    try:
        hits = svc2.store.search(key, k=1)
        assert hits[0][0] == slot.slot_id
    finally:
        svc2.close()
