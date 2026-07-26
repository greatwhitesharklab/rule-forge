"""Case canonicalizer: CaseBook row -> stable Chinese phrase text (§1.2).

Reproducibility = auditability: the same case row always yields the same
text, so a slot's value_text can be traced back to a deterministic rendering
of decision-time observables. Raw values never appear in the text — each
field is bucketed into a business-meaning band (e.g. "负债收入比:偏高").

The field -> Chinese label / bin table is externalized as FIELD_MAP so the
business mapping can be reviewed and extended without touching logic.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Mapping

from synth.world import CaseBook

# Outcome statements appended to the canonical text for slot value_text.
OUTCOME_TEXT: dict[str, str] = {"good": "结局:正常还款", "bad": "结局:违约"}


@dataclass(frozen=True)
class FieldSpec:
    """One observable field: business label + ascending bin edges."""

    name: str  # CaseBook observable column name
    cn_label: str  # business meaning (Chinese)
    bins: tuple[tuple[float, str], ...]  # (inclusive upper bound, band label)


INF = math.inf

# Field -> business-meaning mapping table (externalized configuration).
# Bin edges are inclusive upper bounds, ascending; the last edge must be INF.
FIELD_MAP: tuple[FieldSpec, ...] = (
    FieldSpec("income_volatility_obs", "收入波动", (
        (0.2, "极低"), (0.4, "偏低"), (0.6, "中等"), (0.8, "偏高"), (INF, "极高"))),
    FieldSpec("debt_to_income_obs", "负债收入比", (
        (0.2, "低"), (0.5, "中等"), (1.0, "偏高"), (2.0, "高"), (INF, "极高"))),
    FieldSpec("credit_history_years_reported", "信用历史", (
        (2.0, "极短"), (5.0, "较短"), (10.0, "中等"), (20.0, "较长"), (INF, "很长"))),
    FieldSpec("delinquencies_reported", "历史逾期次数", (
        (0.5, "无"), (1.5, "一次"), (2.5, "两次"), (INF, "多次"))),
    FieldSpec("months_employed", "在职时长", (
        (12.0, "不足一年"), (36.0, "一至三年"), (120.0, "三至十年"), (INF, "十年以上"))),
    FieldSpec("savings_months_obs", "储蓄覆盖月数", (
        (1.0, "不足一月"), (3.0, "一至三月"), (6.0, "三至六月"),
        (12.0, "六至十二月"), (INF, "一年以上"))),
    FieldSpec("requested_loan_to_income", "申请贷款收入比", (
        (0.3, "低"), (0.6, "中等"), (1.0, "偏高"), (2.0, "高"), (INF, "极高"))),
    FieldSpec("platform_loans_disclosed", "借贷平台数", (
        (0.5, "无"), (1.5, "一家"), (2.5, "两家"), (4.5, "数家"), (INF, "多头"))),
)

_FIELD_INDEX: dict[str, FieldSpec] = {s.name: s for s in FIELD_MAP}


def bin_label(spec: FieldSpec, value: float) -> str:
    """Band label for one field value (upper bound inclusive)."""
    for upper, label in spec.bins:
        if value <= upper:
            return label
    raise ValueError(f"{spec.name}: bins do not cover value {value!r}")


def canonicalize(case_row: Mapping[str, float]) -> str:
    """Render one case row as a stable Chinese phrase text.

    Fields render in FIELD_MAP order regardless of mapping order. Missing or
    unknown fields raise KeyError — silently dropping a field would break
    the audit trail.
    """
    unknown = set(case_row) - set(_FIELD_INDEX)
    if unknown:
        raise KeyError(f"unknown observable fields: {sorted(unknown)}")
    parts: list[str] = []
    for spec in FIELD_MAP:
        if spec.name not in case_row:
            raise KeyError(f"missing observable field: {spec.name}")
        parts.append(f"{spec.cn_label}:{bin_label(spec, float(case_row[spec.name]))}")
    return ";".join(parts)


def experience_text(case_row: Mapping[str, float], outcome: str | None) -> str:
    """Canonical case text plus an outcome statement (slot value_text form)."""
    text = canonicalize(case_row)
    if outcome is None:
        return text
    return f"{text};{OUTCOME_TEXT[outcome]}"


def case_row_from_casebook(casebook: CaseBook, index: int) -> dict[str, float]:
    """Slice one CaseBook row into an observable-name -> value mapping."""
    return {
        name: float(casebook.observables[index, i])
        for i, name in enumerate(casebook.observable_names)
    }
