"""P1 acceptance experiment harness (design doc §5).

Runs both arms over the same synthetic world episode by episode:

  day   t: each case gets GBDT P(bad) (shared RollingScorer, time-safe) mixed
           with the arm's memory score; a three-way decision is booked.
  night t: the WRITABLE arm runs the full nightly consolidation (outcome
           reflow, scribe, feature loop); the frozen RAG arm does nothing.

Ground-truth labels are touched only for retrospective evaluation (curves),
never for decisions — the same rule the RollingScorer enforces structurally.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from sklearn.metrics import roc_auc_score

from cloud import CloudLLM, MockProvider
from embed import case_row_from_casebook
from embed.canonicalize import canonicalize
from eval.arms import (
    Arm,
    EmbeddingCache,
    build_arms,
    memory_score,
    mix_scores,
    readonly_hits,
    warmup_memory,
)
from eval.curves import Alignment, reputation_alignment
from nightly import FeatureLibrary, NightlyReport, run_nightly
from scoring import RollingResult, RollingScorer, build_default_registry, decide
from synth import SyntheticWorld, WorldData, default_config


@dataclass(frozen=True)
class ExperimentConfig:
    episodes: int = 100
    per_episode: int = 100
    seed: int = 20260726
    memory_weight: float = 0.2
    warmup_episodes: int = 5
    archive_seed_offset: int = 991991
    zs_window: int = 3
    replay_cap: int = 150  # fixed regime-0 replay set size for L(t)
    align_min_count: int = 2  # min world cases per (profile, regime) truth entry


@dataclass
class EpisodeRecord:
    episode: int
    case_ids: np.ndarray
    final_proba: np.ndarray  # NaN where both sides abstained
    decisions: np.ndarray
    memory_hits: int = 0  # cases with >= 1 retrieved slot (diagnostic)


@dataclass
class ArmResult:
    name: str
    episodes: list[EpisodeRecord] = field(default_factory=list)
    nightly_reports: list[NightlyReport] = field(default_factory=list)
    retention: list[float] = field(default_factory=list)  # L(t) per night
    retention_auc: list[float] = field(default_factory=list)  # raw replay AUC
    alignment: list[Alignment] = field(default_factory=list)


@dataclass
class ExperimentResult:
    config: ExperimentConfig
    arms: dict[str, ArmResult]
    world: WorldData
    switch_episodes: list[int]
    priors: np.ndarray
    proba_by_case: dict[int, float]
    feature_records: tuple
    embed_cache: EmbeddingCache
    runtime_seconds: float


def _global_priors(data: WorldData) -> np.ndarray:
    """prior[t] = bad rate of outcomes visible before episode t (time-safe)."""
    episodes = int(data.casebook.episode.max()) + 1
    priors = np.full(episodes, np.nan)
    for t in range(episodes):
        mask = data.ledger.visible_episode <= t - 1
        if mask.any():
            priors[t] = float(data.ledger.outcome[mask].mean())
    return priors


def _canonical_texts(data: WorldData, prefix: str = "") -> dict[str, str]:
    """case-id-string -> canonical text, for alignment profile lookups."""
    cb = data.casebook
    out: dict[str, str] = {}
    for i in range(len(cb.case_ids)):
        out[f"{prefix}{int(cb.case_ids[i])}"] = _text_of(data, i)
    return out


def _text_of(data: WorldData, i: int) -> str:
    return canonicalize(case_row_from_casebook(data.casebook, i))


def _profile_truth(data: WorldData) -> tuple[dict[tuple[str, str], tuple[float, int]], float]:
    """(canonical_text, regime_tag) -> (empirical bad rate, n); global rate."""
    cb = data.casebook
    sums: dict[tuple[str, str], list[int]] = {}
    for i in range(len(cb.case_ids)):
        key = (_text_of(data, i), str(cb.regime_tag[i]))
        bucket = sums.setdefault(key, [0, 0])
        bucket[0] += int(data.ledger.outcome[i])
        bucket[1] += 1
    truth = {k: (v[0] / v[1], v[1]) for k, v in sums.items()}
    return truth, float(data.ledger.outcome.mean())


def _run_episode(
    arm: Arm, data: WorldData, proba_by_case: dict[int, float], t: int, prior: float
) -> EpisodeRecord:
    cb = data.casebook
    idx = np.where(cb.episode == t)[0]
    finals = np.full(len(idx), np.nan)
    hits_n = 0
    w = arm.config.memory_weight
    for j, i in enumerate(idx):
        i = int(i)
        cid = int(cb.case_ids[i])
        hits = arm.memory.retrieve_for_case(case_row_from_casebook(cb, i), str(cid))
        hits_n += 1 if hits else 0
        mem = memory_score(hits, prior)
        finals[j] = mix_scores(proba_by_case.get(cid, float("nan")), mem, w)
    decisions = decide(finals, arm.config.policy)
    return EpisodeRecord(t, cb.case_ids[idx], finals, decisions, hits_n)


def _replay_set(data: WorldData, cfg: ExperimentConfig) -> tuple[np.ndarray, np.ndarray]:
    """Fixed regime-0 replay set for the retention curve L(t)."""
    first_switch = cfg.episodes
    if data.regimes:
        first_switch = data.regimes[0].episode
    idx = np.where(data.casebook.episode < first_switch)[0]
    if len(idx) > cfg.replay_cap:
        idx = idx[np.linspace(0, len(idx) - 1, cfg.replay_cap).astype(int)]
    labels = data.ledger.outcome[idx].astype(np.float64)
    return idx, labels


def _replay_scores(arm: Arm, data: WorldData, replay_idx: np.ndarray,
                   fallback: float) -> np.ndarray:
    store = arm.memory.service.store
    cfg = arm.config.slot_config
    embedder = arm.memory.embedder
    scores = np.empty(len(replay_idx))
    for j, i in enumerate(replay_idx):
        key = embedder.embed_keys([_text_of(data, int(i))])[0]
        scores[j] = memory_score(readonly_hits(store, cfg, key), fallback)
    return scores


def _replay_auc(labels: np.ndarray, scores: np.ndarray) -> float | None:
    if len(np.unique(labels)) < 2 or len(np.unique(scores)) < 2:
        return None
    return float(roc_auc_score(labels, scores))


def run_experiment(
    cfg: ExperimentConfig,
    work_dir: Path | str,
    *,
    cloud: CloudLLM | None = None,
    encode_fn=None,
) -> ExperimentResult:
    """Run the two-arm P1 experiment end to end."""
    started = time.monotonic()
    work_dir = Path(work_dir)
    data = SyntheticWorld(default_config(seed=cfg.seed)).run(cfg.episodes, cfg.per_episode)
    archive = SyntheticWorld(
        default_config(seed=cfg.seed + cfg.archive_seed_offset)
    ).run(cfg.warmup_episodes, cfg.per_episode)

    registry = build_default_registry(data.casebook.observable_names)
    rolling: RollingResult = RollingScorer(registry).run(data)
    proba_by_case = {
        int(cid): float(p)
        for b in rolling.batches if b.proba is not None
        for cid, p in zip(b.case_ids, b.proba)
    }

    cache = EmbeddingCache(encode_fn) if encode_fn is not None else EmbeddingCache()
    system, rag = build_arms(work_dir, memory_weight=cfg.memory_weight,
                             encode_fn=cache)
    warmup_memory(system.memory, archive)
    warmup_memory(rag.memory, archive)

    cloud = cloud or MockProvider()
    feature_lib = FeatureLibrary()
    priors = _global_priors(data)
    switches = [e.episode for e in data.regimes]
    truth, global_rate = _profile_truth(data)
    texts = _canonical_texts(data)
    texts.update(_canonical_texts(archive, prefix="arch-"))
    replay_idx, replay_labels = _replay_set(data, cfg)
    replay_fallback = float(replay_labels.mean()) if len(replay_labels) else 0.5

    arms = {a.config.name: a for a in (system, rag)}
    results = {name: ArmResult(name) for name in arms}
    ref_auc = {
        name: _replay_auc(replay_labels, _replay_scores(a, data, replay_idx, replay_fallback))
        for name, a in arms.items()
    }

    for t in range(cfg.episodes):
        prior = priors[t]
        for name, arm in arms.items():
            results[name].episodes.append(
                _run_episode(arm, data, proba_by_case, t, prior)
            )
        # Night: only the writable arm consolidates.
        report = run_nightly(data, system.memory, rolling, cloud, t,
                             feature_lib=feature_lib)
        results["system"].nightly_reports.append(report)
        # Curves observed after the night.
        for name, arm in arms.items():
            auc = _replay_auc(
                replay_labels, _replay_scores(arm, data, replay_idx, replay_fallback)
            )
            results[name].retention_auc.append(auc if auc is not None else float("nan"))
            base = ref_auc[name]
            results[name].retention.append(
                auc / base if (auc is not None and base) else float("nan")
            )
        slots = system.memory.service.store.all_slots()
        profiles = {s.slot_id: texts.get(s.provenance[0]) for s in slots if s.provenance}
        profiles = {k: v for k, v in profiles.items() if v is not None}
        results["system"].alignment.append(
            reputation_alignment(slots, profiles, truth, global_rate,
                                 cfg.align_min_count)
        )

    for arm in arms.values():
        arm.memory.service.persist()
        arm.memory.service.close()

    return ExperimentResult(
        config=cfg,
        arms=results,
        world=data,
        switch_episodes=switches,
        priors=priors,
        proba_by_case=proba_by_case,
        feature_records=feature_lib.records,
        embed_cache=cache,
        runtime_seconds=time.monotonic() - started,
    )
