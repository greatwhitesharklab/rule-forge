"""Multi-head deterministic hash addressing (ARCHITECTURE D1).

Each head covers a feature subset (single feature or a second-order cross).
Feature values are joined into a pattern string and hashed with blake2b
(stable across processes, unlike builtin ``hash``) into ``2**bits`` slots.
Numeric features are discretized with quantile bins fitted on the training
split only, so the same input always maps to the same slot id — the
reproducibility required for audit replay.
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass, field

import numpy as np
import pandas as pd


@dataclass(frozen=True)
class HashHead:
    """One addressing head: a named feature subset with its own slot space."""

    name: str
    features: tuple[str, ...]
    bits: int = 12

    @property
    def num_slots(self) -> int:
        return 1 << self.bits


@dataclass
class MultiHeadHasher:
    """Fits numeric bin edges, then maps rows to per-head slot ids."""

    heads: list[HashHead]
    n_bins: int = 4
    numeric_features: list[str] = field(default_factory=list)
    bin_edges_: dict[str, np.ndarray] = field(default_factory=dict)

    def fit(self, df: pd.DataFrame, numeric_features: list[str]) -> "MultiHeadHasher":
        self.numeric_features = list(numeric_features)
        for f in self.numeric_features:
            if f not in self.all_features():
                continue
            quantiles = np.linspace(0.0, 1.0, self.n_bins + 1)[1:-1]
            edges = np.unique(np.quantile(df[f].to_numpy(dtype=float), quantiles))
            self.bin_edges_[f] = edges
        return self

    def all_features(self) -> list[str]:
        seen: list[str] = []
        for h in self.heads:
            for f in h.features:
                if f not in seen:
                    seen.append(f)
        return seen

    def _value_token(self, feature: str, value: object) -> str:
        """Render a feature value as a stable token (numeric values are binned)."""
        if feature in self.bin_edges_:
            b = int(np.searchsorted(self.bin_edges_[feature], float(value)))
            return f"bin{b}"
        return str(value)

    def pattern_string(self, head: HashHead, row: pd.Series) -> str:
        """Human-readable, hash-stable pattern for one head on one row."""
        return ";".join(f"{f}={self._value_token(f, row[f])}" for f in head.features)

    def slot_id(self, head: HashHead, pattern: str) -> int:
        # Head name is mixed in as blake2b key so identical patterns in
        # different heads do not correlate; digest_size fixed for stability.
        digest = hashlib.blake2b(
            pattern.encode("utf-8"), digest_size=8, key=head.name.encode("utf-8")
        ).digest()
        return int.from_bytes(digest, "little") % head.num_slots

    def address_row(self, row: pd.Series) -> tuple[dict[str, int], dict[str, str]]:
        """Return ({head: slot_id}, {head: pattern_string}) for one row."""
        ids: dict[str, int] = {}
        patterns: dict[str, str] = {}
        for h in self.heads:
            p = self.pattern_string(h, row)
            ids[h.name] = self.slot_id(h, p)
            patterns[h.name] = p
        return ids, patterns

    def address_batch(
        self, df: pd.DataFrame
    ) -> tuple[np.ndarray, list[dict[str, str]]]:
        """Vectorized-ish batch addressing.

        Returns (slot_ids [n, K] int64 in head order, per-row pattern dicts).
        """
        n = len(df)
        ids = np.zeros((n, len(self.heads)), dtype=np.int64)
        patterns: list[dict[str, str]] = []
        for i in range(n):
            row = df.iloc[i]
            row_ids, row_patterns = self.address_row(row)
            ids[i] = [row_ids[h.name] for h in self.heads]
            patterns.append(row_patterns)
        return ids, patterns
