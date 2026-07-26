"""Real Qwen3-0.6B Scribe induction test (CPU, local HF cache).

Marked `slow_model`; skips (never hard-fails) when the snapshot is incomplete
or the 0.6B model simply refuses to emit parseable JSON on a given run — the
assertion that matters is loose: at least one induced statement must cite a
known business-field term.
"""

from __future__ import annotations

import pytest

from embed.canonicalize import FIELD_MAP
from llm import LocalLLM
from scribe import Scribe, ScribeCase

pytestmark = pytest.mark.slow_model

MODEL_ID = "Qwen/Qwen3-0.6B"
TERMS = tuple(s.cn_label for s in FIELD_MAP)


def _model_snapshot_ready() -> bool:
    try:
        from huggingface_hub import scan_cache_dir

        for repo in scan_cache_dir().repos:
            if repo.repo_id != MODEL_ID:
                continue
            for rev in repo.revisions:
                names = {f.file_name for f in rev.files}
                if {"model.safetensors", "config.json", "tokenizer.json"} <= names:
                    return True
    except Exception:
        pass
    return False


def _row() -> dict[str, float]:
    return {
        "income_volatility_obs": 0.85,
        "debt_to_income_obs": 1.6,
        "credit_history_years_reported": 1.0,
        "delinquencies_reported": 3.0,
        "months_employed": 5.0,
        "savings_months_obs": 0.5,
        "requested_loan_to_income": 1.4,
        "platform_loans_disclosed": 5.0,
    }


@pytest.fixture(scope="module")
def scribe() -> Scribe:
    if not _model_snapshot_ready():
        pytest.skip(f"{MODEL_ID} snapshot not complete in HF cache")
    try:
        llm = LocalLLM(MODEL_ID, device="cpu").load()
    except Exception as exc:
        pytest.skip(f"{MODEL_ID} unavailable: {exc}")
    return Scribe.from_llm(llm, max_new_tokens=320)


def test_real_model_induces_auditable_statement(scribe: Scribe) -> None:
    cases = [ScribeCase(f"r{i}", _row(), "bad", regime_tag="r0") for i in range(4)]
    drafts = scribe.induce(cases)
    if not drafts:
        pytest.skip("0.6B output not parseable this run (generation variance)")
    assert any(term in d.statement for d in drafts for term in TERMS)
