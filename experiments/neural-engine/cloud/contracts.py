"""Task package contracts (design doc §2.3).

Three task types cross the outbound boundary: ``feature_proposal``,
``case_analysis`` and ``explanation``. ``jsonschema`` is not available in this
environment, so validation is hand-rolled: required keys, types, enums and
ranges are checked for both inbound packages and outbound results.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from typing import Any

TASK_TYPES = ("feature_proposal", "case_analysis", "explanation")

_AUDIENCES = ("customer", "auditor", "regulator")
_CONCLUSIONS = ("approve", "reject", "review")


class ContractError(ValueError):
    """Raised when a task package or result violates the §2.3 contract."""


@dataclass(frozen=True)
class Provenance:
    """Per-call provenance record (§2.1): who served the call and at what cost."""

    provider: str
    model: str
    model_version: str | None
    timestamp: str
    prompt_hash: str
    cost_tokens: int

    def as_dict(self) -> dict[str, Any]:
        return {
            "provider": self.provider,
            "model": self.model,
            "model_version": self.model_version,
            "timestamp": self.timestamp,
            "prompt_hash": self.prompt_hash,
            "cost_tokens": self.cost_tokens,
        }


@dataclass
class TaskPackage:
    """Sanitizable unit of work sent to a cloud provider."""

    task_id: str
    task_type: str
    context: dict[str, Any]
    constraints: dict[str, Any]
    output_schema: dict[str, Any]


@dataclass
class TaskResult:
    """Cloud output — always a *candidate* until the local verifier accepts it."""

    task_id: str
    task_type: str
    content: dict[str, Any]
    provenance: Provenance


# Required fields per task type: {section: {field: expected_type}}.
_PACKAGE_SPECS: dict[str, dict[str, dict[str, type]]] = {
    "feature_proposal": {
        "context": {"case_profiles": list, "existing_features": list, "dead_ends": list},
        "constraints": {"max_features": int, "must_be_executable": str, "no_future_info": bool},
    },
    "case_analysis": {
        "context": {"case_profile": dict, "similar_cases": list, "questions": list},
        "constraints": {"max_findings": int, "cite_features": bool},
    },
    "explanation": {
        "context": {"decision": str, "feature_contributions": list, "audience": str},
        "constraints": {"max_length": int, "no_fabricated_fields": bool},
    },
}


def _check_fields(mapping: dict[str, Any], spec: dict[str, type], where: str) -> None:
    for name, expected in spec.items():
        if name not in mapping:
            raise ContractError(f"{where}: missing required field {name!r}")
        value = mapping[name]
        # bool is a subclass of int; keep the two strictly separate.
        if expected is int and isinstance(value, bool):
            raise ContractError(f"{where}: field {name!r} must be int, got bool")
        if not isinstance(value, expected):
            raise ContractError(
                f"{where}: field {name!r} must be {expected.__name__}, got {type(value).__name__}"
            )


def validate_package(pkg: TaskPackage) -> None:
    """Validate an inbound task package; raise ContractError on any violation."""
    if pkg.task_type not in TASK_TYPES:
        raise ContractError(f"unknown task_type {pkg.task_type!r}; expected one of {TASK_TYPES}")
    if not pkg.task_id:
        raise ContractError("task_id must be a non-empty string")
    if not pkg.output_schema:
        raise ContractError("output_schema must be a non-empty dict")

    spec = _PACKAGE_SPECS[pkg.task_type]
    _check_fields(pkg.context, spec["context"], f"{pkg.task_type}.context")
    _check_fields(pkg.constraints, spec["constraints"], f"{pkg.task_type}.constraints")

    c = pkg.constraints
    if "max_features" in c and c["max_features"] < 1:
        raise ContractError("max_features must be >= 1")
    if "max_findings" in c and c["max_findings"] < 1:
        raise ContractError("max_findings must be >= 1")
    if "max_length" in c and c["max_length"] < 1:
        raise ContractError("max_length must be >= 1")
    if pkg.task_type == "explanation" and pkg.context["audience"] not in _AUDIENCES:
        raise ContractError(f"audience must be one of {_AUDIENCES}")


def _validate_features(content: dict[str, Any]) -> None:
    features = content.get("features")
    if not isinstance(features, list):
        raise ContractError("feature_proposal result: 'features' must be a list")
    for i, item in enumerate(features):
        if not isinstance(item, dict):
            raise ContractError(f"features[{i}] must be an object")
        for name in ("name", "expression", "rationale"):
            if not isinstance(item.get(name), str) or not item[name]:
                raise ContractError(f"features[{i}]: missing/invalid field {name!r}")


def _validate_analysis(content: dict[str, Any]) -> None:
    findings = content.get("findings")
    if not isinstance(findings, list):
        raise ContractError("case_analysis result: 'findings' must be a list")
    for i, item in enumerate(findings):
        if not isinstance(item, dict):
            raise ContractError(f"findings[{i}] must be an object")
        if not isinstance(item.get("claim"), str) or not item["claim"]:
            raise ContractError(f"findings[{i}]: missing/invalid field 'claim'")
        if not isinstance(item.get("evidence_features"), list):
            raise ContractError(f"findings[{i}]: 'evidence_features' must be a list")
        conf = item.get("confidence")
        if isinstance(conf, bool) or not isinstance(conf, (int, float)) or not 0.0 <= conf <= 1.0:
            raise ContractError(f"findings[{i}]: 'confidence' must be a number in [0, 1]")
    if content.get("conclusion") not in _CONCLUSIONS:
        raise ContractError(f"conclusion must be one of {_CONCLUSIONS}")
    if not isinstance(content.get("rationale"), str):
        raise ContractError("case_analysis result: 'rationale' must be a string")


def _validate_explanation(content: dict[str, Any]) -> None:
    if not isinstance(content.get("explanation"), str) or not content["explanation"]:
        raise ContractError("explanation result: 'explanation' must be a non-empty string")
    for name in ("cited_features", "compliance_flags"):
        value = content.get(name)
        if not isinstance(value, list) or not all(isinstance(v, str) for v in value):
            raise ContractError(f"explanation result: {name!r} must be a list of strings")


_RESULT_VALIDATORS = {
    "feature_proposal": _validate_features,
    "case_analysis": _validate_analysis,
    "explanation": _validate_explanation,
}


def validate_result(task_type: str, content: dict[str, Any]) -> None:
    """Validate a cloud result against the task type's output contract."""
    validator = _RESULT_VALIDATORS.get(task_type)
    if validator is None:
        raise ContractError(f"unknown task_type {task_type!r}; expected one of {TASK_TYPES}")
    if not isinstance(content, dict):
        raise ContractError("result content must be a JSON object")
    validator(content)


def render_prompt(pkg: TaskPackage) -> str:
    """Render a package as deterministic JSON (stable hashing, audit replay)."""
    return json.dumps(
        {
            "task": pkg.task_type,
            "context": pkg.context,
            "constraints": pkg.constraints,
            "output_schema": pkg.output_schema,
        },
        ensure_ascii=False,
        sort_keys=True,
    )


def prompt_hash(text: str) -> str:
    """SHA-256 hex digest of the (sanitized) outbound prompt."""
    return hashlib.sha256(text.encode("utf-8")).hexdigest()
