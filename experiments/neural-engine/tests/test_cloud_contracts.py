"""Tests for cloud.contracts (design doc §2.3 task package contracts)."""

from __future__ import annotations

import pytest

from cloud.contracts import (
    ContractError,
    TaskPackage,
    render_prompt,
    validate_package,
    validate_result,
)


def feature_pkg() -> TaskPackage:
    return TaskPackage(
        task_id="t-fp-1",
        task_type="feature_proposal",
        context={
            "case_profiles": [{"bad_rate": 0.12, "segment": "sme"}],
            "existing_features": ["debt_ratio"],
            "dead_ends": ["income_x_region"],
        },
        constraints={
            "max_features": 5,
            "must_be_executable": "python",
            "no_future_info": True,
        },
        output_schema={"features": [{"name": "str", "expression": "str", "rationale": "str"}]},
    )


def analysis_pkg() -> TaskPackage:
    return TaskPackage(
        task_id="t-ca-1",
        task_type="case_analysis",
        context={
            "case_profile": {"debt_ratio": 0.7},
            "similar_cases": [{"case_id": "c-1"}],
            "questions": ["why did this case default?"],
        },
        constraints={"max_findings": 3, "cite_features": True},
        output_schema={"findings": [], "conclusion": "str", "rationale": "str"},
    )


def explain_pkg() -> TaskPackage:
    return TaskPackage(
        task_id="t-ex-1",
        task_type="explanation",
        context={
            "decision": "reject",
            "feature_contributions": [{"feature": "debt_ratio", "value": 0.7, "contribution": -0.2}],
            "audience": "auditor",
        },
        constraints={"max_length": 500, "no_fabricated_fields": True},
        output_schema={"explanation": "str", "cited_features": [], "compliance_flags": []},
    )


class TestPackageValidation:
    def test_all_three_valid_packages_pass(self) -> None:
        for pkg in (feature_pkg(), analysis_pkg(), explain_pkg()):
            validate_package(pkg)  # must not raise

    def test_unknown_task_type_rejected(self) -> None:
        pkg = feature_pkg()
        pkg.task_type = "free_chat"
        with pytest.raises(ContractError):
            validate_package(pkg)

    def test_missing_context_field_rejected(self) -> None:
        pkg = feature_pkg()
        del pkg.context["dead_ends"]
        with pytest.raises(ContractError, match="dead_ends"):
            validate_package(pkg)

    def test_missing_constraint_field_rejected(self) -> None:
        pkg = feature_pkg()
        del pkg.constraints["max_features"]
        with pytest.raises(ContractError, match="max_features"):
            validate_package(pkg)

    def test_wrong_field_type_rejected(self) -> None:
        pkg = feature_pkg()
        pkg.constraints["max_features"] = "five"
        with pytest.raises(ContractError):
            validate_package(pkg)

    def test_bool_is_not_int(self) -> None:
        pkg = feature_pkg()
        pkg.constraints["max_features"] = True
        with pytest.raises(ContractError):
            validate_package(pkg)

    def test_nonpositive_max_features_rejected(self) -> None:
        pkg = feature_pkg()
        pkg.constraints["max_features"] = 0
        with pytest.raises(ContractError):
            validate_package(pkg)

    def test_invalid_audience_rejected(self) -> None:
        pkg = explain_pkg()
        pkg.context["audience"] = "hacker"
        with pytest.raises(ContractError):
            validate_package(pkg)

    def test_empty_output_schema_rejected(self) -> None:
        pkg = analysis_pkg()
        pkg.output_schema = {}
        with pytest.raises(ContractError):
            validate_package(pkg)

    def test_render_prompt_is_deterministic_json(self) -> None:
        a, b = render_prompt(feature_pkg()), render_prompt(feature_pkg())
        assert a == b
        assert "feature_proposal" in a


class TestResultValidation:
    def test_valid_feature_result_passes(self) -> None:
        validate_result(
            "feature_proposal",
            {"features": [{"name": "f1", "expression": "a / b", "rationale": "why"}]},
        )

    def test_feature_result_missing_field_rejected(self) -> None:
        with pytest.raises(ContractError, match="expression"):
            validate_result("feature_proposal", {"features": [{"name": "f1", "rationale": "x"}]})

    def test_valid_analysis_result_passes(self) -> None:
        validate_result(
            "case_analysis",
            {
                "findings": [
                    {"claim": "high leverage", "evidence_features": ["debt_ratio"], "confidence": 0.8}
                ],
                "conclusion": "review",
                "rationale": "evidence-based",
            },
        )

    def test_analysis_bad_conclusion_rejected(self) -> None:
        with pytest.raises(ContractError, match="conclusion"):
            validate_result(
                "case_analysis",
                {"findings": [], "conclusion": "maybe", "rationale": "x"},
            )

    def test_analysis_confidence_out_of_range_rejected(self) -> None:
        with pytest.raises(ContractError, match="confidence"):
            validate_result(
                "case_analysis",
                {
                    "findings": [{"claim": "c", "evidence_features": [], "confidence": 1.5}],
                    "conclusion": "reject",
                    "rationale": "x",
                },
            )

    def test_valid_explanation_result_passes(self) -> None:
        validate_result(
            "explanation",
            {"explanation": "because debt_ratio is high", "cited_features": ["debt_ratio"], "compliance_flags": []},
        )

    def test_explanation_missing_field_rejected(self) -> None:
        with pytest.raises(ContractError, match="cited_features"):
            validate_result("explanation", {"explanation": "x", "compliance_flags": []})

    def test_unknown_task_type_result_rejected(self) -> None:
        with pytest.raises(ContractError):
            validate_result("free_chat", {})
