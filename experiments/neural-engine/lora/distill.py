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
           -- P2.1: when `rationales` is given, the completion also carries
           "依据:{经验陈述}" — the rationale text is retrieved from the slot
           library at build time and is NOT in the prompt, so memory becomes
           a necessary information source for the completion (the P2 FAIL
           root cause was that the verdict alone was prompt-solvable).
    cloud: caller-supplied (prompt, completion) pairs, passed through.

Pairs are deduped on (prompt, completion) keeping the first occurrence, and
capped at DistillConfig.max_pairs (highest-reputation slots first).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Mapping, Protocol, Sequence


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

# P2.1 rationale template: verdict + "依据:{经验陈述}". The fixed phrase is
# written when the case retrieved NO memory hit at distill-build time — the
# model learns to say it exactly when the gate should stay shut.
RATIONALE_PREFIX = "依据:"
NO_EXPERIENCE_RATIONALE = "无既往经验"


def _slot_pair(slot: SlotLike) -> DistillPair:
    regime = slot.regime_tag or "general"
    return DistillPair(
        prompt=SLOT_PROMPT.format(regime=regime),
        completion=slot.value_text,
        source="slot",
        regime=regime,
    )


def _case_pair(
    case: CaseRecord,
    rationale: str | None = None,
    rationale_max_chars: int = 60,
) -> DistillPair:
    verdict = _VERDICT.get(case.outcome, case.outcome)
    completion = verdict + case.reason if case.reason else verdict
    if rationale is not None:
        text = rationale.strip()[:rationale_max_chars] or NO_EXPERIENCE_RATIONALE
        completion = f"{completion}{RATIONALE_PREFIX}{text}"
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
    rationales: Mapping[str, str] | None = None,
    rationale_max_chars: int = 60,
) -> list[DistillPair]:
    """Build the nightly distill set: filtered, ordered, deduped, capped.

    `rationales` (P2.1, optional): case_id -> experience statement retrieved
    from the slot library. When given, case completions gain a "依据:" tail
    (see _case_pair); when None the template is exactly the pre-P2.1 one.
    """
    reputable = [s for s in slots if s.reputation >= config.reputation_threshold]
    reputable.sort(key=lambda s: s.reputation, reverse=True)

    candidates: list[DistillPair] = [_slot_pair(s) for s in reputable]
    candidates.extend(
        _case_pair(
            c,
            None if rationales is None else rationales.get(c.case_id, ""),
            rationale_max_chars,
        )
        for c in cases
    )
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
