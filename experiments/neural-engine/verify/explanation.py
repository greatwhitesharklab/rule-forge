"""Explanation-text verifier (design doc §3.1 row 3): rule checks.

Checks, in order:
1. every cited feature must exist in the given feature manifest — a name
   outside it is a fabricated field (无虚构字段);
2. the text must not contain configurable compliance banned words
   (case-insensitive substring match);
3. the text must fit the length budget.

Q is graded for the reputation ledger: 1.0 when clean, minus 0.5 per
violation, floored at 0.0.
"""

from __future__ import annotations

from typing import Sequence

from .verdict import FAIL, PASS, Verdict

# Minimal default compliance list; deployments extend it via `banned_words`.
DEFAULT_BANNED_WORDS: tuple[str, ...] = (
    "guaranteed approval",
    "保证下款",
    "百分百通过",
    "无视征信",
)


def verify_explanation(
    text: str,
    cited_features: Sequence[str],
    allowed_features: Sequence[str],
    *,
    banned_words: Sequence[str] = DEFAULT_BANNED_WORDS,
    max_length: int = 2000,
) -> Verdict:
    """Rule-check an explanation; returns the violation list in `reasons`."""
    violations: list[str] = []
    allowed = set(allowed_features)
    for name in cited_features:
        if name not in allowed:
            violations.append(f"fabricated feature cited: {name!r} not in feature manifest")
    lowered = text.lower()
    for word in banned_words:
        if word.lower() in lowered:
            violations.append(f"banned compliance word present: {word!r}")
    if len(text) > max_length:
        violations.append(f"length {len(text)} exceeds max_length {max_length}")

    quality = max(0.0, 1.0 - 0.5 * len(violations))
    status = PASS if not violations else FAIL
    return Verdict(
        status, quality, tuple(violations),
        {"violations": float(len(violations)), "length": float(len(text))},
    )
