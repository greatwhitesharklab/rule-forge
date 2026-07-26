"""Tests for cloud.providers (design doc §2.1) and the sanitize hard-wiring."""

from __future__ import annotations

import json
from types import SimpleNamespace

import pytest

from cloud.contracts import TaskPackage, validate_result
from cloud.ledger import CostLedger
from cloud.providers import (
    CloudLLM,
    MockProvider,
    OpenAIProvider,
    ProviderConfigError,
)


def make_pkg(task_type: str, context: dict | None = None) -> TaskPackage:
    base = {
        "feature_proposal": (
            {"case_profiles": [], "existing_features": [], "dead_ends": []},
            {"max_features": 5, "must_be_executable": "python", "no_future_info": True},
        ),
        "case_analysis": (
            {"case_profile": {}, "similar_cases": [], "questions": ["q?"]},
            {"max_findings": 3, "cite_features": True},
        ),
        "explanation": (
            {"decision": "reject", "feature_contributions": [], "audience": "auditor"},
            {"max_length": 500, "no_fabricated_fields": True},
        ),
    }
    ctx, constraints = base[task_type]
    if context:
        ctx.update(context)
    return TaskPackage(
        task_id=f"t-{task_type}",
        task_type=task_type,
        context=ctx,
        constraints=constraints,
        output_schema={"result": "json"},
    )


def fake_client(payload: dict, fail: Exception | None = None) -> tuple[SimpleNamespace, list[dict]]:
    """Build a stand-in openai client; returns (client, captured create() kwargs)."""
    calls: list[dict] = []

    class _Completions:
        def create(self, **kwargs):
            calls.append(kwargs)
            if fail is not None:
                raise fail
            return SimpleNamespace(
                model="fake-model-2026-01",
                choices=[SimpleNamespace(message=SimpleNamespace(content=json.dumps(payload)))],
                usage=SimpleNamespace(prompt_tokens=11, completion_tokens=7),
            )

    return SimpleNamespace(chat=SimpleNamespace(completions=_Completions())), calls


class TestMockProvider:
    def test_conforms_to_protocol(self) -> None:
        assert isinstance(MockProvider(), CloudLLM)

    @pytest.mark.parametrize("task_type", ["feature_proposal", "case_analysis", "explanation"])
    def test_end_to_end_execute(self, task_type: str) -> None:
        provider = MockProvider()
        result = provider.execute(make_pkg(task_type))
        assert result.task_type == task_type
        validate_result(task_type, result.content)  # mock output honors the contract
        prov = result.provenance
        assert prov.provider == "mock"
        assert prov.model
        assert prov.model_version
        assert prov.timestamp
        assert len(prov.prompt_hash) == 64
        assert prov.cost_tokens > 0

    def test_deterministic_prompt_hash(self) -> None:
        provider = MockProvider()
        r1 = provider.execute(make_pkg("feature_proposal"))
        r2 = provider.execute(make_pkg("feature_proposal"))
        assert r1.provenance.prompt_hash == r2.provenance.prompt_hash


class TestOpenAIProviderConfig:
    def test_missing_api_key_raises_explicit_error(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
        with pytest.raises(ProviderConfigError, match="DEEPSEEK_API_KEY"):
            OpenAIProvider("deepseek")

    def test_unknown_provider_rejected(self) -> None:
        with pytest.raises(ProviderConfigError, match="unknown provider"):
            OpenAIProvider("no-such-vendor")

    def test_fallback_slot_unconfigured_raises(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.delenv("CLOUD_FALLBACK_API_KEY", raising=False)
        monkeypatch.delenv("CLOUD_FALLBACK_BASE_URL", raising=False)
        with pytest.raises(ProviderConfigError):
            OpenAIProvider("fallback")


class TestSanitizeHardWiring:
    """execute() must pass every outbound payload through sanitize — no bypass."""

    PII_CONTEXT = {
        "case_profiles": [
            {
                "note": "姓名：张伟，手机13812345678，身份证110101199003070077，"
                "卡号6222021234567890123，住址北京市朝阳区建国路88号，金额12万元"
            }
        ]
    }

    def test_outbound_request_body_has_no_pii(self) -> None:
        payload = {"features": [{"name": "f", "expression": "a/b", "rationale": "r"}]}
        client, calls = fake_client(payload)
        provider = OpenAIProvider("deepseek", client=client)
        provider.execute(make_pkg("feature_proposal", self.PII_CONTEXT))

        body = calls[0]["messages"][1]["content"]
        for raw in ("张伟", "13812345678", "110101199003070077", "6222021234567890123", "建国路88号", "12万"):
            assert raw not in body
        for token in ("[NAME]", "[PHONE]", "[ID_CARD]", "[BANK_CARD]", "[ADDRESS]", "<AMT:10W-50W>"):
            assert token in body

    def test_result_provenance_recorded(self) -> None:
        payload = {"features": [{"name": "f", "expression": "a/b", "rationale": "r"}]}
        client, _ = fake_client(payload)
        provider = OpenAIProvider("deepseek", client=client)
        result = provider.execute(make_pkg("feature_proposal"))
        prov = result.provenance
        assert prov.provider == "deepseek"
        assert prov.model == "deepseek-reasoner"
        assert prov.model_version == "fake-model-2026-01"
        assert prov.cost_tokens == 18  # 11 prompt + 7 completion

    def test_retry_exhausts_then_raises(self) -> None:
        client, calls = fake_client({}, fail=RuntimeError("boom"))
        provider = OpenAIProvider("deepseek", client=client, max_retries=3)
        with pytest.raises(RuntimeError, match="boom"):
            provider.execute(make_pkg("feature_proposal"))
        assert len(calls) == 3

    def test_retry_can_be_disabled(self) -> None:
        client, calls = fake_client({}, fail=RuntimeError("boom"))
        provider = OpenAIProvider("deepseek", client=client, enable_retry=False)
        with pytest.raises(RuntimeError, match="boom"):
            provider.execute(make_pkg("feature_proposal"))
        assert len(calls) == 1

    def test_invalid_result_json_raises(self) -> None:
        client, _ = fake_client({"unexpected": "shape"})
        provider = OpenAIProvider("deepseek", client=client)
        from cloud.providers import TaskResultError

        with pytest.raises(TaskResultError):
            provider.execute(make_pkg("feature_proposal"))


class TestLedgerIntegration:
    def test_success_and_error_calls_recorded(self) -> None:
        ledger = CostLedger()
        ok_client, _ = fake_client(
            {"explanation": "e", "cited_features": [], "compliance_flags": []}
        )
        OpenAIProvider("deepseek", client=ok_client, ledger=ledger).execute(make_pkg("explanation"))

        bad_client, _ = fake_client({}, fail=RuntimeError("boom"))
        with pytest.raises(RuntimeError):
            OpenAIProvider("deepseek", client=bad_client, ledger=ledger, enable_retry=False).execute(
                make_pkg("explanation")
            )

        rows = ledger.aggregate()
        assert rows == [
            {"provider": "deepseek", "task_type": "explanation", "calls": 2, "cost_tokens": 18, "failures": 1}
        ]
