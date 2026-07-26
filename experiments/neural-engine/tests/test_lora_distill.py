"""Distill-set builder tests (design doc §1.4 / §4 step ④).

The nightly LoRA dataset = high-reputation slot value_texts + outcome-labeled
cases ("case summary -> verdict" pairs) + cloud outputs that passed local
verification. Fixed prompt templates, deduped, capped.
"""

from __future__ import annotations

from lora.distill import (
    CaseRecord,
    DistillConfig,
    DistillPair,
    build_distill_set,
)


class FakeSlot:
    """Duck-typed stand-in for slots.store.Slot (only the read surface the
    builder needs: reputation / value_text / regime_tag)."""

    def __init__(self, beta_a: float, beta_b: float, value_text: str, regime_tag: str = ""):
        self.beta_a = beta_a
        self.beta_b = beta_b
        self.value_text = value_text
        self.regime_tag = regime_tag

    @property
    def reputation(self) -> float:
        return self.beta_a / (self.beta_a + self.beta_b)


def slot(rep: float, text: str, regime: str = "") -> FakeSlot:
    # reputation = a/(a+b); a+b = 10 keeps the numbers readable.
    return FakeSlot(rep * 10, (1 - rep) * 10, text, regime)


class TestReputationFilter:
    def test_only_slots_above_threshold_are_distilled(self) -> None:
        slots = [slot(0.9, "high rep experience"), slot(0.3, "low rep noise")]
        pairs = build_distill_set(slots, config=DistillConfig(reputation_threshold=0.7))
        assert len(pairs) == 1
        assert pairs[0].completion == "high rep experience"
        assert pairs[0].source == "slot"

    def test_threshold_is_inclusive(self) -> None:
        pairs = build_distill_set(
            [slot(0.7, "borderline")], config=DistillConfig(reputation_threshold=0.7)
        )
        assert len(pairs) == 1


class TestSlotTemplate:
    def test_prompt_carries_regime_tag(self) -> None:
        (p,) = build_distill_set([slot(0.9, "多头借贷者违约率高", regime="2025-tightening")])
        assert "2025-tightening" in p.prompt
        assert p.regime == "2025-tightening"

    def test_missing_regime_falls_back_to_general(self) -> None:
        (p,) = build_distill_set([slot(0.9, "text")])
        assert "general" in p.prompt
        assert p.regime == "general"


class TestCaseTemplate:
    def test_case_becomes_summary_to_verdict_pair(self) -> None:
        cases = [CaseRecord(case_id="c1", summary="收入稳定, 无多头", outcome="good", regime="r1")]
        (p,) = build_distill_set([], cases)
        assert "收入稳定" in p.prompt
        assert "批准" in p.completion
        assert p.source == "case"
        assert p.regime == "r1"

    def test_bad_outcome_maps_to_reject(self) -> None:
        cases = [CaseRecord(case_id="c2", summary="现金流断裂", outcome="bad")]
        (p,) = build_distill_set([], cases)
        assert "拒绝" in p.completion

    def test_reason_is_appended_when_present(self) -> None:
        cases = [
            CaseRecord(case_id="c3", summary="s", outcome="bad", reason="负债率超红线"),
        ]
        (p,) = build_distill_set([], cases)
        assert "负债率超红线" in p.completion


class TestCloudPairs:
    def test_verified_cloud_outputs_are_injected(self) -> None:
        cloud = [DistillPair(prompt="task", completion="verified output", source="cloud")]
        pairs = build_distill_set([], cloud_pairs=cloud)
        assert pairs == cloud


class TestDedupAndCap:
    def test_identical_pairs_are_deduped(self) -> None:
        slots = [slot(0.9, "same text"), slot(0.8, "same text")]
        pairs = build_distill_set(slots)
        assert len(pairs) == 1
        # first occurrence (higher reputation) wins
        assert pairs[0].completion == "same text"

    def test_max_pairs_cap(self) -> None:
        slots = [slot(0.9, f"exp {i}") for i in range(10)]
        pairs = build_distill_set(slots, config=DistillConfig(max_pairs=4))
        assert len(pairs) == 4

    def test_slots_emitted_in_reputation_order(self) -> None:
        slots = [slot(0.75, "mid"), slot(0.95, "top"), slot(0.8, "high")]
        pairs = build_distill_set(slots)
        assert [p.completion for p in pairs] == ["top", "high", "mid"]


class TestRationaleTemplate:
    """P2.1: completion carries "依据:{经验陈述}" so memory is a necessary
    information source (P2 root-cause fix). Default stays template-exact."""

    def test_default_template_has_no_rationale(self) -> None:
        cases = [CaseRecord(case_id="c1", summary="s", outcome="good")]
        (p,) = build_distill_set([], cases)
        assert "依据" not in p.completion

    def test_rationale_is_appended_after_verdict(self) -> None:
        cases = [CaseRecord(case_id="c1", summary="s", outcome="bad")]
        (p,) = build_distill_set([], cases, rationales={"c1": "多头借贷者违约率高"})
        assert p.completion == "拒绝。依据:多头借贷者违约率高"

    def test_reason_kept_before_rationale(self) -> None:
        cases = [CaseRecord(case_id="c1", summary="s", outcome="bad", reason="r")]
        (p,) = build_distill_set([], cases, rationales={"c1": "经验X"})
        assert p.completion == "拒绝。r依据:经验X"

    def test_missing_case_id_uses_fixed_no_experience_phrase(self) -> None:
        cases = [CaseRecord(case_id="c1", summary="s", outcome="good")]
        (p,) = build_distill_set([], cases, rationales={})
        assert p.completion == "批准放款。依据:无既往经验"

    def test_empty_rationale_uses_fixed_phrase(self) -> None:
        cases = [CaseRecord(case_id="c1", summary="s", outcome="good")]
        (p,) = build_distill_set([], cases, rationales={"c1": "   "})
        assert p.completion.endswith("依据:无既往经验")

    def test_rationale_is_truncated(self) -> None:
        cases = [CaseRecord(case_id="c1", summary="s", outcome="good")]
        (p,) = build_distill_set(
            [], cases, rationales={"c1": "长" * 100}, rationale_max_chars=10
        )
        assert p.completion == "批准放款。依据:" + "长" * 10

    def test_rationale_pairs_dedup_on_full_completion(self) -> None:
        cases = [
            CaseRecord(case_id="c1", summary="s", outcome="good"),
            CaseRecord(case_id="c2", summary="s", outcome="good"),
        ]
        pairs = build_distill_set(
            [], cases, rationales={"c1": "经验", "c2": "经验"}
        )
        assert len(pairs) == 1  # identical (prompt, completion) after binding
