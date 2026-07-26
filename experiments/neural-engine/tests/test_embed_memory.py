"""SemanticMemory facade tests (fake-encoder path, temp SQLite).

End-to-end: case row -> canonical text -> embedding -> SlotService public API.
Identical case rows produce identical key vectors (cosine 1.0), so a repeated
case reinforces the same slot; retrieval then surfaces it above THETA.
"""

from __future__ import annotations

import pytest

from embed import Embedder, SemanticMemory
from embed.canonicalize import canonicalize
from embed.fake import hash_encode
from slots import SlotConfig, SlotService


def _row(debt: float = 0.8) -> dict[str, float]:
    return {
        "income_volatility_obs": 0.3,
        "debt_to_income_obs": debt,
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


class TestWritePath:
    def test_first_write_allocates(self, memory: SemanticMemory) -> None:
        op, slot = memory.write_case_experience(_row(), "bad", case_id="case-1")
        assert op == "allocate"
        assert slot.status == "candidate"
        assert slot.provenance == ["case-1"]

    def test_identical_case_reinforces(self, memory: SemanticMemory) -> None:
        _, slot1 = memory.write_case_experience(_row(), "good", case_id="case-1")
        op, slot2 = memory.write_case_experience(_row(), "good", case_id="case-2")
        assert op == "reinforce"
        assert slot2.slot_id == slot1.slot_id
        assert slot2.provenance == ["case-1", "case-2"]

    def test_identical_bad_case_competes_under_prior(self, memory: SemanticMemory) -> None:
        """P1.1: a fresh slot leans good (bad-rate prior), so a repeated BAD
        case contradicts the lean and spawns a competing slot."""
        _, slot1 = memory.write_case_experience(_row(), "bad", case_id="case-1")
        op, slot2 = memory.write_case_experience(_row(), "bad", case_id="case-2")
        assert op == "compete"
        assert slot2.slot_id != slot1.slot_id

    def test_value_text_is_canonical_plus_outcome(self, memory: SemanticMemory) -> None:
        _, slot = memory.write_case_experience(_row(), "bad", case_id="case-1")
        assert canonicalize(_row()) in slot.value_text
        assert slot.value_text.endswith("结局:违约")

    def test_regime_tag_forwarded(self, memory: SemanticMemory) -> None:
        _, slot = memory.write_case_experience(
            _row(), "good", case_id="case-1", regime_tag="R03"
        )
        assert slot.regime_tag == "R03"


class TestRetrievePath:
    def test_retrieve_finds_written_case(self, memory: SemanticMemory) -> None:
        _, slot = memory.write_case_experience(_row(), "bad", case_id="case-1")
        hits = memory.retrieve_for_case(_row(), case_id="case-q")
        assert [s.slot_id for s, _ in hits] == [slot.slot_id]

    def test_retrieve_logs_attribution(self, memory: SemanticMemory) -> None:
        _, slot = memory.write_case_experience(_row(), "bad", case_id="case-1")
        memory.retrieve_for_case(_row(), case_id="case-q")
        rows = memory.service.store.attributions_for("case-q")
        assert len(rows) == 1
        assert rows[0]["slot_id"] == slot.slot_id
        assert rows[0]["a_sem"] == pytest.approx(1.0, rel=1e-5)

    def test_unrelated_case_misses(self, memory: SemanticMemory) -> None:
        memory.write_case_experience(_row(), "bad", case_id="case-1")
        hits = memory.retrieve_for_case(_row(debt=2.5), case_id="case-q")
        assert hits == []  # different canonical text -> ~orthogonal hash key

    def test_credit_assignment_roundtrip(self, memory: SemanticMemory) -> None:
        """Retrieval attribution -> outcome credit folds into Beta reputation."""
        _, slot = memory.write_case_experience(_row(), "bad", case_id="case-1")
        memory.retrieve_for_case(_row(), case_id="case-q")
        memory.service.credit_assignment("case-q", "bad")
        after = memory.service.store.get_slot(slot.slot_id)
        # P1.1 prior beta_b = 4.0 * 0.1 = 0.4, plus the attribution weight
        # (a_sem 1.0 * a_tmp 0.5 = 0.5, reputation-free).
        assert after.beta_b == pytest.approx(0.4 + 0.5)
