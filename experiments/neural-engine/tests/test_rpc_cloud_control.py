"""Tests for cloud variance control (temperature=0, retry-in-transport,
no-fallback real-cloud mode) and invalid-run exclusion in multiseed stats.
"""

from __future__ import annotations

import json
from types import SimpleNamespace

import pytest

from cloud.contracts import TaskPackage
from cloud.providers import MockProvider, OpenAIProvider, TaskResultError
from eval.orchestrator_eval import CloudUnavailableError, OrchestratorCloud
from eval.rpc_multiseed import summarize_runs
from eval.rpc_paper1 import ExperimentResult


def make_pkg() -> TaskPackage:
    return TaskPackage(
        task_id="t1", task_type="feature_proposal",
        context={"case_profiles": [], "existing_features": [],
                 "dead_ends": []},
        constraints={"max_features": 5, "must_be_executable": "python",
                     "no_future_info": True},
        output_schema={"features": [{"name": "str", "expression": "str",
                                     "rationale": "str"}]},
    )


VALID_PAYLOAD = {"features": [{"name": "f", "expression": "a/b",
                               "rationale": "r"}]}


def scripted_client(contents: list):
    """Fake openai client returning scripted message contents in order."""
    calls: list[dict] = []

    class _Completions:
        def create(self, **kwargs):
            calls.append(kwargs)
            content = contents[min(len(calls) - 1, len(contents) - 1)]
            if isinstance(content, Exception):
                raise content
            return SimpleNamespace(
                model="fake-model",
                choices=[SimpleNamespace(message=SimpleNamespace(
                    content=content))],
                usage=SimpleNamespace(prompt_tokens=11, completion_tokens=7),
            )

    return SimpleNamespace(chat=SimpleNamespace(completions=_Completions())), calls


class TestTemperatureControl:
    def test_default_temperature_passed_to_api(self) -> None:
        # Default is 0.3, NOT 0: greedy decoding makes v4-flash's reasoning
        # loop (measured 125k reasoning chars -> truncation -> empty content).
        client, calls = scripted_client([json.dumps(VALID_PAYLOAD)])
        provider = OpenAIProvider("deepseek", client=client)
        provider.execute(make_pkg())
        assert calls[0]["temperature"] == 0.3

    def test_temperature_override(self) -> None:
        client, calls = scripted_client([json.dumps(VALID_PAYLOAD)])
        provider = OpenAIProvider("deepseek", client=client, temperature=0.7)
        provider.execute(make_pkg())
        assert calls[0]["temperature"] == 0.7


class TestReasoningEffort:
    def test_default_low_passed_to_api(self) -> None:
        client, calls = scripted_client([json.dumps(VALID_PAYLOAD)])
        provider = OpenAIProvider("deepseek", client=client)
        provider.execute(make_pkg())
        assert calls[0]["reasoning_effort"] == "low"

    def test_none_omits_parameter(self) -> None:
        # For models that reject reasoning_effort entirely.
        client, calls = scripted_client([json.dumps(VALID_PAYLOAD)])
        provider = OpenAIProvider("deepseek", client=client,
                                  reasoning_effort=None)
        provider.execute(make_pkg())
        assert "reasoning_effort" not in calls[0]

    def test_override_level(self) -> None:
        client, calls = scripted_client([json.dumps(VALID_PAYLOAD)])
        provider = OpenAIProvider("deepseek", client=client,
                                  reasoning_effort="high")
        provider.execute(make_pkg())
        assert calls[0]["reasoning_effort"] == "high"


class TestRetryOnBadContent:
    def test_non_json_retried_then_success(self) -> None:
        client, calls = scripted_client(
            ["garbage", "still garbage", json.dumps(VALID_PAYLOAD)])
        provider = OpenAIProvider("deepseek", client=client, max_retries=3)
        result = provider.execute(make_pkg())
        assert result.content == VALID_PAYLOAD
        assert len(calls) == 3  # two failures retried, third succeeds

    def test_empty_content_retried(self) -> None:
        client, calls = scripted_client(["", json.dumps(VALID_PAYLOAD)])
        provider = OpenAIProvider("deepseek", client=client, max_retries=3)
        provider.execute(make_pkg())
        assert len(calls) == 2

    def test_retry_exhausted_raises(self) -> None:
        client, calls = scripted_client(["garbage"] * 5)
        provider = OpenAIProvider("deepseek", client=client, max_retries=3)
        with pytest.raises(TaskResultError):
            provider.execute(make_pkg())
        assert len(calls) == 3  # stopped after max_retries attempts


class FailingCloud:
    """cloud_llm stand-in whose execute always fails (retries exhausted)."""

    def execute(self, task):
        raise TaskResultError("cloud returned empty content")


class TestNoFallbackMode:
    def _task(self) -> TaskPackage:
        return make_pkg()

    def test_allow_fallback_false_raises(self) -> None:
        cloud = OrchestratorCloud(llm=None, fields_str="age",
                                  cloud_llm=FailingCloud(),
                                  allow_fallback=False)
        with pytest.raises(CloudUnavailableError):
            cloud.execute(self._task())

    def test_allow_fallback_true_enumerates(self) -> None:
        cloud = OrchestratorCloud(llm=None, fields_str="age",
                                  cloud_llm=FailingCloud(),
                                  allow_fallback=True)
        result = cloud.execute(self._task())
        assert result.content["features"]  # fallback enumeration output

    def test_default_is_fallback(self) -> None:
        cloud = OrchestratorCloud(llm=None, fields_str="age",
                                  cloud_llm=FailingCloud())
        result = cloud.execute(self._task())
        assert result.content["features"]


def _res(group: str, seed: int, strong_rate: float,
         valid: bool = True) -> ExperimentResult:
    return ExperimentResult(
        group=group, seed=seed, b_strong=5, strong_rate=strong_rate,
        b_quality=0.25, b_feat=10, total_proposals=20,
        avg_iv_first_half=0.2, avg_iv_second_half=0.3,
        valid=valid, fail_reason="" if valid else "cloud failed",
    )


class TestInvalidRunExclusion:
    def test_invalid_excluded_from_ci(self) -> None:
        results = [
            _res("A", 1, 0.20), _res("A", 2, 0.22),
            _res("A", 3, 0.99, valid=False),  # poison value must not leak in
            _res("D", 1, 0.40), _res("D", 2, 0.44),
        ]
        agg = summarize_runs(results)
        assert agg["summary"]["A"]["strong_rate"]["mean"] == pytest.approx(0.21)
        assert agg["n_valid"] == 4
        assert agg["n_invalid"] == 1
        assert agg["invalid_runs"][0]["group"] == "A"
        assert agg["invalid_runs"][0]["seed"] == 3

    def test_invalid_seed_dropped_from_ratio_pairs(self) -> None:
        results = [
            _res("A", 1, 0.20), _res("A", 2, 0.22), _res("A", 3, 0.24),
            _res("D", 1, 0.40), _res("D", 2, 0.44),
            _res("D", 3, 0.48, valid=False),
        ]
        agg = summarize_runs(results)
        ratio = agg["d_vs_a_ratio"]["strong_rate"]
        assert ratio["seeds"] == [1, 2]  # seed 3 pair dropped
        assert ratio["mean"] == pytest.approx(2.0)

    def test_all_invalid_yields_none_stats(self) -> None:
        agg = summarize_runs([_res("A", 1, 0.2, valid=False)])
        assert agg["summary"] == {}
        assert agg["d_vs_a_ratio"]["strong_rate"]["mean"] is None
        assert not agg["d_advantage_robust"]
        import json as _json
        _json.dumps(agg)  # serializable
