"""Rule pools for the CLAB-lite world (design doc §5 P0).

A rule maps a conjunction of concept conditions to a bad-logit adjustment —
exactly the shape of a slot's value_text experience statement (§1.2), so
pool rules can later be written into slots verbatim.

Pools:
  * experience pool (20): the ground-truth experience downstream consumers
    are allowed to discover/learn;
  * held-out pool (10): active in world generation but never disclosed to
    any consumer; P1 measures zero-shot generalization against them.

Rule weights are the regime-drift surface: a regime switch mutates a subset
of weights (decay / boost / flip), recorded in the regime history.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

import numpy as np

EXPERIENCE = "experience"
HELDOUT = "heldout"


@dataclass(frozen=True)
class Condition:
    """One concept comparison, e.g. cash_flow_strain > 0.7."""

    concept: str
    op: Literal[">", "<"]
    threshold: float

    def mask(self, concepts: np.ndarray, concept_index: dict[str, int]) -> np.ndarray:
        col = concepts[:, concept_index[self.concept]]
        return col > self.threshold if self.op == ">" else col < self.threshold


@dataclass(frozen=True)
class Rule:
    """Concept conditions -> bad-logit adjustment, with human-readable text.

    `weight` is the BASE weight (regime 0). SyntheticWorld keeps its own
    mutable weight vector for drift; Rule objects stay immutable.
    """

    rule_id: str
    pool: str  # EXPERIENCE | HELDOUT
    conditions: tuple[Condition, ...]
    weight: float  # positive = raises bad logit, negative = lowers it
    text: str  # value_text form: human-readable experience statement

    def fires(self, concepts: np.ndarray, concept_index: dict[str, int]) -> np.ndarray:
        """Boolean mask [n_cases] of cases where all conditions hold."""
        m = np.ones(concepts.shape[0], dtype=bool)
        for cond in self.conditions:
            m &= cond.mask(concepts, concept_index)
        return m


def _c(concept: str, op: Literal[">", "<"], threshold: float) -> Condition:
    return Condition(concept, op, threshold)


def _r(rid: str, pool: str, weight: float, text: str, *conds: Condition) -> Rule:
    return Rule(rid, pool, tuple(conds), weight, text)


# fmt: off
EXPERIENCE_RULES: tuple[Rule, ...] = (
    _r("EXP-01", EXPERIENCE, +0.55, "现金流紧张且多头借贷的申请人违约倾向显著上升",
       _c("cash_flow_strain", ">", 0.70), _c("over_leverage", ">", 0.60)),
    _r("EXP-02", EXPERIENCE, +0.45, "信用资质差(历史短/逾期多)的申请人违约倾向上升",
       _c("credit_worthiness", "<", 0.30)),
    _r("EXP-03", EXPERIENCE, -0.50, "偿付能力强的申请人违约倾向显著下降",
       _c("repayment_capacity", ">", 0.70)),
    _r("EXP-04", EXPERIENCE, +0.35, "现金流极度紧张的申请人违约倾向上升",
       _c("cash_flow_strain", ">", 0.75)),
    _r("EXP-05", EXPERIENCE, +0.40, "多头借贷严重的申请人违约倾向上升",
       _c("over_leverage", ">", 0.75)),
    _r("EXP-06", EXPERIENCE, -0.45, "画像稳定且偿付能力强的申请人是优质客群",
       _c("profile_stability", ">", 0.70), _c("repayment_capacity", ">", 0.60)),
    _r("EXP-07", EXPERIENCE, +0.30, "借贷激进的申请人违约倾向上升",
       _c("borrowing_aggressiveness", ">", 0.70)),
    _r("EXP-08", EXPERIENCE, -0.40, "信用资质好的申请人违约倾向下降",
       _c("credit_worthiness", ">", 0.70)),
    _r("EXP-09", EXPERIENCE, +0.50, "现金流紧张且偿付能力弱的申请人风险叠加",
       _c("cash_flow_strain", ">", 0.60), _c("repayment_capacity", "<", 0.40)),
    _r("EXP-10", EXPERIENCE, +0.30, "画像稳定性差的申请人违约倾向上升",
       _c("profile_stability", "<", 0.30)),
    _r("EXP-11", EXPERIENCE, +0.45, "多头借贷且借贷激进的申请人违约倾向上升",
       _c("over_leverage", ">", 0.60), _c("borrowing_aggressiveness", ">", 0.60)),
    _r("EXP-12", EXPERIENCE, +0.40, "偿付能力弱的申请人违约倾向上升",
       _c("repayment_capacity", "<", 0.30)),
    _r("EXP-13", EXPERIENCE, +0.45, "信用资质偏弱叠加现金流紧张,违约倾向上升",
       _c("credit_worthiness", "<", 0.40), _c("cash_flow_strain", ">", 0.60)),
    _r("EXP-14", EXPERIENCE, -0.30, "画像稳定的申请人违约倾向下降",
       _c("profile_stability", ">", 0.65)),
    _r("EXP-15", EXPERIENCE, +0.40, "借贷激进且信用资质偏弱的申请人违约倾向上升",
       _c("borrowing_aggressiveness", ">", 0.65), _c("credit_worthiness", "<", 0.45)),
    _r("EXP-16", EXPERIENCE, -0.35, "现金流宽松且偿付能力强的申请人违约倾向下降",
       _c("cash_flow_strain", "<", 0.30), _c("repayment_capacity", ">", 0.60)),
    _r("EXP-17", EXPERIENCE, -0.35, "无多头借贷且信用资质好的申请人违约倾向下降",
       _c("over_leverage", "<", 0.30), _c("credit_worthiness", ">", 0.55)),
    _r("EXP-18", EXPERIENCE, -0.55, "偿付能力与信用资质双优的申请人违约倾向显著下降",
       _c("repayment_capacity", ">", 0.60), _c("credit_worthiness", ">", 0.60)),
    _r("EXP-19", EXPERIENCE, +0.40, "现金流紧张且画像不稳定的申请人违约倾向上升",
       _c("cash_flow_strain", ">", 0.65), _c("profile_stability", "<", 0.40)),
    _r("EXP-20", EXPERIENCE, -0.25, "借贷保守的申请人违约倾向下降",
       _c("borrowing_aggressiveness", "<", 0.30)),
)

HELDOUT_RULES: tuple[Rule, ...] = (
    _r("HLD-01", HELDOUT, +0.60, "(held-out)极端多头借贷叠加现金流极度紧张,违约倾向大幅上升",
       _c("over_leverage", ">", 0.80), _c("cash_flow_strain", ">", 0.70)),
    _r("HLD-02", HELDOUT, -0.50, "(held-out)偿付能力极强且画像稳定的申请人几乎不违约",
       _c("repayment_capacity", ">", 0.80), _c("profile_stability", ">", 0.65)),
    _r("HLD-03", HELDOUT, +0.55, "(held-out)借贷极度激进且信用资质差的申请人违约倾向大幅上升",
       _c("borrowing_aggressiveness", ">", 0.80), _c("credit_worthiness", "<", 0.35)),
    _r("HLD-04", HELDOUT, +0.35, "(held-out)信用资质极差的申请人违约倾向进一步上升",
       _c("credit_worthiness", "<", 0.22)),
    _r("HLD-05", HELDOUT, -0.40, "(held-out)现金流极度宽松的申请人违约倾向下降",
       _c("cash_flow_strain", "<", 0.22)),
    _r("HLD-06", HELDOUT, +0.45, "(held-out)多头借贷严重且画像不稳定的申请人违约倾向上升",
       _c("over_leverage", ">", 0.70), _c("profile_stability", "<", 0.35)),
    _r("HLD-07", HELDOUT, -0.45, "(held-out)信用资质好且借贷保守的申请人是隐藏优质客群",
       _c("credit_worthiness", ">", 0.75), _c("borrowing_aggressiveness", "<", 0.40)),
    _r("HLD-08", HELDOUT, +0.50, "(held-out)偿付能力弱且借贷激进的申请人风险叠加",
       _c("repayment_capacity", "<", 0.35), _c("borrowing_aggressiveness", ">", 0.65)),
    _r("HLD-09", HELDOUT, +0.30, "(held-out)画像极不稳定的申请人违约倾向上升",
       _c("profile_stability", "<", 0.22)),
    _r("HLD-10", HELDOUT, -0.35, "(held-out)偿付能力强且无多头借贷的申请人违约倾向下降",
       _c("repayment_capacity", ">", 0.65), _c("over_leverage", "<", 0.35)),
)
# fmt: on


def build_rule_pool() -> tuple[Rule, ...]:
    """Full pool: 20 experience rules followed by 10 held-out rules."""
    return EXPERIENCE_RULES + HELDOUT_RULES
