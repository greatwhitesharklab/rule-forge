"""G1 prompt builder (design doc §1.3): template + slot filling, no LLM.

    slots = retriever(payload)        # relevant experience, summary strings
    dead  = dead_end_lookup(payload)  # dead-end archive entries
    text  = TEMPLATE.render(...)      # section skeleton from templates.py
    return sanitize(text)             # §2.2 welded chain before anything leaves

The sanitized briefing is embedded into the returned TaskPackage context as
``g1_briefing``, so passing ``G1Prompt.package`` to SanitizedCloudExecutor
carries the G1 structure through the executor's hard-wired render -> sanitize
-> outbound-gate path (§7.3) instead of a hand-rolled bare prompt.

``retriever`` is any callable payload -> summary strings (production: a
SlotService-backed adapter; tests: a stub). It must stay encoder-agnostic —
no faiss/embedding imports live here.
"""

from __future__ import annotations

import json
import uuid
from dataclasses import dataclass
from datetime import date
from typing import Any, Callable, Sequence

from cloud.contracts import (
    TASK_TYPES,
    ContractError,
    TaskPackage,
    validate_package,
)
from cloud.sanitize import NerHook, sanitize_text, verify_outbound

from .templates import TEMPLATES

Retriever = Callable[[dict[str, Any]], Sequence[str]]
DeadEndLookup = Callable[[dict[str, Any]], Sequence[str]]

# Default output contracts, rendered into the prompt and enforced again by
# cloud.contracts.validate_result on the way back in.
DEFAULT_OUTPUT_SCHEMAS: dict[str, dict[str, Any]] = {
    "feature_proposal": {
        "type": "object",
        "required": ["features"],
        "properties": {
            "features": {
                "type": "array",
                "items": {
                    "type": "object",
                    "required": ["name", "expression", "rationale"],
                    "properties": {
                        "name": {"type": "string"},
                        "expression": {
                            "type": "string",
                            "note": "pandas/numpy expression over df; §3.2 sandbox whitelist applies",
                        },
                        "rationale": {"type": "string"},
                    },
                },
            }
        },
    },
    "case_analysis": {
        "type": "object",
        "required": ["findings", "conclusion", "rationale"],
        "properties": {
            "findings": {
                "type": "array",
                "items": {
                    "type": "object",
                    "required": ["claim", "evidence_features", "confidence"],
                    "properties": {
                        "claim": {"type": "string"},
                        "evidence_features": {"type": "array", "items": {"type": "string"}},
                        "confidence": {"type": "number", "minimum": 0.0, "maximum": 1.0},
                    },
                },
            },
            "conclusion": {"type": "string", "enum": ["approve", "reject", "review"]},
            "rationale": {"type": "string"},
        },
    },
    "explanation": {
        "type": "object",
        "required": ["explanation", "cited_features", "compliance_flags"],
        "properties": {
            "explanation": {"type": "string"},
            "cited_features": {"type": "array", "items": {"type": "string"}},
            "compliance_flags": {"type": "array", "items": {"type": "string"}},
        },
    },
}


@dataclass(frozen=True)
class G1Prompt:
    """Rendered G1 output: the outbound-ready text plus its TaskPackage."""

    package: TaskPackage
    text: str  # sanitized, outbound-gated briefing (== package.context["g1_briefing"])


def _numbered(items: Sequence[str], empty_note: str) -> str:
    if not items:
        return f"({empty_note})"
    return "\n".join(f"{i}. {entry}" for i, entry in enumerate(items, start=1))


def _json_block(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2)


def make_prompt(
    task_type: str,
    payload: dict[str, Any],
    *,
    retriever: Retriever | None = None,
    dead_end_lookup: DeadEndLookup | None = None,
    reference_date: date | None = None,
    ner_hook: NerHook | None = None,
) -> G1Prompt:
    """Render a G1 prompt for ``task_type`` and pass it through the §2.2 chain.

    ``payload`` carries contract-shaped "context"/"constraints" dicts plus an
    optional "task_id"/"output_schema". Raises ContractError on any contract
    violation and OutboundBlockedError if PII survives sanitization.
    """
    if task_type not in TASK_TYPES:
        raise ContractError(f"unknown task_type {task_type!r}; expected one of {TASK_TYPES}")
    context = payload.get("context")
    constraints = payload.get("constraints")
    if not isinstance(context, dict):
        raise ContractError("payload must carry a 'context' dict")
    if not isinstance(constraints, dict):
        raise ContractError("payload must carry a 'constraints' dict")
    output_schema = payload.get("output_schema") or DEFAULT_OUTPUT_SCHEMAS[task_type]

    experiences = list(retriever(payload)) if retriever is not None else []
    if dead_end_lookup is not None:
        dead_ends = list(dead_end_lookup(payload))
    else:
        # Contract-shaped fallback: feature_proposal context carries dead_ends.
        dead_ends = list(context.get("dead_ends", []))

    text = TEMPLATES[task_type].format(
        payload_section=_json_block(context),
        experience_section=_numbered(experiences, "no relevant local experience retrieved"),
        dead_end_section=_numbered(dead_ends, "dead-end archive is empty"),
        constraints_section=_json_block(constraints),
        schema_section=_json_block(output_schema),
    )
    safe_text = sanitize_text(text, reference_date=reference_date, ner_hook=ner_hook)
    verify_outbound(safe_text)  # welded gate (§2.2): nothing unsanitized leaves

    package = TaskPackage(
        task_id=str(payload.get("task_id") or f"g1-{task_type}-{uuid.uuid4().hex[:12]}"),
        task_type=task_type,
        context={**context, "g1_briefing": safe_text},
        constraints=dict(constraints),
        output_schema=output_schema,
    )
    validate_package(package)  # §2.3 contract on the way out, too
    return G1Prompt(package=package, text=safe_text)
