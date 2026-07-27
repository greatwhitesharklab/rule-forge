"""Append-only JSONL write-ahead log for slot-table mutations.

Every mutating op (allocate / reinforce / compete / credit / status /
attribution) is appended before it is considered done, so the full memory
state can be rebuilt from an empty database by replaying the WAL (design doc
§7 item 4: regulatory reproducibility of memory state at any point in time).
"""

from __future__ import annotations

import base64
import json
from pathlib import Path
from typing import Any, Iterator

import numpy as np

# WAL vector format version. v1 (legacy): vectors are JSON float arrays
# (~19 bytes/dimension — the 12GB LendingClub WAL). v2: float16 bytes,
# base64-encoded (~2.7 bytes/dimension, ~7x smaller). Precision trade-off:
# float16 round-trip error is <= half ulp (~1e-3 absolute for the value
# ranges we store: unit-norm keys, standard-normal-ish value_vecs), far
# below the 0.85 similarity gate and any retrieval-scoring sensitivity, so
# replayed state is numerically equivalent for every downstream decision.
# v1 records are auto-detected on decode (list vs str) and still replay.
FORMAT_VERSION = 2


def encode_vector(vec: np.ndarray) -> str:
    """Encode a vector as base64(float16 bytes) — WAL format v2."""
    arr = np.asarray(vec, dtype=np.float32).astype(np.float16)
    return base64.b64encode(arr.tobytes()).decode("ascii")


def decode_vector(value: Any) -> np.ndarray:
    """Decode a WAL vector field, auto-sniffing the format:
    str -> v2 base64 float16; list -> v1 JSON float array (lossless)."""
    if isinstance(value, str):
        raw = base64.b64decode(value)
        return np.frombuffer(raw, dtype=np.float16).astype(np.float32)
    return np.asarray(value, dtype=np.float32)


class WalWriter:
    def __init__(self, path: str | Path):
        self.path = Path(path)
        self._fh = self.path.open("a", encoding="utf-8")

    def append(self, record: dict[str, Any]) -> None:
        self._fh.write(json.dumps(record) + "\n")
        self._fh.flush()

    def close(self) -> None:
        self._fh.close()


class WalReader:
    def __init__(self, path: str | Path):
        self.path = Path(path)

    def __iter__(self) -> Iterator[dict[str, Any]]:
        with self.path.open("r", encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if line:
                    yield json.loads(line)
