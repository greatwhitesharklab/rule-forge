"""Eval-arm construction tests (design doc §5 P1 contrast).

The P1 acceptance experiment contrasts a writable-memory system arm against
a frozen pure-RAG arm. These tests pin the single independent variable
(memory writability) and the embedding dedup cache.
"""

from __future__ import annotations

from dataclasses import replace
from types import SimpleNamespace

import numpy as np

from embed import Embedder
from embed.fake import hash_encode
from eval.arms import (
    EmbeddingCache,
    build_arms,
    memory_score,
    mix_scores,
    rep_bad,
    warmup_memory,
)
from synth import SyntheticWorld, default_config


def test_arms_differ_only_in_writability(tmp_path):
    """Structural assertion: the ONLY arm-level difference is `writable`
    (plus the name label); every other config field is identical."""
    system, rag = build_arms(tmp_path)

    assert system.config.writable is True
    assert rag.config.writable is False
    aligned_system = replace(system.config, name="arm", writable=False)
    aligned_rag = replace(rag.config, name="arm")
    assert aligned_system == aligned_rag
    assert system.config.slot_config == rag.config.slot_config
    assert system.config.policy == rag.config.policy
    assert system.config.memory_weight == rag.config.memory_weight


def test_warmup_gives_both_arms_identical_memory(tmp_path):
    """Same archive, same sequence => same slots and same Beta reputations."""
    system, rag = build_arms(tmp_path, encode_fn=EmbeddingCache(hash_encode))
    archive = SyntheticWorld(default_config(seed=4242, switch_prob=0.0)).run(3, 30)

    warmup_memory(system.memory, archive)
    warmup_memory(rag.memory, archive)

    sys_slots = system.memory.service.store.all_slots()
    rag_slots = rag.memory.service.store.all_slots()
    assert len(sys_slots) == len(rag_slots) > 0
    for s, r in zip(sys_slots, rag_slots):
        assert s.value_text == r.value_text
        assert s.regime_tag == r.regime_tag
        assert s.beta_a == np.float64(r.beta_a)
        assert s.beta_b == np.float64(r.beta_b)
    # Warmup credit assignment must have moved reputations off the (1,1) prior.
    assert max(s.beta_a + s.beta_b for s in sys_slots) > 2.0


def test_embedding_cache_deduplicates():
    """Canonical text space is small; identical (text, dim) pairs are encoded
    exactly once and cache reads return bit-identical vectors."""
    cache = EmbeddingCache(hash_encode)
    emb = Embedder(encode_fn=cache)

    first = emb.embed_keys(["收入波动:低", "负债收入比:高", "收入波动:低"])
    assert cache.misses == 2
    assert cache.hits == 1
    assert np.array_equal(first[0], first[2])

    emb.embed_values(["收入波动:低"])  # same text, different dim -> a miss
    assert cache.misses == 3

    # Cached result equals a direct (cache-less) encoding.
    direct = Embedder(encode_fn=hash_encode).embed_keys(["收入波动:低"])
    assert np.allclose(first[0], direct[0])


def test_memory_score_reputation_weighting_and_fallback():
    good_slot = SimpleNamespace(beta_a=9.0, beta_b=1.0)  # rep_bad = 0.1
    bad_slot = SimpleNamespace(beta_a=1.0, beta_b=9.0)  # rep_bad = 0.9

    assert rep_bad(good_slot) == 0.1
    score = memory_score([(good_slot, 0.5), (bad_slot, 0.5)], prior=0.2)
    assert score == (0.5 * 0.1 + 0.5 * 0.9) / 1.0
    # Alpha weights dominate: the good slot with higher alpha pulls the score.
    score = memory_score([(good_slot, 0.9), (bad_slot, 0.1)], prior=0.2)
    assert abs(score - (0.9 * 0.1 + 0.1 * 0.9) / 1.0) < 1e-12
    # No hit -> global prior fallback.
    assert memory_score([], prior=0.2) == 0.2


def test_mix_scores_blending_and_nan_rules():
    assert abs(mix_scores(0.2, 0.6, 0.25) - (0.75 * 0.2 + 0.25 * 0.6)) < 1e-12
    assert mix_scores(np.nan, 0.6, 0.25) == 0.6  # cold-start GBDT -> memory only
    assert mix_scores(0.2, None, 0.25) == 0.2  # no memory hit -> GBDT only
    assert np.isnan(mix_scores(np.nan, None, 0.25))  # neither -> abstain (NaN)
