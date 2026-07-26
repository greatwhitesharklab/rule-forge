"""Champion-challenger promotion gate tests (design doc §1.4 晋升, P2 验收 NT<5%).

Gate logic (three doors) is tested deterministically with an injected NLL
evaluator; the real evaluation path is covered by a tiny-model integration
test where the challenger is actually trained on the replay pairs.
"""

from __future__ import annotations

import json
from pathlib import Path

import torch

from _tiny import FakeTokenizer, make_tiny_qwen3
from lora.distill import DistillPair
from lora.promote import (
    GateThresholds,
    PromotionGate,
    mean_completion_nll,
    old_regime_probe,
)
from lora.train import LoraTrainConfig, train_lora

REPLAY = [DistillPair(prompt=f"replay {i}", completion=f"out {i}", source="case") for i in range(4)]
OLD = [DistillPair(prompt=f"old {i}", completion=f"old out {i}", source="case") for i in range(3)]


def stub_nll(table: dict[tuple[str | None, str], float]):
    """nll_fn(adapter_dir, pairs, tag) -> fixed value from `table`."""
    def fn(adapter_dir: str | None, pairs, tag: str) -> float:
        return table[(adapter_dir, tag)]
    return fn


def make_gate(tmp_path: Path, table, **kw) -> PromotionGate:
    return PromotionGate(
        model=make_tiny_qwen3(),
        tokenizer=FakeTokenizer(),
        log_path=tmp_path / "promotion_log.jsonl",
        nll_fn=stub_nll(table),
        **kw,
    )


class TestThreeDoors:
    def test_significant_improvement_promotes(self, tmp_path: Path) -> None:
        challenger = tmp_path / "20260726"
        challenger.mkdir()
        gate = make_gate(tmp_path, {
            (None, "replay"): 2.0, (str(challenger), "replay"): 1.0,   # -50%
            (None, "old"): 2.0, (str(challenger), "old"): 2.01,        # +0.5%
        })
        v = gate.judge(None, str(challenger), REPLAY, OLD)
        assert v.promoted
        decision = json.loads((challenger / "gate_decision.json").read_text())
        assert decision["verdict"] == "promoted"

    def test_no_improvement_rejects(self, tmp_path: Path) -> None:
        challenger = tmp_path / "20260726"
        challenger.mkdir()
        gate = make_gate(tmp_path, {
            (None, "replay"): 2.0, (str(challenger), "replay"): 1.995,  # -0.25% < 1%
            (None, "old"): 2.0, (str(challenger), "old"): 2.0,
        })
        v = gate.judge(None, str(challenger), REPLAY, OLD)
        assert not v.promoted
        assert "improvement" in v.reason
        decision = json.loads((challenger / "gate_decision.json").read_text())
        assert decision["verdict"] == "rejected"

    def test_old_regime_regression_above_5pct_rejects(self, tmp_path: Path) -> None:
        challenger = tmp_path / "20260726"
        challenger.mkdir()
        gate = make_gate(tmp_path, {
            (None, "replay"): 2.0, (str(challenger), "replay"): 1.0,   # -50%
            (None, "old"): 2.0, (str(challenger), "old"): 2.2,         # +10% > 5%
        })
        v = gate.judge(None, str(challenger), REPLAY, OLD)
        assert not v.promoted
        assert "old" in v.reason

    def test_champion_adapter_is_the_baseline_when_present(self, tmp_path: Path) -> None:
        champ, challenger = str(tmp_path / "champ"), str(tmp_path / "chall")
        Path(champ).mkdir()
        Path(challenger).mkdir()
        gate = make_gate(tmp_path, {
            (champ, "replay"): 1.0, (str(challenger), "replay"): 0.9,  # -10%
            (champ, "old"): 1.0, (str(challenger), "old"): 1.0,
        })
        v = gate.judge(champ, challenger, REPLAY, OLD)
        assert v.promoted and v.champion == champ

    def test_thresholds_are_configurable(self, tmp_path: Path) -> None:
        challenger = tmp_path / "20260726"
        challenger.mkdir()
        gate = make_gate(
            tmp_path,
            {
                (None, "replay"): 2.0, (str(challenger), "replay"): 1.9,   # -5%
                (None, "old"): 2.0, (str(challenger), "old"): 2.04,        # +2%
            },
            thresholds=GateThresholds(min_rel_improvement=0.10, max_old_regression=0.01),
        )
        v = gate.judge(None, str(challenger), REPLAY, OLD)
        assert not v.promoted  # fails both configured doors


class TestPromotionLog:
    def test_every_verdict_is_logged_as_jsonl(self, tmp_path: Path) -> None:
        challenger = tmp_path / "20260726"
        challenger.mkdir()
        gate = make_gate(tmp_path, {
            (None, "replay"): 2.0, (str(challenger), "replay"): 1.0,
            (None, "old"): 2.0, (str(challenger), "old"): 2.0,
        })
        gate.judge(None, str(challenger), REPLAY, OLD)
        gate.judge(None, str(challenger), REPLAY, OLD)
        lines = (tmp_path / "promotion_log.jsonl").read_text().strip().splitlines()
        assert len(lines) == 2
        rec = json.loads(lines[0])
        assert rec["champion"] is None
        assert rec["challenger"] == str(challenger)
        assert rec["verdict"] == "promoted"
        assert {"ts", "replay_champion_nll", "replay_challenger_nll",
                "old_champion_nll", "old_challenger_nll", "reason"} <= set(rec)


class TestOldRegimeProbe:
    def test_tagged_regimes_are_picked(self) -> None:
        pairs = [
            DistillPair("p", "c", "case", regime="2024-normal"),
            DistillPair("p", "c", "case", regime="2026-now"),
        ]
        probe = old_regime_probe(pairs, old_regimes={"2024-normal"})
        assert len(probe) == 1 and probe[0].regime == "2024-normal"

    def test_untagged_fallback_is_deterministic(self) -> None:
        pairs = [DistillPair(f"p{i}", f"c{i}", "case") for i in range(10)]
        a = old_regime_probe(pairs, fraction=0.2)
        b = old_regime_probe(pairs, fraction=0.2)
        assert a == b and 0 < len(a) < len(pairs)

    def test_judge_derives_old_subset_when_not_given(self, tmp_path: Path) -> None:
        challenger = tmp_path / "20260726"
        challenger.mkdir()
        seen: list[str] = []

        def fn(adapter_dir, pairs, tag):
            seen.append(tag)
            return 1.0 if adapter_dir else 2.0

        gate = PromotionGate(make_tiny_qwen3(), FakeTokenizer(),
                             tmp_path / "log.jsonl", nll_fn=fn)
        gate.judge(None, str(challenger), REPLAY)  # no explicit old_pairs
        assert seen == ["replay", "replay", "old", "old"]


class TestRealEvalPath:
    def test_trained_challenger_promotes_over_base(self, tmp_path: Path) -> None:
        """End-to-end on the tiny model: train on the replay pairs, then the
        gate must see a large replay-NLL drop vs the base and promote."""
        torch.manual_seed(0)
        model = make_tiny_qwen3()
        tok = FakeTokenizer()
        replay = [
            DistillPair(prompt=f"案例 {i}", completion=f"结论 {i}", source="case")
            for i in range(6)
        ]
        art = train_lora(
            model, tok, replay,
            LoraTrainConfig(lr=1e-2, max_steps=60, batch_size=2),
            output_root=tmp_path / "adapters", today="20260726",
        )
        base_nll = mean_completion_nll(model, tok, replay)
        gate = PromotionGate(model, tok, tmp_path / "promotion_log.jsonl")
        v = gate.judge(None, str(art.adapter_dir), replay, replay)
        assert v.replay_challenger_nll < base_nll
        assert v.promoted, v.reason
