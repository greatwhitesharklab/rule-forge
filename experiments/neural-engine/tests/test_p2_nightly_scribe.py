"""P2 nightly scribe-mode switch tests (design doc §4 step 2).

NightlyConfig.scribe_mode selects HOW step 2 writes experience:
"canonical" (default, P1 behavior verbatim) vs "scribe" (LLM-induced
statements via write_experiences). These tests pin both modes and the
missing-scribe error path, using a fake generate_fn — no LLM loaded.
"""

from __future__ import annotations

import json

import pytest

from cloud import MockProvider
from embed import Embedder, SemanticMemory
from embed.fake import hash_encode
from nightly import NightlyConfig, run_nightly
from scoring import RollingScorer, ScorerConfig, build_default_registry
from scribe import Scribe
from slots import SlotService
from synth import SyntheticWorld, default_config

STATEMENT = "负债收入比偏高的客户违约风险显著上升"


def _world(episodes: int = 6, per_episode: int = 30, seed: int = 11):
    return SyntheticWorld(default_config(seed=seed, switch_prob=0.0)).run(
        episodes, per_episode
    )


def _memory(tmp_path) -> SemanticMemory:
    return SemanticMemory(SlotService(tmp_path / "slots.db"), Embedder(encode_fn=hash_encode))


def _scores(data):
    registry = build_default_registry(data.casebook.observable_names)
    return RollingScorer(registry, ScorerConfig(min_train_samples=30)).run(data)


def _fake_scribe() -> Scribe:
    payload = json.dumps(
        {
            "experiences": [
                {
                    "statement": STATEMENT,
                    "conditions": "负债收入比:偏高",
                    "evidence_count": 3,
                }
            ]
        },
        ensure_ascii=False,
    )
    return Scribe(lambda prompt: payload)


def test_default_mode_is_canonical_unchanged(tmp_path):
    """No scribe_mode set: step 2 writes per-case template text, one write
    per scribed case — the P1 behavior, bit for bit."""
    data = _world()
    memory = _memory(tmp_path)

    rep = run_nightly(data, memory, _scores(data), MockProvider(), 4)

    assert NightlyConfig().scribe_mode == "canonical"
    assert sum(rep.write_ops.values()) == len(rep.scribed_case_ids)
    # Template writes carry the case rendering, not an LLM statement.
    texts = [s.value_text for s in memory.service.store.all_slots()]
    assert texts and all(STATEMENT not in t for t in texts)
    assert not any(
        p.startswith("scribe:")
        for s in memory.service.store.all_slots()
        for p in s.provenance
    )


def test_scribe_mode_writes_llm_statements(tmp_path):
    """scribe_mode="scribe": step 2 routes through write_experiences with the
    injected Scribe; slots carry the induced statement + scribe provenance."""
    data = _world()
    memory = _memory(tmp_path)
    cfg = NightlyConfig(scribe_mode="scribe")

    rep = run_nightly(
        data, memory, _scores(data), MockProvider(), 4,
        config=cfg, scribe=_fake_scribe(),
    )

    slots = memory.service.store.all_slots()
    scribe_slots = [
        s for s in slots if any(p.startswith("scribe:") for p in s.provenance)
    ]
    assert scribe_slots, "no slot carries scribe provenance"
    assert all(s.value_text == STATEMENT for s in scribe_slots)
    # Provenance token keeps the source case id list auditable.
    assert any(
        any(cid in p for cid in rep.scribed_case_ids)
        for s in scribe_slots
        for p in s.provenance
    )
    # write_ops account for drafts (identical statements merge via reinforce,
    # so drafts >= distinct slots), not individual cases.
    assert sum(rep.write_ops.values()) >= len(scribe_slots) >= 1
    # scribed_case_ids still accounts for every inlier case consumed.
    assert len(rep.scribed_case_ids) + rep.anomalies_excluded == 30


def test_scribe_mode_requires_scribe_instance(tmp_path):
    data = _world()
    memory = _memory(tmp_path)
    cfg = NightlyConfig(scribe_mode="scribe")

    with pytest.raises(ValueError, match="scribe"):
        run_nightly(data, memory, _scores(data), MockProvider(), 4, config=cfg)
