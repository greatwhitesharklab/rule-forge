"""Scribe quality-gate tests: the immune system for LLM-produced drafts.

check_statement returns a rejection reason string, or None when the statement
may become slot value_text. Rules: non-empty, length within [8, 120] chars,
and at least one known business-field term (FIELD_MAP Chinese labels).
"""

from __future__ import annotations

from scribe import check_statement


class TestQualityGate:
    def test_valid_statement_passes(self) -> None:
        assert check_statement("负债收入比偏高的申请人违约风险显著上升") is None

    def test_empty_rejected(self) -> None:
        assert check_statement("") == "empty"
        assert check_statement("   \n  ") == "empty"

    def test_too_short_rejected(self) -> None:
        # 5 chars: a bare field name is a label, not an induced rule.
        assert check_statement("负债收入比") == "too_short"

    def test_too_long_rejected(self) -> None:
        statement = "负债收入比" + "很" * 200
        assert check_statement(statement) == "too_long"

    def test_no_business_term_rejected(self) -> None:
        assert check_statement("申请人通常更偏好期限较短的产品方案") == "no_business_term"

    def test_every_field_label_counts_as_business_term(self) -> None:
        for term in ("收入波动", "负债收入比", "信用历史", "历史逾期次数",
                     "在职时长", "储蓄覆盖月数", "申请贷款收入比", "借贷平台数"):
            assert check_statement(f"{term}异常的申请人需要人工复核") is None
