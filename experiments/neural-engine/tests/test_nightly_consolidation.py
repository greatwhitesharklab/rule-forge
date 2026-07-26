"""Nightly consolidation tests (design doc §4, P1 — no LoRA/G2).

Covers: the four nightly steps' counters, incremental-only outcome reflow
(matured_view delta, never a recomputation), and the feature library's
shadow-status bookkeeping for cloud-proposed features.
"""

from __future__ import annotations

import numpy as np

from cloud import MockProvider
from cloud.contracts import Provenance, TaskResult
from embed import Embedder, SemanticMemory, case_row_from_casebook
from embed.fake import hash_encode
from nightly import FeatureLibrary, NightlyConfig, run_nightly
from scoring import RollingScorer, ScorerConfig, build_default_registry
from slots import SlotService
from synth import SyntheticWorld, default_config
from verify import PASS


def _world(episodes: int = 8, per_episode: int = 40, seed: int = 7):
    # Static regime (switch_prob=0) keeps expectations deterministic.
    return SyntheticWorld(default_config(seed=seed, switch_prob=0.0)).run(
        episodes, per_episode
    )


def _memory(tmp_path, name: str = "slots.db") -> SemanticMemory:
    service = SlotService(tmp_path / name)
    return SemanticMemory(service, Embedder(encode_fn=hash_encode))


def _scores(data):
    registry = build_default_registry(data.casebook.observable_names)
    return RollingScorer(registry, ScorerConfig(min_train_samples=40)).run(data)


class _StubCloud:
    """Contract-shaped stub provider returning caller-supplied features."""

    def __init__(self, features: list[dict[str, str]]) -> None:
        self._features = features
        self.calls = 0

    def execute(self, task) -> TaskResult:
        self.calls += 1
        return TaskResult(
            task_id=task.task_id,
            task_type=task.task_type,
            content={"features": self._features},
            provenance=Provenance(
                provider="stub",
                model="stub-1",
                model_version="v1",
                timestamp="2026-07-26T00:00:00+00:00",
                prompt_hash="0" * 64,
                cost_tokens=0,
            ),
        )


# ------------------------------------------------------------- step 1: reflow


def test_outcome_reflow_is_incremental_only(tmp_path):
    """Night t credits exactly the cases whose visible_episode == t."""
    data = _world()
    memory = _memory(tmp_path)
    scores = _scores(data)
    cloud = MockProvider()

    rep3 = run_nightly(data, memory, scores, cloud, 3)
    rep4 = run_nightly(data, memory, scores, cloud, 4)

    expected3 = {str(int(c)) for c in data.ledger.case_ids[data.ledger.visible_episode == 3]}
    expected4 = {str(int(c)) for c in data.ledger.case_ids[data.ledger.visible_episode == 4]}
    assert set(rep3.credited_case_ids) == expected3
    assert set(rep4.credited_case_ids) == expected4
    # Incremental: no overlap, and night 4 does NOT re-credit night-3 cases.
    assert expected3.isdisjoint(expected4)
    assert expected3 | expected4 == {
        str(int(c))
        for c in data.ledger.case_ids[
            (data.ledger.visible_episode == 3) | (data.ledger.visible_episode == 4)
        ]
    }


def test_credit_assignment_moves_beta_reputation(tmp_path):
    """Attributed retrievals + matured outcomes => Beta counts grow past the prior."""
    data = _world()
    memory = _memory(tmp_path)
    scores = _scores(data)
    cloud = MockProvider()
    cb = data.casebook

    # Seed slots for episode-0 cases, then retrieve them so attribution rows
    # exist for credit_assignment to fold outcomes into.
    ep0 = [int(i) for i in np.where(cb.episode == 0)[0]]
    for i in ep0:
        row = case_row_from_casebook(cb, i)
        memory.write_case_experience(row, None, str(i), str(cb.regime_tag[i]))
    for i in ep0:
        memory.retrieve_for_case(case_row_from_casebook(cb, i), str(i))

    updates = 0
    for t in (1, 2, 3):  # ep0 outcomes mature with delay 1..3
        updates += run_nightly(data, memory, scores, cloud, t).reputation_updates
    assert updates > 0
    beta_mass = [s.beta_a + s.beta_b for s in memory.service.store.all_slots()]
    # Prior mass is lambda=4; credited slots must exceed it.
    assert max(beta_mass) > 4.0


def test_credit_reflow_triggers_compete_on_conflict(tmp_path):
    """P1.1 hook: a matured bad outcome attributed to a good-leaning slot
    (fresh slots lean good under the bad-rate prior) spawns a competing slot;
    disabling the hook spawns none."""
    data = _world()
    memory = _memory(tmp_path)
    scores = _scores(data)
    cloud = MockProvider()
    cb = data.casebook

    ep0 = [int(i) for i in np.where(cb.episode == 0)[0]]
    for i in ep0:
        row = case_row_from_casebook(cb, i)
        memory.write_case_experience(row, None, str(i), str(cb.regime_tag[i]))
    for i in ep0:
        memory.retrieve_for_case(case_row_from_casebook(cb, i), str(i))

    competes = 0
    for t in (1, 2, 3):
        competes += run_nightly(data, memory, scores, cloud, t).credit_competes
    bad_ep0 = int(data.ledger.outcome[ep0].sum())
    assert bad_ep0 > 0  # fixture sanity: there are bad outcomes to conflict
    assert competes > 0
    # Compete slots carry the matured outcome in their value_text; scribe
    # writes never do (outcome is unmatured on decision day).
    comp_texts = [
        s.value_text
        for s in memory.service.store.all_slots()
        if "结局" in s.value_text
    ]
    assert len(comp_texts) == competes

    memory2 = _memory(tmp_path, "slots2.db")
    for i in ep0:
        row = case_row_from_casebook(cb, i)
        memory2.write_case_experience(row, None, str(i), str(cb.regime_tag[i]))
    for i in ep0:
        memory2.retrieve_for_case(case_row_from_casebook(cb, i), str(i))
    cfg = NightlyConfig(credit_compete=False)
    competes_off = sum(
        run_nightly(data, memory2, scores, cloud, t, config=cfg).credit_competes
        for t in (1, 2, 3)
    )
    assert competes_off == 0


# ---------------------------------------------------------- step 2: scribe


def test_scribe_counts_and_write_ops(tmp_path):
    """Anomaly screening + writing account for every one of today's cases."""
    data = _world()
    memory = _memory(tmp_path)
    scores = _scores(data)
    per_episode = 40

    rep = run_nightly(data, memory, scores, MockProvider(), 4)

    assert len(rep.scribed_case_ids) + rep.anomalies_excluded == per_episode
    assert set(rep.write_ops) == {"allocate", "reinforce", "compete"}
    assert sum(rep.write_ops.values()) == len(rep.scribed_case_ids)
    # Fresh memory + near-orthogonal hash keys => every write allocates.
    assert rep.write_ops["allocate"] == len(rep.scribed_case_ids)
    ep4_ids = {str(int(c)) for c in data.casebook.case_ids[data.casebook.episode == 4]}
    assert set(rep.scribed_case_ids) <= ep4_ids


# ----------------------------------------------- step 3: feature hypothesis


def test_feature_loop_records_shadow_and_rejected(tmp_path):
    data = _world()
    memory = _memory(tmp_path)
    scores = _scores(data)
    cloud = _StubCloud(
        [
            {
                "name": "leverage_sum",
                "expression": "df.debt_to_income_obs + df.requested_loan_to_income",
                "rationale": "stacked leverage drives default",
            },
            {
                "name": "const_zero",
                "expression": "df.months_employed * 0.0",
                "rationale": "constant carries no signal",
            },
        ]
    )
    lib = FeatureLibrary()
    cfg = NightlyConfig(hard_min_for_proposal=3, hard_proba_cap=1.0)

    rep = run_nightly(data, memory, scores, cloud, 6, config=cfg, feature_lib=lib)

    assert cloud.calls == 1
    assert rep.feature_proposed == 2
    assert rep.feature_passed == 1
    assert [r.name for r in lib.by_status("shadow")] == ["leverage_sum"]
    assert [r.name for r in lib.by_status("rejected")] == ["const_zero"]
    shadow = lib.by_status("shadow")[0]
    assert shadow.verdict == PASS
    assert shadow.quality > 0.5
    assert shadow.provenance["provider"] == "stub"
    assert rep.feature_records[0].episode == 6


def test_feature_loop_skips_cloud_when_too_few_hard_cases(tmp_path):
    data = _world()
    memory = _memory(tmp_path)
    scores = _scores(data)
    cloud = _StubCloud([])
    cfg = NightlyConfig(hard_min_for_proposal=10**9)

    rep = run_nightly(data, memory, scores, cloud, 6, config=cfg)

    assert cloud.calls == 0
    assert rep.feature_proposed == 0
    assert rep.feature_passed == 0
    assert rep.cloud_provenance is None


def test_mock_provider_feature_fails_verification(tmp_path):
    """The canned mock feature references unknown columns; the AST whitelist
    refuses it, so it is proposed but never admitted."""
    data = _world()
    memory = _memory(tmp_path)
    scores = _scores(data)
    lib = FeatureLibrary()
    cfg = NightlyConfig(hard_min_for_proposal=3, hard_proba_cap=1.0)

    rep = run_nightly(data, memory, scores, MockProvider(), 6,
                      config=cfg, feature_lib=lib)

    assert rep.feature_proposed == 1
    assert rep.feature_passed == 0
    assert lib.by_status("shadow") == []
    assert rep.cloud_provenance is not None
    assert rep.cloud_provenance["provider"] == "mock"
