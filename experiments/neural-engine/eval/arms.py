"""Experiment arms for the P1 acceptance contrast (design doc §5).

Two arms share EVERYTHING except one independent variable: whether the
experience memory is writable.

  * system arm  (writable=True):  nightly consolidation runs — outcomes are
    credited into Beta reputation, new cases are scribed, the memory evolves.
  * RAG arm     (writable=False): the same retrieval is injected at decision
    time, but after the identical warmup the memory is frozen — no credit,
    no scribe, no compete. This is "retrieval-augmented" vs "writable
    experience" under one world, one seed, one GBDT.

Both arms wrap their Embedder in a shared EmbeddingCache: the canonical
text space is small (bucketed Chinese phrases), so identical texts are
encoded exactly once. The cache is a pure function wrapper — sharing it
between arms cannot leak state, it only avoids recomputation.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Sequence

import numpy as np

from embed import Embedder, SemanticMemory, case_row_from_casebook
from embed.canonicalize import canonicalize
from embed.encoder import EncodeFn
from embed.fake import hash_encode
from scoring import Policy
from slots import Slot, SlotConfig, SlotService
from slots.store import SlotStore
from synth.world import WorldData


class EmbeddingCache:
    """Dedup cache around a deterministic encode fn, keyed by (text, dim)."""

    def __init__(self, base_fn: EncodeFn = hash_encode) -> None:
        self._base = base_fn
        self._cache: dict[tuple[str, int], np.ndarray] = {}
        self.hits = 0
        self.misses = 0

    def __call__(self, texts: Sequence[str], dim: int) -> np.ndarray:
        out = np.empty((len(texts), dim), dtype=np.float32)
        pending: dict[str, list[int]] = {}
        for i, text in enumerate(texts):
            key = (text, dim)
            if key in self._cache:
                out[i] = self._cache[key]
                self.hits += 1
            elif text in pending:
                # Duplicate within the same batch: one encode, counted as a hit.
                pending[text].append(i)
                self.hits += 1
            else:
                pending[text] = [i]
                self.misses += 1
        if pending:
            fresh = self._base(list(pending), dim)
            for text, vec in zip(pending, fresh):
                self._cache[(text, dim)] = vec
                for i in pending[text]:
                    out[i] = vec
        return out


@dataclass(frozen=True)
class ArmConfig:
    """Everything an arm runs with; `writable` is THE independent variable."""

    name: str
    writable: bool
    memory_weight: float = 0.2
    policy: Policy = field(default_factory=Policy)
    slot_config: SlotConfig = field(default_factory=SlotConfig)


@dataclass
class Arm:
    config: ArmConfig
    memory: SemanticMemory


def build_arms(
    work_dir: Path | str,
    *,
    memory_weight: float = 0.2,
    encode_fn: EncodeFn | None = None,
    slot_config: SlotConfig | None = None,
    policy: Policy | None = None,
) -> tuple[Arm, Arm]:
    """Construct the two arms with identical configuration (except writability)."""
    work_dir = Path(work_dir)
    work_dir.mkdir(parents=True, exist_ok=True)
    shared_cfg = slot_config or SlotConfig()
    shared_policy = policy or Policy()
    embedder_a = Embedder(encode_fn=encode_fn)
    embedder_b = Embedder(encode_fn=encode_fn)
    arms = []
    for name, writable in (("system", True), ("rag", False)):
        service = SlotService(work_dir / f"{name}_slots.db", config=shared_cfg)
        cfg = ArmConfig(
            name=name,
            writable=writable,
            memory_weight=memory_weight,
            policy=shared_policy,
            slot_config=shared_cfg,
        )
        embedder = embedder_a if writable else embedder_b
        arms.append(Arm(config=cfg, memory=SemanticMemory(service, embedder)))
    return arms[0], arms[1]


def warmup_memory(memory: SemanticMemory, archive: WorldData) -> None:
    """Seed memory from a historical archive (outcomes fully matured).

    Per archive case: write the experience, then retrieve it (logging
    attribution) and credit the known outcome — so initial slot reputations
    reflect the archive's empirical bad rates. The new-slot prior is centered
    on the archive's global bad rate (P1.1). The sequence is deterministic,
    so both arms start from bit-identical memory content.
    """
    cb = archive.casebook
    memory.service.set_outcome_prior(float(archive.ledger.outcome.mean()))
    for i in range(len(cb.case_ids)):
        row = case_row_from_casebook(cb, i)
        case_id = f"arch-{int(cb.case_ids[i])}"
        outcome = "bad" if int(archive.ledger.outcome[i]) == 1 else "good"
        memory.write_case_experience(row, outcome, case_id,
                                     regime_tag=str(cb.regime_tag[i]))
        memory.retrieve_for_case(row, case_id)
        memory.service.credit_assignment(case_id, outcome)


def rep_bad(slot: Slot) -> float:
    """Bad-leaning reputation: beta_b / (beta_a + beta_b)."""
    return slot.beta_b / (slot.beta_a + slot.beta_b)


def memory_score(hits: list[tuple[Slot, float]], prior: float) -> float:
    """Alpha-weighted bad reputation of retrieved slots; prior when no hit."""
    if not hits:
        return prior
    denom = sum(alpha for _slot, alpha in hits)
    if denom <= 0.0:
        return prior
    return sum(alpha * rep_bad(slot) for slot, alpha in hits) / denom


def mix_scores(gbdt_proba: float, mem_score: float | None, weight: float) -> float:
    """final = (1-w)*gbdt + w*memory; degrade gracefully under missing sides."""
    gbdt_ok = np.isfinite(gbdt_proba)
    if gbdt_ok and mem_score is not None and np.isfinite(mem_score):
        return (1.0 - weight) * gbdt_proba + weight * mem_score
    if gbdt_ok:
        return float(gbdt_proba)
    if mem_score is not None and np.isfinite(mem_score):
        return float(mem_score)
    return float("nan")


def readonly_hits(
    store: SlotStore, cfg: SlotConfig, key_vec: np.ndarray, k: int | None = None
) -> list[tuple[Slot, float]]:
    """The retrieve() gating WITHOUT attribution logging / touch.

    Used by evaluation curves (retention replay) that must observe memory
    without perturbing it. Mirrors the P1.1 read path:
    weight = a_sem * a_tmp**gamma_exp, keep weight > theta (reputation-free).
    """
    out: list[tuple[Slot, float]] = []
    for slot_id, a_sem in store.search(key_vec, k or cfg.retrieve_k):
        slot = store.get_slot(slot_id)
        if slot is None:
            continue
        events = store.recent_events(slot_id, cfg.a_tmp_window)
        a_tmp = (sum(events) + 1.0) / (len(events) + 2.0)
        weight = a_sem * a_tmp**cfg.gamma_exp
        if weight > cfg.theta:
            out.append((slot, weight))
    return out


def case_text(row: dict[str, float]) -> str:
    """Canonical text of one case row (re-exported for cache-keyed reuse)."""
    return canonicalize(row)
