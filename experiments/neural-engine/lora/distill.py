"""Distill-set builder for the nightly LoRA consolidation (design §1.4, §4④).

Dataset = high-reputation slot value_texts + outcome-labeled cases rendered
as "case summary -> verdict" pairs + cloud outputs that passed local
verification (injected by the caller — the builder never talks to the cloud).

Fixed text templates (auditability: every pair is human-readable and its
provenance is one of slot/case/cloud):

    slot : prompt  = "经验复述(regime={regime_tag|general}):"
           completion = slot.value_text
    case : prompt  = "案例摘要: {summary}\n审批结论:"
           completion = "批准放款。" / "拒绝。" [+ reason]
    cloud: caller-supplied (prompt, completion) pairs, passed through.

Pairs are deduped on (prompt, completion) keeping the first occurrence, and
capped at DistillConfig.max_pairs (highest-reputation slots first).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Protocol, Sequence


class SlotLike(Protocol):
    """Read surface the builder needs from slots.store.Slot."""

    @property
    def reputation(self) -> float: ...
    value_text: str
    regime_tag: str


@dataclass(frozen=True)
class CaseRecord:
    """An outcome-labeled case distilled into a summary -> verdict pair."""

    case_id: str
    summary: str
    outcome: str  # "good" | "bad"
    reason: str = ""
    regime: str = ""


@dataclass(frozen=True)
class DistillPair:
    prompt: str
    completion: str
    source: str  # "slot" | "case" | "cloud"
    regime: str = ""


@dataclass(frozen=True)
class DistillConfig:
    reputation_threshold: float = 0.7
    max_pairs: int = 5000


SLOT_PROMPT = "经验复述(regime={regime}):"
CASE_PROMPT = "案例摘要: {summary}\n审批结论:"
_VERDICT = {"good": "批准放款。", "bad": "拒绝。"}


def _slot_pair(slot: SlotLike) -> DistillPair:
    regime = slot.regime_tag or "general"
    return DistillPair(
        prompt=SLOT_PROMPT.format(regime=regime),
        completion=slot.value_text,
        source="slot",
        regime=regime,
    )


def _case_pair(case: CaseRecord) -> DistillPair:
    verdict = _VERDICT.get(case.outcome, case.outcome)
    completion = verdict + case.reason if case.reason else verdict
    return DistillPair(
        prompt=CASE_PROMPT.format(summary=case.summary),
        completion=completion,
        source="case",
        regime=case.regime,
    )


def build_distill_set(
    slots: Sequence[SlotLike],
    cases: Sequence[CaseRecord] = (),
    cloud_pairs: Iterable[DistillPair] = (),
    config: DistillConfig = DistillConfig(),
) -> list[DistillPair]:
    """Build the nightly distill set: filtered, ordered, deduped, capped."""
    reputable = [s for s in slots if s.reputation >= config.reputation_threshold]
    reputable.sort(key=lambda s: s.reputation, reverse=True)

    candidates: list[DistillPair] = [_slot_pair(s) for s in reputable]
    candidates.extend(_case_pair(c) for c in cases)
    candidates.extend(cloud_pairs)

    seen: set[tuple[str, str]] = set()
    out: list[DistillPair] = []
    for pair in candidates:
        key = (pair.prompt, pair.completion)
        if key in seen:
            continue
        seen.add(key)
        out.append(pair)
        if len(out) >= config.max_pairs:
            break
    return out
