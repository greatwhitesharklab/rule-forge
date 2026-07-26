"""P2 gate-probe data construction + metrics schema tests (no LLM).

Uses the deterministic hash encoder and a real SlotService so the probe arms
are built through the same store.search path as production retrieval:
  * related arm (case's own text key) must retrieve high-weight hits;
  * unrelated arm (off-domain text key) must retrieve strictly weaker hits;
  * summarize_gates / gate_verdict / build_p2_metrics pin the json schema;
  * plot_gate_hist emits a real PNG.
"""

from __future__ import annotations

import numpy as np

from embed import Embedder, SemanticMemory
from embed.fake import hash_encode
from eval.p2_common import (
    P2Config,
    StagedMemoryReader,
    build_p2_metrics,
    build_probe_sets,
    gate_verdict,
    hits_for_key,
    plot_gate_hist,
    summarize_gates,
)
from slots import SlotService

CASE_TEXTS = [
    "收入波动:偏低;负债收入比:低;信用历史:长;历史逾期次数:无",
    "收入波动:偏高;负债收入比:偏高;信用历史:极短;历史逾期次数:多次",
    "收入波动:中等;负债收入比:中等;信用历史:中等;历史逾期次数:一次",
]


def _memory(tmp_path) -> SemanticMemory:
    mem = SemanticMemory(
        SlotService(tmp_path / "slots.db"), Embedder(encode_fn=hash_encode)
    )
    for i, text in enumerate(CASE_TEXTS):
        key = mem.embedder.embed_keys([text])[0]
        value = mem.embedder.embed_values([text])[0]
        mem.service.write_slot(key, value, text + ";结局:违约", "bad", f"c{i}")
    return mem


def test_probe_arms_differ_in_hit_weight(tmp_path):
    mem = _memory(tmp_path)
    store = mem.service.store
    related, unrelated = build_probe_sets(mem.embedder, store, CASE_TEXTS, k=4)

    assert len(related) == len(unrelated) == len(CASE_TEXTS)
    rel_w = [h.weight for hits in related if hits for h in hits]
    unrel_w = [h.weight for hits in unrelated if hits for h in hits]
    assert rel_w, "related arm produced no hits"
    # Same text -> cosine ~1.0 for the top hit of every related sample.
    top_related = [hits[0].weight for hits in related if hits]
    assert min(top_related) > 0.99
    # Off-domain keys are near-orthogonal: strictly weaker on average.
    assert unrel_w, "unrelated arm produced no hits (store non-empty)"
    assert float(np.mean(unrel_w)) < float(np.mean(rel_w)) * 0.5


def test_hits_for_key_clamps_negative_weights(tmp_path):
    mem = _memory(tmp_path)
    store = mem.service.store
    key = mem.embedder.embed_keys(["完全无关的天气话题文本"])[0]
    hits = hits_for_key(store, key, k=8)
    assert hits and all(h.weight >= 0.0 for h in hits)


def test_summarize_gates_and_verdict():
    stats = summarize_gates([0.8, 0.9, 0.7, 0.85])
    assert stats["n"] == 4
    assert set(stats) == {"n", "mean", "p10", "p50", "p90"}
    assert 0.8 <= stats["p50"] <= 0.85
    assert summarize_gates([]) == {"n": 0, "mean": None, "p10": None,
                                   "p50": None, "p90": None}
    assert gate_verdict(0.71, 0.19) is True
    assert gate_verdict(0.69, 0.19) is False
    assert gate_verdict(0.71, 0.21) is False
    assert gate_verdict(None, 0.1) is False


def test_build_p2_metrics_schema(tmp_path):
    cfg = P2Config()
    gate = {
        "pre": {"related": summarize_gates([0.5, 0.5]),
                "unrelated": summarize_gates([0.5])},
        "post": {"related": summarize_gates([0.8, 0.9]),
                 "unrelated": summarize_gates([0.1, 0.2])},
        "hits": {"related": [8, 8], "unrelated": [8, 8]},
    }
    promotion = {
        "promoted": True, "reason": "promoted: replay +5.00%, old +1.00%",
        "replay_champion_nll": 2.0, "replay_challenger_nll": 1.9,
        "rel_improvement": 0.05,
        "old_champion_nll": 2.0, "old_challenger_nll": 2.02,
        "old_regression": 0.01,
    }
    metrics = build_p2_metrics(
        cfg, timing={"total_sec": 1.0}, training={"steps": 80},
        gate=gate, promotion=promotion,
    )

    for key in ("experiment", "config", "timing", "training", "gate",
                "promotion", "verdicts"):
        assert key in metrics
    assert metrics["gate"]["thresholds"] == {"related_min": 0.7,
                                             "unrelated_max": 0.2}
    assert metrics["verdicts"] == {"gate_alpha": "PASS",
                                   "lora_promotion": "PASS",
                                   "overall": "PASS"}
    # Failing gate arm flips the overall verdict.
    gate["post"]["related"] = summarize_gates([0.4, 0.5])
    metrics2 = build_p2_metrics(cfg, {}, {}, gate, promotion)
    assert metrics2["verdicts"]["gate_alpha"] == "FAIL"
    assert metrics2["verdicts"]["overall"] == "FAIL"
    # Rejected challenger fails the promotion criterion.
    promotion3 = {**promotion, "promoted": False}
    metrics3 = build_p2_metrics(cfg, {}, {}, gate, promotion3)
    assert metrics3["verdicts"]["lora_promotion"] == "FAIL"


def test_plot_gate_hist_writes_png(tmp_path):
    out = tmp_path / "gate_hist.png"
    rng = np.random.default_rng(0)
    plot_gate_hist(
        list(rng.uniform(0.6, 1.0, 24)),
        list(rng.uniform(0.0, 0.4, 24)),
        out,
        pre_related=[0.5] * 24,
        pre_unrelated=[0.5] * 24,
    )
    assert out.exists() and out.stat().st_size > 1000
