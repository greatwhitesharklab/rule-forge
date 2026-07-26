"""Scribe induction tests: grouping, prompt content, JSON fault tolerance.

All tests inject a fake generate_fn — no LLM weights touched. The fake covers
the four real-world output shapes: clean JSON, ```json fenced, JSON wrapped in
chatter, and unparseable text (retry-with-feedback, then degrade-to-empty).
"""

from __future__ import annotations

import json

import pytest

from scribe import ExperienceDraft, Scribe, ScribeCase

STATEMENT = "负债收入比偏高的申请人违约风险显著上升"


def _payload(items: list[dict] | None = None, **over) -> str:
    exp = {"statement": STATEMENT, "conditions": "负债收入比:偏高", "evidence_count": 2}
    exp.update(over)
    return json.dumps({"experiences": items if items is not None else [exp]},
                      ensure_ascii=False)


class FakeGen:
    """Queued-response generate_fn recording every prompt it sees."""

    def __init__(self, *responses: str):
        self.responses = list(responses)
        self.prompts: list[str] = []

    def __call__(self, prompt: str) -> str:
        self.prompts.append(prompt)
        if len(self.responses) > 1:
            return self.responses.pop(0)
        return self.responses[0]


def _row(dti: float = 0.8, employed: float = 40.0) -> dict[str, float]:
    return {
        "income_volatility_obs": 0.3,
        "debt_to_income_obs": dti,
        "credit_history_years_reported": 6.0,
        "delinquencies_reported": 0.0,
        "months_employed": employed,
        "savings_months_obs": 4.0,
        "requested_loan_to_income": 0.5,
        "platform_loans_disclosed": 1.0,
    }


def _case(cid: str, outcome: str | None = "bad", **rowkw) -> ScribeCase:
    return ScribeCase(case_id=cid, row=_row(**rowkw), outcome=outcome,
                      regime_tag="r0")


class TestGrouping:
    def test_same_profile_prefix_groups_together(self) -> None:
        """Rows identical in the first 4 canonical fields form ONE group even
        when the trailing fields differ."""
        gen = FakeGen(_payload())
        scribe = Scribe(gen)
        drafts = scribe.induce([
            _case("c1", employed=40.0),
            _case("c2", employed=200.0),  # same prefix, different tail
        ])
        assert len(gen.prompts) == 1
        assert len(drafts) == 1
        assert drafts[0].case_ids == ("c1", "c2")

    def test_different_prefix_splits_groups(self) -> None:
        gen = FakeGen(_payload())
        scribe = Scribe(gen)
        drafts = scribe.induce([_case("c1", dti=0.8), _case("c2", dti=1.5)])
        assert len(gen.prompts) == 2
        assert {d.case_ids for d in drafts} == {("c1",), ("c2",)}

    def test_empty_input_calls_no_llm(self) -> None:
        gen = FakeGen(_payload())
        assert Scribe(gen).induce([]) == []
        assert gen.prompts == []


class TestOutcomeDerivation:
    def test_uniform_good(self) -> None:
        drafts = Scribe(FakeGen(_payload())).induce(
            [_case("c1", "good"), _case("c2", "good")])
        assert drafts[0].outcome == "good"
        assert drafts[0].mixed is False

    def test_uniform_bad(self) -> None:
        drafts = Scribe(FakeGen(_payload())).induce(
            [_case("c1", "bad"), _case("c2", "bad")])
        assert drafts[0].outcome == "bad"
        assert drafts[0].mixed is False

    def test_mixed_outcomes_yield_neutral_draft(self) -> None:
        drafts = Scribe(FakeGen(_payload())).induce(
            [_case("c1", "good"), _case("c2", "bad")])
        assert drafts[0].outcome is None
        assert drafts[0].mixed is True

    def test_unknown_outcomes(self) -> None:
        drafts = Scribe(FakeGen(_payload())).induce(
            [_case("c1", None), _case("c2", None)])
        assert drafts[0].outcome is None
        assert drafts[0].mixed is False


class TestParseShapes:
    def test_clean_json(self) -> None:
        drafts = Scribe(FakeGen(_payload())).induce([_case("c1"), _case("c2")])
        assert [d.statement for d in drafts] == [STATEMENT]
        assert drafts[0].conditions == "负债收入比:偏高"
        assert drafts[0].evidence_count == 2

    def test_fenced_json(self) -> None:
        gen = FakeGen(f"```json\n{_payload()}\n```")
        drafts = Scribe(gen).induce([_case("c1")])
        assert [d.statement for d in drafts] == [STATEMENT]

    def test_chatter_around_json(self) -> None:
        gen = FakeGen(f"好的,归纳结果如下:\n{_payload()}\n希望对您有帮助。")
        drafts = Scribe(gen).induce([_case("c1")])
        assert [d.statement for d in drafts] == [STATEMENT]

    def test_invalid_then_retry_with_feedback(self) -> None:
        gen = FakeGen("我无法归纳这组案例", _payload())
        drafts = Scribe(gen).induce([_case("c1")])
        assert len(gen.prompts) == 2
        assert "无法解析" in gen.prompts[1]  # error feedback attached
        assert [d.statement for d in drafts] == [STATEMENT]
        assert scribe_report_ok(drafts)

    def test_persistent_invalid_degrades_to_empty(self) -> None:
        """Total parse failure must degrade silently: no exception escapes."""
        gen = FakeGen("废话一", "废话二")
        scribe = Scribe(gen)
        drafts = scribe.induce([_case("c1")])
        assert drafts == []
        assert len(gen.prompts) == 2  # one retry, not more
        assert scribe.last_report.failed_groups == 1

    def test_generator_exception_degrades_to_empty(self) -> None:
        def boom(prompt: str) -> str:
            raise RuntimeError("model exploded")

        scribe = Scribe(boom)
        assert scribe.induce([_case("c1")]) == []
        assert scribe.last_report.llm_errors == 1


def scribe_report_ok(drafts: list[ExperienceDraft]) -> bool:
    return bool(drafts)


class TestSchemaValidation:
    def test_evidence_count_clamped_to_group_size(self) -> None:
        drafts = Scribe(FakeGen(_payload(evidence_count=99))).induce(
            [_case("c1"), _case("c2")])
        assert drafts[0].evidence_count == 2

    def test_missing_optional_fields_default(self) -> None:
        raw = json.dumps({"experiences": [{"statement": STATEMENT}]},
                         ensure_ascii=False)
        drafts = Scribe(FakeGen(raw)).induce([_case("c1"), _case("c2")])
        assert drafts[0].conditions == ""
        assert drafts[0].evidence_count == 2  # defaults to group size

    def test_items_without_statement_skipped(self) -> None:
        raw = json.dumps({"experiences": [{"conditions": "x"}, {"statement": STATEMENT}]},
                         ensure_ascii=False)
        drafts = Scribe(FakeGen(raw)).induce([_case("c1")])
        assert [d.statement for d in drafts] == [STATEMENT]

    def test_experiences_capped_per_group(self) -> None:
        items = [{"statement": f"{STATEMENT}{i}"} for i in range(5)]
        drafts = Scribe(FakeGen(_payload(items))).induce([_case("c1")])
        assert len(drafts) == 3

    def test_wrong_top_level_type_retries_then_degrades(self) -> None:
        gen = FakeGen("[1,2,3]", "42")
        scribe = Scribe(gen)
        assert scribe.induce([_case("c1")]) == []
        assert scribe.last_report.failed_groups == 1


class TestPromptContent:
    def test_prompt_carries_canonical_summaries_and_contract(self) -> None:
        gen = FakeGen(_payload())
        Scribe(gen).induce([_case("c1", "bad"), _case("c2", "bad")])
        prompt = gen.prompts[0]
        assert "负债收入比:偏高" in prompt  # de-identified canonical summary
        assert "结局:违约" in prompt
        assert "收入波动" in prompt and "借贷平台数" in prompt  # business terms
        assert '"experiences"' in prompt  # output contract
        assert "审计" in prompt
