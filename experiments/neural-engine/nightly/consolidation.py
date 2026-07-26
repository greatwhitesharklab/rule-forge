"""Nightly consolidation job (design doc §4, P1 edition — no LoRA, no G2).

Four deterministic steps per night t:

1. Outcome reflow: cases whose outcome newly matured at t (the
   ``matured_view`` increment, ``visible_episode == t`` — never a rescan of
   all matured cases) are folded into slot Beta reputation via
   ``credit_assignment``.
2. Scribe: episode t's cases are anomaly-screened (IsolationForest on the
   decision-time observables) and the inliers are written as experience
   (outcome unknown at decision day -> ``None``; allocate/reinforce/compete
   is dispatched by the slot service).
3. Feature hypothesis loop: matured bad cases the GBDT scored low
   ("unexplained bads", top-k) drive a G1 ``feature_proposal`` prompt to the
   cloud; each returned expression goes through the sandbox + backtest
   verifier and lands in the shadow FeatureLibrary.
4. Reporting: counts for every step, with provenance (credited case ids,
   scribed case ids, cloud provenance) so the night is replayable.

The job is a pure orchestrator: it never mutates the world, the scorer or
the world ledger; the only state it advances is the memory (and the
caller-supplied feature library).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import numpy as np
from sklearn.ensemble import IsolationForest

from cloud import CloudLLM
from embed import SemanticMemory, case_row_from_casebook
from embed.canonicalize import canonicalize, experience_text
from nightly.feature_lib import FeatureLibrary, FeatureRecord
from prompts import make_prompt
from scoring import RollingResult
from scribe import Scribe, ScribeCase, write_experiences
from slots.service import Outcome
from synth.world import WorldData
from verify import PASS, backtest_frame, verify_feature


@dataclass(frozen=True)
class NightlyConfig:
    """Knobs for the P1 nightly job."""

    anomaly_contamination: float = 0.05
    anomaly_min_samples: int = 20  # below this, skip screening (write all)
    iso_random_state: int = 20260726
    hard_top_k: int = 20  # unexplained-bad list size
    hard_min_for_proposal: int = 5  # below this, don't spend a cloud call
    hard_proba_cap: float = 0.35  # "GBDT scored low" threshold
    max_features_per_night: int = 5
    # P1.1 root cause #3: at credit time, spawn a competing slot when the
    # matured outcome contradicts an attributed slot's lean (scribe writes
    # carry no outcome, so write-path compete can never fire otherwise).
    credit_compete: bool = True
    # P2: step-2 write path switch. "canonical" (default) reproduces the P1
    # per-case template write verbatim; "scribe" routes the inlier cases
    # through write_experiences(mode="scribe") — the local LLM induces
    # rule statements and those become the slot value_texts.
    scribe_mode: str = "canonical"


@dataclass(frozen=True)
class NightlyReport:
    """Auditable outcome of one nightly run."""

    episode: int
    credited_case_ids: tuple[str, ...]  # step 1 provenance
    reputation_updates: int  # slot Beta updates applied in step 1
    credit_competes: int  # step 1: competing slots spawned on outcome conflict
    write_ops: dict[str, int]  # step 2: allocate / reinforce / compete
    scribed_case_ids: tuple[str, ...]  # step 2 provenance
    anomalies_excluded: int
    hard_cases: int  # step 3: unexplained bads found
    feature_proposed: int
    feature_passed: int
    feature_records: tuple[FeatureRecord, ...] = ()
    cloud_provenance: dict[str, Any] | None = None


def _proba_by_case(scorer: RollingResult) -> dict[int, float]:
    """Decision-time GBDT P(bad) per case id (NaN for cold-start episodes)."""
    out: dict[int, float] = {}
    for batch in scorer.batches:
        if batch.proba is None:
            continue
        for cid, p in zip(batch.case_ids, batch.proba):
            out[int(cid)] = float(p)
    return out


def _reflow_outcomes(
    world: WorldData, memory: SemanticMemory, episode: int, cfg: NightlyConfig
) -> tuple[tuple[str, ...], int, int]:
    """Step 1: credit exactly the cases maturing at `episode` (incremental).

    P1.1 compete hook: when a matured outcome contradicts the lean of a slot
    the case was attributed to (same rep_gap rule as the write path), a
    competing slot is spawned BEFORE the blame lands — the scribe writes
    outcome-less cases, so this is the only place compete can fire.
    """
    ledger = world.ledger
    cb = world.casebook
    new_mask = ledger.visible_episode == episode
    credited: list[str] = []
    updates = 0
    competes = 0
    store = memory.service.store
    for cid, outcome in zip(ledger.case_ids[new_mask], ledger.outcome[new_mask]):
        case_id = str(int(cid))
        out_str: Outcome = "bad" if int(outcome) == 1 else "good"
        credited.append(case_id)
        attrs = store.attributions_for(case_id)
        updates += len(attrs)
        if cfg.credit_compete and attrs:
            rivals = [
                s
                for r in attrs
                if (s := store.get_slot(r["slot_id"])) is not None
                and memory.service.conflicts(s, out_str)
            ]
            if rivals:
                row = case_row_from_casebook(cb, int(cid))
                key = memory.embedder.embed_keys([canonicalize(row)])[0]
                text = experience_text(row, out_str)
                value = memory.embedder.embed_values([text])[0]
                tag = str(cb.regime_tag[int(cid)])
                for slot in rivals:
                    memory.service.compete(
                        slot.slot_id, key, value, text, case_id, regime_tag=tag
                    )
                    competes += 1
        memory.service.credit_assignment(case_id, out_str)
    return tuple(credited), updates, competes


def _scribe(
    world: WorldData,
    memory: SemanticMemory,
    episode: int,
    cfg: NightlyConfig,
    scribe: Scribe | None = None,
) -> tuple[tuple[str, ...], dict[str, int], int]:
    """Step 2: anomaly-screen today's cases, write the inliers as experience.

    canonical mode: one template write per inlier case (P1 verbatim).
    scribe mode: the inliers go through write_experiences(mode="scribe") —
    the LLM induces statements; write_ops then count DRAFT writes while
    scribed_case_ids still accounts for every inlier case consumed.
    """
    cb = world.casebook
    idx = np.where(cb.episode == episode)[0]
    obs = cb.observables[idx]
    if len(idx) >= cfg.anomaly_min_samples:
        iso = IsolationForest(
            contamination=cfg.anomaly_contamination,
            random_state=cfg.iso_random_state,
            n_estimators=60,
        )
        inlier = iso.fit_predict(obs) == 1
    else:
        inlier = np.ones(len(idx), dtype=bool)

    if cfg.scribe_mode == "scribe":
        cases = [
            ScribeCase(
                case_id=str(int(cb.case_ids[i])),
                row=case_row_from_casebook(cb, int(i)),
                outcome=None,  # outcome not yet matured on decision day
                regime_tag=str(cb.regime_tag[i]),
            )
            for i, keep in zip(idx, inlier)
            if keep
        ]
        report = write_experiences(cases, memory, mode="scribe", scribe=scribe)
        scribed = tuple(c.case_id for c in cases)
        return scribed, dict(report.write_ops), int((~inlier).sum())

    write_ops = {"allocate": 0, "reinforce": 0, "compete": 0}
    scribed: list[str] = []
    for i, keep in zip(idx, inlier):
        if not keep:
            continue
        case_id = str(int(cb.case_ids[i]))
        op, _slot = memory.write_case_experience(
            case_row_from_casebook(cb, int(i)),
            None,  # outcome not yet matured on decision day
            case_id,
            regime_tag=str(cb.regime_tag[i]),
        )
        write_ops[op] += 1
        scribed.append(case_id)
    return tuple(scribed), write_ops, int((~inlier).sum())


def _feature_loop(
    world: WorldData,
    scorer: RollingResult,
    cloud: CloudLLM,
    episode: int,
    cfg: NightlyConfig,
    lib: FeatureLibrary,
) -> tuple[int, int, int, tuple[FeatureRecord, ...], dict[str, Any] | None]:
    """Step 3: unexplained bads -> G1 prompt -> cloud -> sandbox verify."""
    ledger = world.ledger
    proba = _proba_by_case(scorer)
    matured_bad = np.where(ledger.visible_mask(episode) & (ledger.outcome == 1))[0]
    hard = [
        (int(ledger.case_ids[i]), proba[int(ledger.case_ids[i])])
        for i in matured_bad
        if int(ledger.case_ids[i]) in proba
        and np.isfinite(proba[int(ledger.case_ids[i])])
        and proba[int(ledger.case_ids[i])] <= cfg.hard_proba_cap
    ]
    hard.sort(key=lambda kv: kv[1])
    hard = hard[: cfg.hard_top_k]
    if len(hard) < cfg.hard_min_for_proposal:
        return len(hard), 0, 0, (), None

    cb = world.casebook
    names = list(cb.observable_names)
    profiles = [
        {
            name: round(float(cb.observables[cid, j]), 4)
            for j, name in enumerate(names)
        }
        for cid, _p in hard
    ]
    payload = {
        "context": {
            "case_profiles": profiles,
            "existing_features": [r.name for r in lib.records],
            "dead_ends": [
                f"{r.name}: {r.verdict} ({r.quality:.2f})"
                for r in lib.by_status("rejected")
            ],
        },
        "constraints": {
            "max_features": cfg.max_features_per_night,
            "must_be_executable": "python",
            "no_future_info": True,
        },
    }
    prompt = make_prompt("feature_proposal", payload)
    result = cloud.execute(prompt.package)

    df, labels = backtest_frame(world, episode)
    records: list[FeatureRecord] = []
    proposed = 0
    passed = 0
    for f in result.content.get("features", [])[: cfg.max_features_per_night]:
        proposed += 1
        verdict = verify_feature(str(f["expression"]), df, labels)
        if verdict.status == PASS:
            passed += 1
        records.append(
            lib.add(
                name=str(f["name"]),
                expression=str(f["expression"]),
                rationale=str(f["rationale"]),
                episode=episode,
                verdict=verdict,
                provenance=result.provenance.as_dict(),
            )
        )
    return len(hard), proposed, passed, tuple(records), result.provenance.as_dict()


def run_nightly(
    world: WorldData,
    memory: SemanticMemory,
    scorer: RollingResult,
    cloud: CloudLLM,
    episode: int,
    *,
    config: NightlyConfig | None = None,
    feature_lib: FeatureLibrary | None = None,
    scribe: Scribe | None = None,
) -> NightlyReport:
    """Run one night of P1 consolidation; returns the auditable report.

    `scribe` is only consulted when config.scribe_mode == "scribe" (P2);
    the default canonical path never touches it.
    """
    cfg = config or NightlyConfig()
    lib = feature_lib if feature_lib is not None else FeatureLibrary()

    # P1.1: feed the rolling global bad rate into the new-slot prior.
    matured = world.ledger.visible_mask(episode)
    if matured.any():
        memory.service.set_outcome_prior(float(world.ledger.outcome[matured].mean()))

    credited, updates, competes = _reflow_outcomes(world, memory, episode, cfg)
    scribed, write_ops, anomalies = _scribe(world, memory, episode, cfg, scribe)
    hard_n, proposed, passed, records, provenance = _feature_loop(
        world, scorer, cloud, episode, cfg, lib
    )
    return NightlyReport(
        episode=episode,
        credited_case_ids=credited,
        reputation_updates=updates,
        credit_competes=competes,
        write_ops=write_ops,
        scribed_case_ids=scribed,
        anomalies_excluded=anomalies,
        hard_cases=hard_n,
        feature_proposed=proposed,
        feature_passed=passed,
        feature_records=records,
        cloud_provenance=provenance,
    )
