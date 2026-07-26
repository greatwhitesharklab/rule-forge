"""Explanation-text verifier tests (design doc §3.1 row 3): rule checks."""

from verify.explanation import verify_explanation

ALLOWED = ["debt_ratio", "income_volatility_obs", "platform_loans_disclosed"]


class TestExplanationVerifier:
    def test_clean_explanation_passes(self) -> None:
        v = verify_explanation(
            "Rejected because debt_ratio is high and income_volatility_obs is elevated.",
            cited_features=["debt_ratio", "income_volatility_obs"],
            allowed_features=ALLOWED,
            max_length=500,
        )
        assert v.status == "pass"
        assert v.quality == 1.0
        assert v.reasons == ()

    def test_fabricated_feature_caught(self) -> None:
        v = verify_explanation(
            "Rejected because credit_score_v2 is poor.",
            cited_features=["debt_ratio", "credit_score_v2"],
            allowed_features=ALLOWED,
            max_length=500,
        )
        assert v.status == "fail"
        assert v.quality == 0.5
        assert any("credit_score_v2" in r and "fabricated" in r for r in v.reasons)

    def test_banned_word_caught_case_insensitive(self) -> None:
        v = verify_explanation(
            "You are GUARANTEED approval regardless of debt_ratio.",
            cited_features=["debt_ratio"],
            allowed_features=ALLOWED,
            banned_words=["guaranteed approval"],
            max_length=500,
        )
        assert v.status == "fail"
        assert any("banned" in r for r in v.reasons)

    def test_default_compliance_wordlist(self) -> None:
        v = verify_explanation(
            "放心,本产品保证下款。",
            cited_features=[],
            allowed_features=ALLOWED,
            max_length=500,
        )
        assert v.status == "fail"
        assert any("保证下款" in r for r in v.reasons)

    def test_length_limit(self) -> None:
        v = verify_explanation(
            "x" * 101,
            cited_features=[],
            allowed_features=ALLOWED,
            max_length=100,
        )
        assert v.status == "fail"
        assert any("length" in r for r in v.reasons)
        assert v.metrics["length"] == 101

    def test_multiple_violations_drive_quality_to_zero(self) -> None:
        v = verify_explanation(
            "保证下款, credit_score_v2 says so. " + "x" * 600,
            cited_features=["credit_score_v2"],
            allowed_features=ALLOWED,
            max_length=100,
        )
        assert v.status == "fail"
        assert v.quality == 0.0
        assert v.metrics["violations"] >= 3

    def test_empty_citations_allowed(self) -> None:
        v = verify_explanation(
            "Approved on overall profile strength.",
            cited_features=[],
            allowed_features=ALLOWED,
            max_length=500,
        )
        assert v.status == "pass"
