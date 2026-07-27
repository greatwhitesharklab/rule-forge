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
        "meta": [
            (
                s.slot_id,
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
        "key_vecs": [s.key_vec for s in slots],
        "value_vecs": [s.value_vec for s in slots],
        "attribution": [
            (r["case_id"], r["slot_id"], r["alpha"], r["a_sem"], r["a_rep"], r["a_tmp"])
            for r in svc.store.all_attributions()
        ],
        "events": [
            (s.slot_id, tuple(svc.store.recent_events(s.slot_id, 100))) for s in slots
        ],
    }


def assert_state_equal(actual: dict, expected: dict) -> None:
    assert actual["meta"] == expected["meta"]
    assert actual["attribution"] == expected["attribution"]
    assert actual["events"] == expected["events"]
    # Vectors ride the WAL as float16 (format v2): replay is approximate
    # within half-ulp (~1e-3), not bit-exact. See slots/wal.py FORMAT_VERSION.
    for a, e in zip(actual["key_vecs"], expected["key_vecs"]):
        np.testing.assert_allclose(a, e, rtol=1e-3, atol=3e-3)
    for a, e in zip(actual["value_vecs"], expected["value_vecs"]):
        np.testing.assert_allclose(a, e, rtol=1e-3, atol=3e-3)


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

    assert_state_equal(actual, expected)
    assert len(actual["meta"]) == 3  # allocate + compete + allocate
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


# ------------------------------------------- vector compression (WAL format v2)

import json

from slots.wal import FORMAT_VERSION, decode_vector, encode_vector


def test_vector_codec_roundtrip():
    vec = make_value(42)
    back = decode_vector(encode_vector(vec))
    assert back.dtype == np.float32
    # float16 round-trip: within half-ulp for standard-normal magnitudes.
    np.testing.assert_allclose(back, vec, rtol=1e-3, atol=3e-3)


def test_new_format_record_roundtrip(tmp_path):
    db = tmp_path / "v2.db"
    svc = SlotService(db)
    key, value = make_key(1), make_value(1)
    _, slot = svc.write_slot(key, value, "rule", "good", "case-1")
    svc.reinforce(slot.slot_id, make_value(2), "case-2")
    svc.close()

    recs = [
        json.loads(line) for line in open(str(db) + ".wal.jsonl", encoding="utf-8")
    ]
    assert recs[0]["fmt"] == FORMAT_VERSION
    assert isinstance(recs[0]["key_vec"], str)  # base64, not a JSON array
    assert isinstance(recs[0]["value_vec"], str)
    np.testing.assert_allclose(
        decode_vector(recs[0]["key_vec"]), key, rtol=1e-3, atol=3e-3
    )
    np.testing.assert_allclose(
        decode_vector(recs[0]["value_vec"]), value, rtol=1e-3, atol=3e-3
    )
    assert isinstance(recs[1]["value_vec"], str)  # reinforce record too
    np.testing.assert_allclose(
        decode_vector(recs[1]["value_vec"]), make_value(2), rtol=1e-3, atol=3e-3
    )


def test_legacy_v1_records_still_replay(tmp_path):
    """v1 WAL records (JSON float arrays, pre-compression) must still rebuild."""
    key, value = make_key(7), make_value(7)
    ts = "2026-01-01T00:00:00+00:00"
    legacy = [
        {  # v1 allocate: no fmt field, JSON float arrays, no betas
            "op": "allocate",
            "ts": ts,
            "key_vec": [float(x) for x in key],
            "value_vec": [float(x) for x in value],
            "value_text": "legacy rule",
            "case_id": "c1",
            "regime_tag": "",
        },
        {
            "op": "reinforce",
            "ts": ts,
            "slot_id": 1,
            "value_vec": [float(x) for x in make_value(8)],
            "case_id": "c2",
        },
        {"op": "status", "ts": ts, "slot_id": 1, "status": "active"},
    ]
    wal_path = tmp_path / "legacy.wal.jsonl"
    with wal_path.open("w", encoding="utf-8") as fh:
        for rec in legacy:
            fh.write(json.dumps(rec) + "\n")

    svc = SlotService.rebuild(tmp_path / "legacy.db", wal_path)
    try:
        slot = svc.store.get_slot(1)
        # v1 was lossless float32, so legacy replay stays (near) bit-exact.
        np.testing.assert_allclose(slot.key_vec, key, rtol=1e-6, atol=1e-6)
        expected = (0.9 * value + 0.1 * make_value(8)).astype(np.float32)
        np.testing.assert_allclose(slot.value_vec, expected, rtol=1e-5, atol=1e-6)
        assert slot.status == "active"
        assert slot.provenance == ["c1", "c2"]
    finally:
        svc.close()


def test_wal_size_benchmark(capsys):
    """1000 allocate records: v2 (float16+base64) vs v1 (JSON float array)."""
    key, value = make_key(1), make_value(1)
    base = {
        "op": "allocate",
        "ts": "2026-01-01T00:00:00+00:00",
        "value_text": "rule",
        "case_id": "c",
        "regime_tag": "",
        "beta_a": 3.6,
        "beta_b": 0.4,
    }
    v1 = base | {
        "key_vec": [float(x) for x in key],
        "value_vec": [float(x) for x in value],
    }
    v2 = base | {
        "fmt": FORMAT_VERSION,
        "key_vec": encode_vector(key),
        "value_vec": encode_vector(value),
    }
    n = 1000
    v1_bytes = len(json.dumps(v1)) * n
    v2_bytes = len(json.dumps(v2)) * n
    print(
        f"\nWAL size, {n} allocate records: v1={v1_bytes}B v2={v2_bytes}B"
        f" ratio={v2_bytes / v1_bytes:.3f}"
    )
    assert v2_bytes < v1_bytes * 0.2


def test_wal_store_vectors_false(tmp_path):
    """Lazy mode: records carry no vectors; from-empty rebuild must refuse."""
    db = tmp_path / "lean.db"
    svc = SlotService(db, SlotConfig(wal_store_vectors=False))
    _, slot = svc.write_slot(make_key(1), make_value(1), "rule", "good", "case-1")
    svc.reinforce(slot.slot_id, make_value(2), "case-2")
    svc.set_status(slot.slot_id, "active")
    svc.close()

    recs = [
        json.loads(line) for line in open(str(db) + ".wal.jsonl", encoding="utf-8")
    ]
    assert "key_vec" not in recs[0] and "value_vec" not in recs[0]
    assert "value_vec" not in recs[1]
    assert recs[2]["op"] == "status"  # non-vector ops unaffected

    with pytest.raises(ValueError, match="wal_store_vectors=False"):
        SlotService.rebuild(tmp_path / "rebuilt.db", str(db) + ".wal.jsonl")
