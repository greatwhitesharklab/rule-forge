"""Cloud provider abstraction (design doc §2.1).

``CloudLLM`` is the vendor-neutral protocol; ``PROVIDERS`` holds config-driven
OpenAI-compatible endpoints (DeepSeek example + fallback slot). API keys come
from environment variables only — never hard-coded.

The structural guarantee (§7.3): ``SanitizedCloudExecutor.execute`` is a
template method that sanitizes the rendered prompt and re-scans it *before*
calling ``_transport``. Subclasses only implement ``_transport``, which
receives the sanitized string — raw payloads never reach the network layer.
"""

from __future__ import annotations

import json
import os
from abc import ABC, abstractmethod
from dataclasses import dataclass
from datetime import date, datetime, timezone
from typing import Any, Protocol, runtime_checkable

from tenacity import Retrying, retry_if_exception_type, stop_after_attempt, wait_exponential

from .contracts import (
    ContractError,
    Provenance,
    TaskPackage,
    TaskResult,
    prompt_hash,
    render_prompt,
    validate_package,
    validate_result,
)
from .ledger import CostLedger
from .sanitize import AlertHook, NerHook, OutboundBlockedError, sanitize_text, verify_outbound


class ProviderConfigError(RuntimeError):
    """Raised when a provider is misconfigured (unknown vendor, missing key)."""


class TaskResultError(RuntimeError):
    """Raised when a cloud response cannot be parsed into a valid result."""


@runtime_checkable
class CloudLLM(Protocol):
    """Vendor-neutral execution contract: cloud executes, local verifies."""

    def execute(self, task: TaskPackage) -> TaskResult: ...


@dataclass(frozen=True)
class ProviderConfig:
    base_url: str
    model: str
    api_key_env: str
    base_url_env: str | None = None


# OpenAI-compatible endpoints; add vendors here, not in code.
# deepseek-v4-flash(2026-07):MoE 284B/13B 激活,$0.14/$0.28 per 1M tokens,
# 比 v3 便宜 35-100x,1M context。替代已下线的 deepseek-reasoner。
PROVIDERS: dict[str, ProviderConfig] = {
    "deepseek": ProviderConfig(
        base_url="https://api.deepseek.com",
        model="deepseek-v4-flash",
        api_key_env="DEEPSEEK_API_KEY",
    ),
    "fallback": ProviderConfig(
        base_url="",
        model="",
        api_key_env="CLOUD_FALLBACK_API_KEY",
        base_url_env="CLOUD_FALLBACK_BASE_URL",
    ),
}


@dataclass(frozen=True)
class TransportResponse:
    """What a transport call returns, before contract validation."""

    content: str
    model_version: str | None
    input_tokens: int
    output_tokens: int


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _parse_json_content(raw: str) -> dict[str, Any]:
    text = raw.strip()
    if text.startswith("```"):  # tolerate fenced JSON despite instructions
        text = text.strip("`")
        if text.startswith("json"):
            text = text[4:]
    try:
        value = json.loads(text)
    except json.JSONDecodeError as exc:
        raise TaskResultError(f"cloud response is not valid JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise TaskResultError("cloud response must be a JSON object")
    return value


class SanitizedCloudExecutor(ABC):
    """Template method: sanitize + outbound gate are hard-wired before transport."""

    provider_name: str
    model_name: str

    def __init__(
        self,
        *,
        ledger: CostLedger | None = None,
        reference_date: date | None = None,
        ner_hook: NerHook | None = None,
        alert_hook: AlertHook | None = None,
    ) -> None:
        self._ledger = ledger
        self._reference_date = reference_date
        self._ner_hook = ner_hook
        self._alert_hook = alert_hook

    def execute(self, task: TaskPackage) -> TaskResult:
        validate_package(task)
        safe_prompt = sanitize_text(
            render_prompt(task),
            reference_date=self._reference_date,
            ner_hook=self._ner_hook,
        )
        p_hash = prompt_hash(safe_prompt)
        try:
            verify_outbound(safe_prompt, alert_hook=self._alert_hook)
        except OutboundBlockedError as exc:
            self._record(task, p_hash, status="blocked", error=str(exc), cost=0, model_version=None)
            raise

        try:
            raw = self._transport(safe_prompt, task)
            content = _parse_json_content(raw.content)
            validate_result(task.task_type, content)
        except (TaskResultError, ContractError) as exc:
            self._record(task, p_hash, status="error", error=f"invalid result: {exc}",
                         cost=0, model_version=None)
            raise TaskResultError(str(exc)) from exc
        except Exception as exc:
            self._record(task, p_hash, status="error", error=str(exc), cost=0, model_version=None)
            raise

        cost = raw.input_tokens + raw.output_tokens
        prov = Provenance(
            provider=self.provider_name,
            model=self.model_name,
            model_version=raw.model_version,
            timestamp=_utc_now(),
            prompt_hash=p_hash,
            cost_tokens=cost,
        )
        self._record(task, p_hash, status="ok", error=None, cost=cost,
                     model_version=raw.model_version)
        return TaskResult(task_id=task.task_id, task_type=task.task_type,
                          content=content, provenance=prov)

    def _record(self, task: TaskPackage, p_hash: str, *, status: str,
                error: str | None, cost: int, model_version: str | None) -> None:
        if self._ledger is None:
            return
        self._ledger.record(
            ts=_utc_now(), task_id=task.task_id, task_type=task.task_type,
            provider=self.provider_name, model=self.model_name,
            model_version=model_version, prompt_hash=p_hash,
            cost_tokens=cost, status=status, error=error,
        )

    @abstractmethod
    def _transport(self, safe_prompt: str, task: TaskPackage) -> TransportResponse:
        """Send the *sanitized* prompt to the vendor and return the raw reply."""
        raise NotImplementedError


class OpenAIProvider(SanitizedCloudExecutor):
    """Real provider over the OpenAI-compatible chat completions API."""

    def __init__(
        self,
        provider: str = "deepseek",
        *,
        api_key: str | None = None,
        base_url: str | None = None,
        model: str | None = None,
        client: Any | None = None,
        max_retries: int = 3,
        enable_retry: bool = True,
        temperature: float = 0.3,
        reasoning_effort: str | None = "low",
        **kwargs: Any,
    ) -> None:
        super().__init__(**kwargs)
        cfg = PROVIDERS.get(provider)
        if cfg is None:
            raise ProviderConfigError(
                f"unknown provider {provider!r}; known: {sorted(PROVIDERS)}"
            )
        self.provider_name = provider
        self.model_name = model or cfg.model
        # Variance control (measured 2026-08 on deepseek-v4-flash):
        # - temperature=0 is FORBIDDEN: greedy decoding makes the reasoning
        #   model loop (observed 125k reasoning chars, 16k-token truncation,
        #   empty content). Default 0.3 keeps variance low without looping.
        # - reasoning_effort='low' caps the reasoning token budget (large
        #   feature_proposal prompts otherwise burn >8k tokens on reasoning
        #   alone). Set to None for models that reject the parameter.
        self._temperature = temperature
        self._reasoning_effort = reasoning_effort

        if client is None:
            key = api_key or os.environ.get(cfg.api_key_env, "")
            if not key:
                raise ProviderConfigError(
                    f"provider {provider!r} needs an API key: set env {cfg.api_key_env} "
                    "(keys come from the environment, never from source code)"
                )
            url = base_url or (
                os.environ.get(cfg.base_url_env, "") if cfg.base_url_env else ""
            ) or cfg.base_url
            if not url:
                raise ProviderConfigError(
                    f"provider {provider!r} has no base_url: configure it via "
                    f"{cfg.base_url_env or 'PROVIDERS'}"
                )
            from openai import OpenAI

            client = OpenAI(base_url=url, api_key=key)
        self._client = client

        self._retrying = (
            Retrying(
                stop=stop_after_attempt(max_retries),
                wait=wait_exponential(multiplier=0.5, min=0.5, max=8),
                retry=retry_if_exception_type(Exception),
                reraise=True,
            )
            if enable_retry
            else None
        )

    def _transport(self, safe_prompt: str, task: TaskPackage) -> TransportResponse:
        messages = [
            {
                "role": "system",
                "content": (
                    "You are a credit-risk execution engine. Respond with a single "
                    "strict JSON object matching the requested output_schema. "
                    "No prose, no code fences."
                ),
            },
            {"role": "user", "content": safe_prompt},
        ]

        def _call() -> Any:
            # v4-flash 是推理模型,reasoning 占 token;实测大 context prompt
            # (feature_proposal ~8KB)reasoning 可超 4000 token,max_tokens=4096
            # 会全部耗尽导致 content 为空(finish_reason=length)。8192 才够。
            kwargs: dict[str, Any] = {}
            if self._reasoning_effort is not None:
                kwargs["reasoning_effort"] = self._reasoning_effort
            resp = self._client.chat.completions.create(
                model=self.model_name, messages=messages, max_tokens=8192,
                temperature=self._temperature, **kwargs,
            )
            content = resp.choices[0].message.content or ""
            # Validate INSIDE the retried call: empty content / non-JSON
            # responses are retried (tenacity, up to max_retries) instead of
            # leaking into the run as silent fallback pollution.
            if not content.strip():
                raise TaskResultError(
                    "cloud returned empty content (reasoning token budget?)")
            _parse_json_content(content)
            return resp

        resp = self._retrying(_call) if self._retrying is not None else _call()
        usage = getattr(resp, "usage", None)
        return TransportResponse(
            content=resp.choices[0].message.content or "",
            model_version=getattr(resp, "model", None),
            input_tokens=int(getattr(usage, "prompt_tokens", 0) or 0),
            output_tokens=int(getattr(usage, "completion_tokens", 0) or 0),
        )


class MockProvider(SanitizedCloudExecutor):
    """Deterministic offline provider for tests and key-less demos.

    Returns contract-valid canned output per task type; goes through the same
    sanitize + gate + provenance path as real providers.
    """

    provider_name = "mock"
    model_name = "mock-deterministic-1"

    _CANNED: dict[str, dict[str, Any]] = {
        "feature_proposal": {
            "features": [
                {
                    "name": "mock_income_stability",
                    "expression": "income_std_30d / income_mean_30d",
                    "rationale": "deterministic mock: unstable income predicts default",
                }
            ]
        },
        "case_analysis": {
            "findings": [
                {
                    "claim": "high leverage observed in case profile",
                    "evidence_features": ["debt_ratio"],
                    "confidence": 0.8,
                }
            ],
            "conclusion": "review",
            "rationale": "deterministic mock analysis",
        },
        "explanation": {
            "explanation": "deterministic mock explanation citing debt_ratio",
            "cited_features": ["debt_ratio"],
            "compliance_flags": [],
        },
    }

    def __init__(self, *, input_tokens: int = 128, output_tokens: int = 64, **kwargs: Any) -> None:
        super().__init__(**kwargs)
        self._input_tokens = input_tokens
        self._output_tokens = output_tokens

    def _transport(self, safe_prompt: str, task: TaskPackage) -> TransportResponse:
        return TransportResponse(
            content=json.dumps(self._CANNED[task.task_type], ensure_ascii=False),
            model_version="mock-v1",
            input_tokens=self._input_tokens,
            output_tokens=self._output_tokens,
        )
