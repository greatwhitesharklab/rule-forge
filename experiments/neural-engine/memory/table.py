"""Memory table: slot schema, batch build, query, snapshot (ARCHITECTURE D2).

Slot content = prototype vector + stats (n, bad_rate EWMA, updated_at,
confidence) + human-readable pattern_desc approximated by the modal pattern
string of the samples written into the slot (no exact inverse hashing).
Phase 1 builds the table offline from the training split only, then freezes
it for backbone/read-gate training (D6).
"""

from __future__ import annotations

import pickle
import time
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

# Confidence prior strength: a slot needs ~CONFIDENCE_PRIOR_N samples to
# reach 0.5 confidence. Simple n/(n+k) form; freshness/variance terms from
# D2 are deferred to the online write gate (Phase 3).
CONFIDENCE_PRIOR_N = 20.0


@dataclass
class MemorySlot:
    proto: np.ndarray  # shape [proto_dim], L2-normalized mean of sample embeddings
    n: int = 0
    bad_rate: float = 0.0
    updated_at: float = 0.0
    confidence: float = 0.0
    pattern_desc: str = ""

    def refresh_confidence(self) -> None:
        self.confidence = self.n / (self.n + CONFIDENCE_PRIOR_N)


@dataclass
class MemoryTable:
    """Per-head slot stores; heads are independent address spaces."""

    head_names: list[str]
    num_slots: list[int]
    proto_dim: int
    slots: list[dict[int, MemorySlot]] = field(default_factory=list)

    def __post_init__(self) -> None:
        if not self.slots:
            self.slots = [{} for _ in self.head_names]

    # ------------------------------------------------------------------ build

    def build(
        self,
        slot_ids: np.ndarray,  # [n, K] int
        embeddings: np.ndarray,  # [n, proto_dim] float
        labels: np.ndarray,  # [n] 0/1
        patterns: list[dict[str, str]],
    ) -> None:
        """Batch-build from the training split. proto = L2-normalized mean of
        member embeddings; bad_rate = empirical bad share (EWMA seed); desc =
        modal pattern string of members."""
        now = time.time()
        n = slot_ids.shape[0]
        for k, head in enumerate(self.head_names):
            store = self.slots[k]
            members: dict[int, list[int]] = {}
            for i in range(n):
                members.setdefault(int(slot_ids[i, k]), []).append(i)
            for sid, idxs in members.items():
                emb = embeddings[idxs].mean(axis=0)
                norm = float(np.linalg.norm(emb))
                proto = emb / norm if norm > 0 else emb
                descs = [patterns[i][head] for i in idxs]
                # modal pattern approximates the slot's dominant cross (D2 audit)
                pattern_desc = max(set(descs), key=descs.count)
                slot = MemorySlot(
                    proto=proto.astype(np.float32),
                    n=len(idxs),
                    bad_rate=float(labels[idxs].mean()),
                    updated_at=now,
                    pattern_desc=pattern_desc,
                )
                slot.refresh_confidence()
                store[sid] = slot

    # ------------------------------------------------------------------ query

    def query(
        self, slot_ids: np.ndarray  # [n, K]
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        """Batch lookup. Returns (protos [n,K,d], confidences [n,K], ns [n,K],
        hits [n,K] bool). Missed slots return zero proto / zero stats."""
        n, k = slot_ids.shape
        protos = np.zeros((n, k, self.proto_dim), dtype=np.float32)
        confs = np.zeros((n, k), dtype=np.float32)
        ns = np.zeros((n, k), dtype=np.float32)
        hits = np.zeros((n, k), dtype=bool)
        for ki in range(k):
            store = self.slots[ki]
            for i in range(n):
                slot = store.get(int(slot_ids[i, ki]))
                if slot is not None:
                    protos[i, ki] = slot.proto
                    confs[i, ki] = slot.confidence
                    ns[i, ki] = slot.n
                    hits[i, ki] = True
        return protos, confs, ns, hits

    def get_slot(self, head_index: int, slot_id: int) -> MemorySlot | None:
        return self.slots[head_index].get(int(slot_id))

    def occupancy(self) -> dict[str, tuple[int, int]]:
        """{head: (occupied_slots, total_slots)} for reporting."""
        return {
            h: (len(self.slots[k]), self.num_slots[k])
            for k, h in enumerate(self.head_names)
        }

    # --------------------------------------------------------------- snapshot

    def save(self, path: str | Path) -> None:
        with open(path, "wb") as f:
            pickle.dump(self, f)

    @staticmethod
    def load(path: str | Path) -> "MemoryTable":
        with open(path, "rb") as f:
            table = pickle.load(f)
        if not isinstance(table, MemoryTable):
            raise TypeError(f"unexpected snapshot payload: {type(table)}")
        return table
