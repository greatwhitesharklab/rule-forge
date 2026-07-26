"""Scribe induction pipeline: case groups -> local LLM -> ExperienceDrafts.

The local LLM does the semantic work the encoder cannot (design doc §1.1):
it reads a group of similar, de-identified canonical case summaries and
induces auditable one-sentence rule statements.

Everything the LLM touches is treated as hostile input:

  * output is JSON-extracted (clean / ```json fenced / chatter-wrapped),
  * schema-validated (top-level dict, ``experiences`` list, statement str),
  * retried exactly once with the parse error fed back into the prompt,
  * and on total failure the group degrades to ZERO drafts — no exception
    ever escapes induce(); failures are counted in ``last_report``.

Grouping is deliberately simple: cases sharing the first
``GROUP_PREFIX_FIELDS`` canonical-text segments form one group (same profile
head, any outcome mix). Outcome is NOT part of the key, so mixed-outcome
groups are reachable and their lean is derived from the distribution.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any, Callable, Sequence

from embed.canonicalize import FIELD_MAP, OUTCOME_TEXT, canonicalize

from .draft import ExperienceDraft, ScribeCase

GenerateFn = Callable[[str], str]

GROUP_PREFIX_FIELDS = 4  # canonical segments forming the group key
MAX_CASES_PER_PROMPT = 12  # deterministic head sample shown to the LLM
MAX_EXPERIENCES_PER_GROUP = 3
BUSINESS_TERMS = tuple(spec.cn_label for spec in FIELD_MAP)

_FENCE_RE = re.compile(r"```(?:json)?\s*(.*?)```", re.DOTALL)


@dataclass
class InduceReport:
    """Auditable counters for one induce() call."""

    groups: int = 0
    prompts: int = 0  # LLM calls made (including the retry)
    retries: int = 0
    failed_groups: int = 0  # groups that degraded to zero drafts
    llm_errors: int = 0  # generate_fn raised


def group_cases(cases: Sequence[ScribeCase]) -> list[list[ScribeCase]]:
    """Bucket cases by their canonical-text head (first 4 segments).

    Insertion order is preserved so grouping is deterministic for a fixed
    input order.
    """
    buckets: dict[str, list[ScribeCase]] = {}
    for case in cases:
        head = ";".join(
            canonicalize(case.row).split(";")[:GROUP_PREFIX_FIELDS]
        )
        buckets.setdefault(head, []).append(case)
    return list(buckets.values())


def group_outcome(cases: Sequence[ScribeCase]) -> tuple[str | None, bool]:
    """Derive the group's outcome lean: uniform -> "good"/"bad"; both
    present -> (None, mixed=True); none matured -> (None, False)."""
    known = {c.outcome for c in cases if c.outcome is not None}
    if len(known) == 1:
        return next(iter(known)), False
    if len(known) == 2:
        return None, True
    return None, False


def build_prompt(
    group: Sequence[ScribeCase],
    error: str | None = None,
    max_cases: int = MAX_CASES_PER_PROMPT,
) -> str:
    """Chinese induction prompt: numbered canonical summaries + JSON contract."""
    shown = group[:max_cases]
    sample_note = (
        f",抽样展示前 {len(shown)} 条" if len(group) > len(shown) else ""
    )
    lines = []
    for i, case in enumerate(shown, 1):
        outcome = OUTCOME_TEXT.get(case.outcome, "结局:未知")  # type: ignore[arg-type]
        lines.append(f"{i}. {canonicalize(case.row)};{outcome}")
    cases_block = "\n".join(lines)
    terms = "、".join(BUSINESS_TERMS)
    prompt = f"""你是信贷审批经验归纳员(Scribe)。下面是一组画像相似的贷款申请案例,字段已脱敏为分档标签。
每行格式:序号. 画像;结局

案例(共 {len(group)} 条{sample_note}):
{cases_block}

已知业务字段:{terms}

任务:归纳这组案例体现的规律,最多 {MAX_EXPERIENCES_PER_GROUP} 条。要求:
- statement: 一句话中文规律陈述,必须引用至少一个已知业务字段,可审计,不得编造字段之外的信息
- conditions: 该规律的适用条件(引用上面的分档标签)
- evidence_count: 支持该规律的案例条数

只输出 JSON,不要输出任何其他文字:
{{"experiences": [{{"statement": "...", "conditions": "...", "evidence_count": 1}}]}}"""
    if error is not None:
        prompt += f"\n\n上次输出无法解析:{error}\n请重新输出,只输出上面要求的 JSON。"
    return prompt


def _brace_candidates(text: str):
    """Yield balanced {...} substrings (string-aware scan), outermost first."""
    start = 0
    while True:
        s = text.find("{", start)
        if s < 0:
            return
        depth = 0
        in_str = False
        esc = False
        for i in range(s, len(text)):
            ch = text[i]
            if in_str:
                if esc:
                    esc = False
                elif ch == "\\":
                    esc = True
                elif ch == '"':
                    in_str = False
                continue
            if ch == '"':
                in_str = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    yield text[s : i + 1]
                    break
        start = s + 1


def extract_json(text: str) -> dict[str, Any]:
    """Pull the first parseable JSON object out of raw LLM output.

    Tries, in order: the whole stripped text, ``` fenced blocks, then
    balanced-brace candidates (covers leading/trailing chatter). Raises
    ValueError with a short reason when nothing parses into a dict.
    """
    candidates = [text.strip()]
    candidates.extend(m.group(1).strip() for m in _FENCE_RE.finditer(text))
    candidates.extend(_brace_candidates(text))
    last_error = "no JSON object found"
    for cand in candidates:
        if not cand:
            continue
        try:
            obj = json.loads(cand)
        except json.JSONDecodeError as exc:
            last_error = f"JSONDecodeError: {exc.msg}"
            continue
        if isinstance(obj, dict):
            return obj
        last_error = f"top-level JSON is {type(obj).__name__}, not an object"
    raise ValueError(last_error)


def validate_payload(
    obj: dict[str, Any], group_size: int, max_experiences: int
) -> list[tuple[str, str, int]]:
    """Schema-check the payload; returns (statement, conditions, evidence).

    Items missing a string ``statement`` are skipped silently — they never
    become drafts. evidence_count defaults to the group size and is clamped
    to [1, group_size]: the LLM may not claim evidence it never saw.
    """
    experiences = obj.get("experiences")
    if not isinstance(experiences, list):
        raise ValueError('"experiences" missing or not a list')
    out: list[tuple[str, str, int]] = []
    for item in experiences[:max_experiences]:
        if not isinstance(item, dict):
            continue
        statement = item.get("statement")
        if not isinstance(statement, str) or not statement.strip():
            continue
        conditions = item.get("conditions")
        evidence = item.get("evidence_count")
        try:
            ev = int(evidence)
        except (TypeError, ValueError):
            ev = group_size
        ev = max(1, min(ev, group_size))
        out.append((statement.strip(),
                    conditions.strip() if isinstance(conditions, str) else "",
                    ev))
    return out


def _chat_wrap(prompt: str) -> str:
    """Qwen3 chat-format wrapper with an empty think block.

    LocalLLM.generate() is a raw completer — without chat markers the 0.6B
    model just echoes the JSON template back. The empty <think></think>
    prefill forces Qwen3's non-thinking mode, so the continuation IS the
    JSON answer. Special tokens are stripped on decode; any residual chatter
    is handled by extract_json.
    """
    return (
        f"<|im_start|>user\n{prompt}<|im_end|>\n"
        "<|im_start|>assistant\n<think>\n\n</think>\n"
    )


class Scribe:
    """LLM experience inducer. generate_fn is injectable (tests use fakes)."""

    def __init__(
        self,
        generate_fn: GenerateFn,
        *,
        max_cases_per_prompt: int = MAX_CASES_PER_PROMPT,
        max_experiences_per_group: int = MAX_EXPERIENCES_PER_GROUP,
    ) -> None:
        self._generate = generate_fn
        self.max_cases_per_prompt = max_cases_per_prompt
        self.max_experiences_per_group = max_experiences_per_group
        self.last_report = InduceReport()

    @classmethod
    def from_llm(
        cls,
        llm: Any,  # llm.LocalLLM; typed loosely to keep import optional
        *,
        max_new_tokens: int = 256,
        temperature: float = 0.0,
        **kwargs: Any,
    ) -> "Scribe":
        """Wrap a LocalLLM's generate() as the induction generate_fn.

        The prompt is chat-wrapped (see _chat_wrap): raw completion makes
        the 0.6B model echo the JSON template instead of filling it in.
        """
        return cls(
            lambda prompt: llm.generate(
                _chat_wrap(prompt),
                max_new_tokens=max_new_tokens,
                temperature=temperature,
            ),
            **kwargs,
        )

    def induce(self, cases: Sequence[ScribeCase]) -> list[ExperienceDraft]:
        """Group cases, induce drafts per group. Never raises."""
        report = InduceReport()
        drafts: list[ExperienceDraft] = []
        groups = group_cases(cases)
        report.groups = len(groups)
        for group in groups:
            outcome, mixed = group_outcome(group)
            for statement, conditions, evidence in self._induce_group(group, report):
                drafts.append(ExperienceDraft(
                    statement=statement,
                    conditions=conditions,
                    evidence_count=evidence,
                    case_ids=tuple(c.case_id for c in group),
                    outcome=outcome,
                    mixed=mixed,
                    regime_tag=group[0].regime_tag,
                ))
        self.last_report = report
        return drafts

    def _induce_group(
        self, group: Sequence[ScribeCase], report: InduceReport
    ) -> list[tuple[str, str, int]]:
        """One group: prompt -> parse -> validate; retry once with feedback."""
        error: str | None = None
        for attempt in range(2):
            prompt = build_prompt(group, error, self.max_cases_per_prompt)
            report.prompts += 1
            if attempt:
                report.retries += 1
            try:
                text = self._generate(prompt)
            except Exception:
                # A broken generator will not heal by retrying; degrade.
                report.llm_errors += 1
                return []
            try:
                obj = extract_json(text)
                return validate_payload(
                    obj, len(group), self.max_experiences_per_group
                )
            except ValueError as exc:
                error = str(exc)
        report.failed_groups += 1
        return []
