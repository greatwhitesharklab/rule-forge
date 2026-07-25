"""Hasher determinism and addressing tests (D1)."""

import pandas as pd
import pytest

from memory.hasher import HashHead, MultiHeadHasher

HEADS = [
    HashHead("checking", ("checking_status",), bits=10),
    HashHead("loan", ("credit_amount", "duration"), bits=12),
]


@pytest.fixture()
def hasher() -> MultiHeadHasher:
    df = pd.DataFrame(
        {
            "checking_status": ["<0", "0<=X<200", "no checking", ">=200"] * 5,
            "credit_amount": [1000.0 + i * 500 for i in range(20)],
            "duration": [12 + i for i in range(20)],
        }
    )
    return MultiHeadHasher(HEADS).fit(df, ["credit_amount", "duration"])


def test_same_input_same_slot(hasher):
    row = pd.Series(
        {"checking_status": "<0", "credit_amount": 2500.0, "duration": 24}
    )
    ids1, _ = hasher.address_row(row)
    ids2, _ = hasher.address_row(row)
    assert ids1 == ids2


def test_hash_stable_across_calls_golden(hasher):
    # Golden value pins cross-process stability (blake2b, not builtin hash).
    head = HEADS[0]
    assert hasher.slot_id(head, "checking_status=<0") == hasher.slot_id(
        head, "checking_status=<0"
    )
    slot = hasher.slot_id(head, "checking_status=<0")
    assert isinstance(slot, int) and 0 <= slot < head.num_slots
    # A literal recomputation with hashlib must agree.
    import hashlib

    digest = hashlib.blake2b(
        b"checking_status=<0", digest_size=8, key=b"checking"
    ).digest()
    assert slot == int.from_bytes(digest, "little") % head.num_slots


def test_different_patterns_diverge(hasher):
    head = HEADS[0]
    slots = {hasher.slot_id(head, f"checking_status=v{i}") for i in range(50)}
    assert len(slots) > 40  # 2^10 space, collisions should be rare


def test_batch_matches_rowwise(hasher):
    df = pd.DataFrame(
        {
            "checking_status": ["<0", "no checking"],
            "credit_amount": [2500.0, 4000.0],
            "duration": [24, 36],
        }
    )
    ids, patterns = hasher.address_batch(df)
    assert ids.shape == (2, 2)
    for i in range(2):
        row_ids, row_patterns = hasher.address_row(df.iloc[i])
        assert list(ids[i]) == [row_ids[h.name] for h in HEADS]
        assert patterns[i] == row_patterns


def test_numeric_binning_is_fitted(hasher):
    row = pd.Series(
        {"checking_status": "<0", "credit_amount": 2500.0, "duration": 24}
    )
    _, patterns = hasher.address_row(row)
    assert "bin" in patterns["loan"]
