"""G1 prompt-template tests (design doc §1.3): render, inject, sanitize."""

from datetime import date

import pytest

from cloud.contracts import ContractError, validate_package
from cloud.providers import MockProvider
from cloud.sanitize import scan_pii
from prompts import make_prompt

REF_DATE = date(2026, 7, 26)

FEATURE_PAYLOAD = {
    "task_id": "g1-fp-1",
    "context": {
        "case_profiles": [
            {
                "case_id": "c1",
                "phone": "13812345678",
                "requested": "50000元",
                "applied_at": "2026-07-20",
                "note": "多头借贷偏高",
            }
        ],
        "existing_features": ["debt_ratio"],
        "dead_ends": [],
    },
    "constraints": {"max_features": 3, "must_be_executable": "pandas", "no_future_info": True},
}

ANALYSIS_PAYLOAD = {
    "task_id": "g1-ca-1",
    "context": {
        "case_profile": {"debt_ratio": 0.7},
        "similar_cases": [],
        "questions": ["why risky?"],
    },
    "constraints": {"max_findings": 5, "cite_features": True},
}

EXPLAIN_PAYLOAD = {
    "task_id": "g1-ex-1",
    "context": {
        "decision": "reject",
        "feature_contributions": [{"feature": "debt_ratio", "weight": -0.3}],
        "audience": "auditor",
    },
    "constraints": {"max_length": 500, "no_fabricated_fields": True},
}


def _retriever(payload: dict) -> list[str]:
    return ["slot#7: 多头借贷高 + 现金流紧张 -> bad (reputation 0.82)"]


def _dead_ends(payload: dict) -> list[str]:
    return ["income_mean_7d (死因: 区分度不足)", "app_open_cnt_1d (死因: 覆盖率萎缩)"]


class TestRenderContents:
    def test_experience_summaries_injected(self) -> None:
        g1 = make_prompt(
            "feature_proposal", FEATURE_PAYLOAD, retriever=_retriever, reference_date=REF_DATE
        )
        assert "slot#7: 多头借贷高" in g1.text
        assert "reputation 0.82" in g1.text

    def test_dead_end_archive_is_explicit_constraint(self) -> None:
        g1 = make_prompt(
            "feature_proposal", FEATURE_PAYLOAD, dead_end_lookup=_dead_ends,
            reference_date=REF_DATE,
        )
        assert "DO NOT REPEAT" in g1.text
        assert "income_mean_7d (死因: 区分度不足)" in g1.text
        assert "app_open_cnt_1d" in g1.text

    def test_output_schema_section_present(self) -> None:
        g1 = make_prompt("feature_proposal", FEATURE_PAYLOAD, reference_date=REF_DATE)
        assert "OUTPUT CONTRACT" in g1.text
        assert "features" in g1.text  # schema body rendered into the prompt

    def test_no_retriever_marks_empty_sections(self) -> None:
        g1 = make_prompt("case_analysis", ANALYSIS_PAYLOAD, reference_date=REF_DATE)
        assert "no relevant local experience" in g1.text
        assert "dead-end archive is empty" in g1.text

    @pytest.mark.parametrize(
        "task_type, payload, heading",
        [
            ("feature_proposal", FEATURE_PAYLOAD, "FEATURE PROPOSAL"),
            ("case_analysis", ANALYSIS_PAYLOAD, "CASE ANALYSIS"),
            ("explanation", EXPLAIN_PAYLOAD, "EXPLANATION"),
        ],
    )
    def test_three_task_types_render(self, task_type: str, payload: dict, heading: str) -> None:
        g1 = make_prompt(task_type, payload, reference_date=REF_DATE)
        assert heading in g1.text
        validate_package(g1.package)


class TestSanitizeChain:
    def test_pii_redacted_in_rendered_prompt(self) -> None:
        g1 = make_prompt("feature_proposal", FEATURE_PAYLOAD, reference_date=REF_DATE)
        assert "13812345678" not in g1.text
        assert "[PHONE]" in g1.text
        assert scan_pii(g1.text) == []

    def test_amounts_and_dates_generalized(self) -> None:
        g1 = make_prompt("feature_proposal", FEATURE_PAYLOAD, reference_date=REF_DATE)
        assert "50000元" not in g1.text
        assert "<AMT:5W-10W>" in g1.text
        assert "2026-07-20" not in g1.text
        assert "D-6" in g1.text

    def test_executor_welded_chain_accepts_package(self) -> None:
        # The G1 package goes through SanitizedCloudExecutor's hard-wired
        # render -> sanitize -> outbound-gate path unchanged (§7.3).
        g1 = make_prompt(
            "feature_proposal", FEATURE_PAYLOAD, retriever=_retriever,
            dead_end_lookup=_dead_ends, reference_date=REF_DATE,
        )
        result = MockProvider().execute(g1.package)
        assert result.task_type == "feature_proposal"
        assert result.content["features"]
        # Briefing is embedded so the executor's rendered prompt carries the
        # G1 structure (experience, dead ends, schema) through the gate.
        assert g1.package.context["g1_briefing"] == g1.text


class TestContractValidation:
    def test_unknown_task_type_raises(self) -> None:
        with pytest.raises(ContractError):
            make_prompt("rule_writing", FEATURE_PAYLOAD, reference_date=REF_DATE)

    def test_missing_context_field_raises(self) -> None:
        bad = {
            "task_id": "g1-bad",
            "context": {"existing_features": [], "dead_ends": []},  # no case_profiles
            "constraints": {"max_features": 1, "must_be_executable": "pandas",
                            "no_future_info": True},
        }
        with pytest.raises(ContractError):
            make_prompt("feature_proposal", bad, reference_date=REF_DATE)

    def test_task_id_generated_when_absent(self) -> None:
        payload = {k: v for k, v in ANALYSIS_PAYLOAD.items() if k != "task_id"}
        g1 = make_prompt("case_analysis", payload, reference_date=REF_DATE)
        assert g1.package.task_id.startswith("g1-case_analysis-")

    def test_dead_ends_default_from_context(self) -> None:
        payload = {
            **FEATURE_PAYLOAD,
            "context": {**FEATURE_PAYLOAD["context"], "dead_ends": ["old_idea (死因: regime 不稳)"]},
        }
        g1 = make_prompt("feature_proposal", payload, reference_date=REF_DATE)
        assert "old_idea (死因: regime 不稳)" in g1.text
