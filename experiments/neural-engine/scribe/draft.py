"""Scribe data types (design doc §1.1 Scribe, §4 nightly step 2).

A ScribeCase is one decision-time case plus its (possibly unmatured) outcome.
An ExperienceDraft is what the local LLM produces from a group of similar
cases: a one-sentence Chinese rule statement plus its applicability
conditions, carrying the group's outcome lean and full provenance.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

from slots.service import Outcome


@dataclass(frozen=True)
class ScribeCase:
    """One case as induction input: observables + outcome + identity."""

    case_id: str
    row: Mapping[str, float]  # CaseBook observable-name -> value
    outcome: Outcome | None  # None = outcome not yet matured
    regime_tag: str = ""


@dataclass(frozen=True)
class ExperienceDraft:
    """One LLM-induced rule, not yet quality-gated or written to a slot.

    outcome is the group's derived lean: "good"/"bad" when the evidence is
    uniform, None when the group is mixed or outcomes are unknown (mixed
    handling rules live in writer.py).
    """

    statement: str  # one-sentence Chinese rule (slot value_text candidate)
    conditions: str  # applicability conditions, in business terms
    evidence_count: int  # supporting cases (clamped to the source group size)
    case_ids: tuple[str, ...]  # source group provenance
    outcome: Outcome | None
    mixed: bool  # True when the group contains both good and bad outcomes
    regime_tag: str = ""
