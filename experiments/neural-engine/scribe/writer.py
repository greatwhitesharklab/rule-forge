"""Scribe write adapter + unified experience-writing entry (§4 step 2).

ScribeWriter turns quality-gated ExperienceDrafts into slot writes:

  * value_text is the LLM-induced statement — NOT the canonical template;
  * key AND value vectors both encode the statement, so retrieval finds the
    rule by its semantic content rather than by one case's rendering;
  * provenance is a single token ``scribe:id1,id2,...`` carrying the mode
    tag and the source case id list (capped, see MAX_PROVENANCE_IDS), routed
    through write_slot's case_id so it lands in the WAL and survives replay;
  * mixed-evidence drafts (same profile head, both outcomes) are written
    with outcome=None: the group itself could not decide a direction, so
    the slot enters neutral — write-path compete can never fire on it — and
    later Beta credit_assignment arbitrates which lean survives.

write_experiences() is the single entry the nightly job will switch on:
mode="canonical" reproduces the P1 behavior verbatim (per-case template
text via SemanticMemory.write_case_experience); mode="scribe" runs the full
LLM pipeline. Degeneration never raises: an unparseable LLM yields zero
drafts, zero writes, and a counted parse_failed_groups.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal, Sequence

from embed import SemanticMemory
from embed.canonicalize import FIELD_MAP

from .draft import ExperienceDraft, ScribeCase
from .induce import Scribe

MIN_STATEMENT_LEN = 8  # chars; a bare field label is not an induced rule
MAX_STATEMENT_LEN = 120  # one auditable sentence, not an essay
BUSINESS_TERMS = tuple(spec.cn_label for spec in FIELD_MAP)
MAX_PROVENANCE_IDS = 8
SCRIBE_TAG = "scribe"

WriteMode = Literal["canonical", "scribe"]


def check_statement(statement: str) -> str | None:
    """Quality gate for slot-bound statements: rejection reason or None.

    Slot raw material quality is part of the immune system: a statement that
    is empty, too short/long, or cites no known business field never reaches
    the slot table.
    """
    s = statement.strip()
    if not s:
        return "empty"
    if len(s) < MIN_STATEMENT_LEN:
        return "too_short"
    if len(s) > MAX_STATEMENT_LEN:
        return "too_long"
    if not any(term in s for term in BUSINESS_TERMS):
        return "no_business_term"
    return None


def _provenance_token(draft: ExperienceDraft) -> str:
    """'scribe:c1,c2,...,c8(+N)' — mode tag + capped source case id list."""
    ids = draft.case_ids
    shown = ",".join(ids[:MAX_PROVENANCE_IDS])
    suffix = (
        f"(+{len(ids) - MAX_PROVENANCE_IDS})"
        if len(ids) > MAX_PROVENANCE_IDS
        else ""
    )
    return f"{SCRIBE_TAG}:{shown}{suffix}"


@dataclass
class WriteReport:
    """Auditable outcome of one write_experiences()/ScribeWriter.write call."""

    mode: str
    cases: int = 0
    drafts: int = 0
    written: int = 0
    dropped: dict[str, int] = field(default_factory=dict)  # gate reason -> n
    write_ops: dict[str, int] = field(
        default_factory=lambda: {"allocate": 0, "reinforce": 0, "compete": 0}
    )
    parse_failed_groups: int = 0


class ScribeWriter:
    """ExperienceDraft -> SemanticMemory write path (scribe mode)."""

    def __init__(self, memory: SemanticMemory) -> None:
        self.memory = memory

    def write(
        self,
        drafts: Sequence[ExperienceDraft],
        report: WriteReport | None = None,
    ) -> WriteReport:
        report = report or WriteReport(mode=SCRIBE_TAG)
        for draft in drafts:
            report.drafts += 1
            reason = check_statement(draft.statement)
            if reason is not None:
                report.dropped[reason] = report.dropped.get(reason, 0) + 1
                continue
            key = self.memory.embedder.embed_keys([draft.statement])[0]
            value = self.memory.embedder.embed_values([draft.statement])[0]
            op, _slot = self.memory.service.write_slot(
                key,
                value,
                draft.statement,
                draft.outcome,  # None for mixed/unknown evidence — see module docstring
                _provenance_token(draft),
                regime_tag=draft.regime_tag,
            )
            report.write_ops[op] += 1
            report.written += 1
        return report


def write_experiences(
    cases: Sequence[ScribeCase],
    memory: SemanticMemory,
    *,
    mode: WriteMode = "canonical",
    scribe: Scribe | None = None,
) -> WriteReport:
    """Unified nightly write entry; the mode switch lives here (not in nightly).

    canonical: per-case template write, exactly the P1 behavior.
    scribe:    group -> LLM induce -> quality gate -> statement write.
    """
    if mode == "canonical":
        report = WriteReport(mode=mode, cases=len(cases))
        for case in cases:
            op, _slot = memory.write_case_experience(
                case.row, case.outcome, case.case_id, regime_tag=case.regime_tag
            )
            report.write_ops[op] += 1
            report.written += 1
        return report
    if mode == "scribe":
        if scribe is None:
            raise ValueError("mode='scribe' requires a Scribe instance")
        drafts = scribe.induce(cases)
        report = WriteReport(
            mode=mode,
            cases=len(cases),
            parse_failed_groups=scribe.last_report.failed_groups,
        )
        return ScribeWriter(memory).write(drafts, report)
    raise ValueError(f"unknown mode: {mode!r}")
