"""Scribe write-adapter and unified-entry tests (fake encoder + temp SQLite).

Covers: value_text comes from the LLM statement (never the canonical
template), key/value vectors encode the statement, provenance carries the
scribe tag + source case ids, mixed-evidence drafts stay outcome-neutral, the
quality gate drops junk with counts, and write_experiences() dispatches both
modes.
"""

from __future__ import annotations

import json

import numpy as np
import pytest

from embed import Embedder, SemanticMemory
from embed.canonicalize import experience_text
from embed.fake import hash_encode
from scribe import (
    ExperienceDraft,
    Scribe,
    ScribeCase,
    ScribeWriter,
    write_experiences,
)
from slots import SlotConfig, SlotService

STATEMENT = "负债收入比偏高的申请人违约风险显著上升"


def _draft(statement: str = STATEMENT, outcome: str | None = "bad",
           case_ids: tuple[str, ...] = ("c1", "c2"), mixed: bool = False,
           regime_tag: str = "r0") -> ExperienceDraft:
    return ExperienceDraft(
        statement=statement, conditions="", evidence_count=len(case_ids),
        case_ids=case_ids, outcome=outcome, mixed=mixed, regime_tag=regime_tag,
    )


def _row(dti: float = 0.8) -> dict[str, float]:
    return {
        "income_volatility_obs": 0.3,
        "debt_to_income_obs": dti,
        "credit_history_years_reported": 6.0,
        "delinquencies_reported": 0.0,
        "months_employed": 40.0,
        "savings_months_obs": 4.0,
        "requested_loan_to_income": 0.5,
        "platform_loans_disclosed": 1.0,
    }


@pytest.fixture
def memory(tmp_path):
    svc = SlotService(tmp_path / "slots.db", SlotConfig(theta=0.1))
    mem = SemanticMemory(svc, Embedder(encode_fn=hash_encode))
    yield mem
    svc.close()


def _only_slot(memory: SemanticMemory):
    slots = memory.service.store.all_slots()
    assert len(slots) == 1
    return slots[0]


class TestScribeWriter:
    def test_value_text_is_llm_statement_not_template(self, memory) -> None:
        ScribeWriter(memory).write([_draft()])
        slot = _only_slot(memory)
        assert slot.value_text == STATEMENT
        assert "收入波动" not in slot.value_text  # no template rendering

    def test_vectors_encode_the_statement(self, memory) -> None:
        ScribeWriter(memory).write([_draft()])
        slot = _only_slot(memory)
        expected_key = memory.embedder.embed_keys([STATEMENT])[0]
        expected_value = memory.embedder.embed_values([STATEMENT])[0]
        assert np.allclose(slot.key_vec, expected_key, atol=1e-5)
        assert np.allclose(slot.value_vec, expected_value, atol=1e-5)

    def test_provenance_carries_scribe_tag_and_case_ids(self, memory) -> None:
        ScribeWriter(memory).write([_draft(case_ids=("c1", "c2", "c3"))])
        slot = _only_slot(memory)
        assert slot.provenance == ["scribe:c1,c2,c3"]
        assert slot.regime_tag == "r0"

    def test_provenance_token_caps_long_id_lists(self, memory) -> None:
        ids = tuple(f"c{i}" for i in range(11))
        ScribeWriter(memory).write([_draft(case_ids=ids)])
        token = _only_slot(memory).provenance[0]
        assert token.startswith("scribe:c0,")
        assert token.endswith("(+3)")
        assert "c7" in token and "c8" not in token

    def test_gate_drops_junk_drafts_with_counts(self, memory) -> None:
        drafts = [
            _draft(),                                # passes
            _draft(statement="太短了"),               # too_short
            _draft(statement="申请人一般都还可以接受"),  # no_business_term
            _draft(statement="   "),                 # empty
        ]
        report = ScribeWriter(memory).write(drafts)
        assert report.drafts == 4
        assert report.written == 1
        assert report.dropped == {
            "too_short": 1, "no_business_term": 1, "empty": 1,
        }

    def test_write_ops_counted(self, memory) -> None:
        other = "借贷平台数多头的申请人违约概率明显更高"
        report = ScribeWriter(memory).write([_draft(), _draft(statement=other)])
        assert report.write_ops == {"allocate": 2, "reinforce": 0, "compete": 0}


class TestMixedOutcomeHandling:
    def test_bad_draft_competes_against_good_leaning_slot(self, memory) -> None:
        """Baseline: a directional bad draft on an existing good-leaning slot
        (fresh slots lean good under the P1.1 prior) triggers compete."""
        writer = ScribeWriter(memory)
        op1, _ = _write_one(writer, _draft(outcome="good"))
        op2, _ = _write_one(writer, _draft(outcome="bad"))
        assert (op1, op2) == ("allocate", "compete")

    def test_mixed_draft_never_competes(self, memory) -> None:
        """Mixed evidence (same profile, both outcomes) writes outcome=None:
        the slot enters neutral, coexists via reinforce, and later Beta
        credit_assignment arbitrates the lean."""
        writer = ScribeWriter(memory)
        _write_one(writer, _draft(outcome="good"))
        op, slot = _write_one(writer, _draft(outcome=None, mixed=True,
                                             case_ids=("c9",)))
        assert op == "reinforce"
        assert slot.provenance[-1] == "scribe:c9"


def _write_one(writer: ScribeWriter, draft: ExperienceDraft):
    report = writer.write([draft])
    op = next(k for k, v in report.write_ops.items() if v)
    slot = writer.memory.service.store.all_slots()[-1]
    if op == "reinforce":  # reinforce mutates the FIRST slot, not the last
        slot = writer.memory.service.store.all_slots()[0]
    return op, slot


class FakeGen:
    def __init__(self, *responses: str):
        self.responses = list(responses)
        self.prompts: list[str] = []

    def __call__(self, prompt: str) -> str:
        self.prompts.append(prompt)
        if len(self.responses) > 1:
            return self.responses.pop(0)
        return self.responses[0]


def _payload(statement: str = STATEMENT) -> str:
    return json.dumps({"experiences": [{
        "statement": statement, "conditions": "", "evidence_count": 1,
    }]}, ensure_ascii=False)


class TestUnifiedEntry:
    def test_canonical_mode_matches_p1_behavior(self, memory) -> None:
        cases = [ScribeCase("c1", _row(), "bad", regime_tag="r0")]
        report = write_experiences(cases, memory, mode="canonical")
        slot = _only_slot(memory)
        assert report.mode == "canonical"
        assert report.written == 1
        assert slot.value_text == experience_text(_row(), "bad")
        assert slot.provenance == ["c1"]  # no scribe tag in canonical mode

    def test_scribe_mode_runs_llm_pipeline(self, memory) -> None:
        scribe = Scribe(FakeGen(_payload()))
        cases = [ScribeCase("c1", _row(), "bad"), ScribeCase("c2", _row(), "bad")]
        report = write_experiences(cases, memory, mode="scribe", scribe=scribe)
        slot = _only_slot(memory)
        assert report.mode == "scribe"
        assert report.drafts == 1 and report.written == 1
        assert slot.value_text == STATEMENT
        assert slot.provenance == ["scribe:c1,c2"]

    def test_scribe_mode_total_parse_failure_degrades(self, memory) -> None:
        """LLM garbage twice -> zero slots written, failure recorded, no raise."""
        scribe = Scribe(FakeGen("废话", "还是废话"))
        cases = [ScribeCase("c1", _row(), "bad")]
        report = write_experiences(cases, memory, mode="scribe", scribe=scribe)
        assert report.written == 0
        assert report.parse_failed_groups == 1
        assert memory.service.store.all_slots() == []

    def test_scribe_mode_requires_scribe(self, memory) -> None:
        with pytest.raises(ValueError, match="scribe"):
            write_experiences([], memory, mode="scribe")

    def test_unknown_mode_rejected(self, memory) -> None:
        with pytest.raises(ValueError, match="mode"):
            write_experiences([], memory, mode="bogus")
