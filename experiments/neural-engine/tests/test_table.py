"""Memory table build / query / snapshot tests (D2)."""

import numpy as np

from memory.table import MemorySlot, MemoryTable


def make_table() -> tuple[MemoryTable, np.ndarray, np.ndarray, np.ndarray]:
    rng = np.random.default_rng(0)
    slot_ids = np.array([[1, 10], [1, 11], [2, 10], [1, 12]], dtype=np.int64)
    emb = rng.standard_normal((4, 8)).astype(np.float32)
    labels = np.array([0, 1, 0, 0])
    patterns = [
        {"h1": "a=x", "h2": "b=u"},
        {"h1": "a=x", "h2": "b=v"},
        {"h1": "a=y", "h2": "b=u"},
        {"h1": "a=z", "h2": "b=w"},
    ]
    table = MemoryTable(["h1", "h2"], [16, 16], 8)
    table.build(slot_ids, emb, labels, patterns)
    return table, slot_ids, emb, labels


def test_build_populates_slots():
    table, _, _, _ = make_table()
    assert table.occupancy() == {"h1": (2, 16), "h2": (3, 16)}
    slot = table.get_slot(0, 1)
    assert slot is not None and slot.n == 3
    assert abs(slot.bad_rate - 1 / 3) < 1e-6
    assert slot.confidence > 0
    assert slot.pattern_desc == "a=x"  # modal pattern


def test_proto_is_normalized_mean():
    table, _, emb, _ = make_table()
    slot = table.get_slot(0, 2)
    assert slot is not None and slot.n == 1
    assert np.allclose(slot.proto, emb[2] / np.linalg.norm(emb[2]), atol=1e-5)


def test_query_hit_and_miss():
    table, _, _, _ = make_table()
    sids = np.array([[1, 99], [5, 10]], dtype=np.int64)
    protos, confs, ns, hits = table.query(sids)
    assert hits.tolist() == [[True, False], [False, True]]
    assert confs[0, 1] == 0.0 and ns[1, 0] == 0.0
    assert np.all(protos[1, 0] == 0.0)
    assert protos.shape == (2, 2, 8)


def test_snapshot_roundtrip(tmp_path):
    table, _, _, _ = make_table()
    path = tmp_path / "table.pkl"
    table.save(path)
    loaded = MemoryTable.load(path)
    assert loaded.head_names == table.head_names
    s1, s2 = loaded.get_slot(0, 1), table.get_slot(0, 1)
    assert s1 is not None and s2 is not None
    assert np.allclose(s1.proto, s2.proto)
    assert (s1.n, s1.bad_rate, s1.confidence, s1.pattern_desc) == (
        s2.n, s2.bad_rate, s2.confidence, s2.pattern_desc,
    )


def test_slot_confidence_grows_with_n():
    small = MemorySlot(proto=np.zeros(4), n=5)
    big = MemorySlot(proto=np.zeros(4), n=500)
    small.refresh_confidence()
    big.refresh_confidence()
    assert 0 < small.confidence < big.confidence < 1
