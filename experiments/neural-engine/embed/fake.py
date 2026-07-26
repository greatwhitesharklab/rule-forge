"""Deterministic offline encoder for tests and demos (no model dependency).

Hash projection: each (text, dim) pair seeds a numpy Generator from
SHA-256, producing a pseudo-random Gaussian vector. Same text -> same
vector, always; unrelated texts are near-orthogonal. This is NOT a semantic
encoder — it exists so unit tests can exercise the full embed -> slots
pipeline deterministically without loading Qwen3.
"""

from __future__ import annotations

import hashlib
from typing import Sequence

import numpy as np


def _seed(text: str, dim: int, salt: str) -> int:
    digest = hashlib.sha256(f"{salt}|{dim}|{text}".encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big")


def hash_encode(texts: Sequence[str], dim: int, salt: str = "embed-fake") -> np.ndarray:
    """Deterministic hash projection: (texts, dim) -> float32 [N, dim]."""
    out = np.empty((len(texts), dim), dtype=np.float32)
    for i, text in enumerate(texts):
        rng = np.random.default_rng(_seed(text, dim, salt))
        out[i] = rng.standard_normal(dim).astype(np.float32)
    return out
