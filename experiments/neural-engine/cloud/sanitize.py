"""Outbound sanitization (design doc §2.2) — the only channel to the cloud.

Pipeline: PII removal (regex + pluggable NER hook) → numeric generalization
(amounts to bucket codes, dates to relative days, coordinates to city level)
→ structure preservation (statistics/ratios untouched) → outbound scan that
blocks any residual PII and fires an alert hook.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date, datetime, timezone
from typing import Callable

# NER hook: text -> [(start, end, label)] spans to redact as [PII].
NerHook = Callable[[str], list[tuple[int, int, str]]]
# Alert hook: called with the PII hits found at the outbound gate.
AlertHook = Callable[[list["PiiHit"]], None]


class OutboundBlockedError(RuntimeError):
    """Raised when residual PII is detected at the outbound boundary."""


@dataclass(frozen=True)
class PiiHit:
    kind: str
    start: int
    end: int
    text: str


# --- PII patterns -----------------------------------------------------------
# Order matters: ID cards (18 digits) must be redacted before bank cards
# (16-19 digits), or the bank-card rule would partially eat an ID number.
# Boundary guards: a digit run flanked by '.', letters, '_' or '%' is a
# decimal/exponent/identifier fragment (p_value, correlation, hash slice,
# percentage), never a card/ID — real card numbers in business text follow
# Chinese labels or punctuation and stand alone. Guarding on context (instead
# of requiring a 卡号/card keyword) keeps standalone card numbers redacted.
_NUMERIC_CTX = r"\d.%A-Za-z_"
_ID_CARD = re.compile(
    rf"(?<![{_NUMERIC_CTX}])\d{{6}}(?:19|20)\d{{2}}(?:0[1-9]|1[0-2])"
    rf"(?:0[1-9]|[12]\d|3[01])\d{{3}}[\dXx](?![{_NUMERIC_CTX}])"
)
_BANK_CARD = re.compile(rf"(?<![{_NUMERIC_CTX}])\d{{16,19}}(?![{_NUMERIC_CTX}])")
_PHONE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
# Labeled names only ("姓名：张伟" / "申请人为李娜"); a mandatory separator
# keeps "客户经理" / "申请人要求…" from being misread as names.
_NAME_LABELED = re.compile(
    r"((?:姓名|申请人|借款人|联系人|法人代表|法人|客户)\s*[:：为是]\s*)([一-龥]{2,4})(?![一-龥])"
)
_NAME_HONORIFIC = re.compile(r"[一-龥](?:先生|女士)")
_ADDRESS = re.compile(
    r"[一-龥]{2,}(?:省|市|自治区)(?:[一-龥]{1,10}(?:市|区|县|州)){0,2}"
    r"[一-龥]{0,10}(?:路|街|大道|巷|镇|村)[一-龥\d]{0,10}号(?:[一-龥\d]{0,6}室)?"
)
_COORD = re.compile(r"(?<!\d)\d{1,3}\.\d{4,}\s*[,，]\s*\d{1,3}\.\d{4,}(?!\d)")

# --- Generalization patterns -------------------------------------------------
_DATE_CN = re.compile(r"(?<!\d)(\d{4})年(\d{1,2})月(\d{1,2})日?")
_DATE_ISO = re.compile(r"(?<!\d)(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?!\d)")
# A number counts as an amount only with a currency symbol or unit attached;
# bare statistics ("n=100000", "0.032") pass through untouched.
_AMOUNT = re.compile(r"([¥￥])?\s*(\d[\d,]*(?:\.\d+)?)\s*(万元|万|元|块|人民币)?")

# Amount buckets in units of 万元 (10k CNY).
_BUCKETS: tuple[tuple[float, float, str], ...] = (
    (0, 1, "0-1W"),
    (1, 5, "1W-5W"),
    (5, 10, "5W-10W"),
    (10, 50, "10W-50W"),
    (50, 100, "50W-100W"),
    (100, 500, "100W-500W"),
)


def amount_bucket_code(wan: float) -> str:
    """Bucket code for an amount given in 万元 (e.g. 12.5 -> '10W-50W')."""
    for lo, hi, code in _BUCKETS:
        if lo <= wan < hi:
            return code
    return "500W+"


def _amount_sub(match: re.Match[str]) -> str:
    symbol, digits, unit = match.group(1), match.group(2), match.group(3)
    if symbol is None and unit is None:
        return match.group(0)  # bare number, not an amount — keep as-is
    value = float(digits.replace(",", ""))
    wan = value / 10000.0 if unit in ("元", "块", "人民币") else value
    if unit is None:  # currency symbol without unit, e.g. "¥35,000"
        wan = value / 10000.0
    return f"<AMT:{amount_bucket_code(wan)}>"


def _relative_days(ref: date, y: str, m: str, d: str) -> str:
    days = (ref - date(int(y), int(m), int(d))).days
    return f"D-{days}" if days >= 0 else f"D+{-days}"


def _date_sub(ref: date) -> Callable[[re.Match[str]], str]:
    def sub(match: re.Match[str]) -> str:
        try:
            return _relative_days(ref, match.group(1), match.group(2), match.group(3))
        except ValueError:
            return match.group(0)  # not a real date (e.g. "2024-13-40")

    return sub


def sanitize_text(
    text: str,
    *,
    reference_date: date | None = None,
    ner_hook: NerHook | None = None,
) -> str:
    """Redact PII and generalize amounts/dates/coordinates in ``text``.

    ``reference_date`` anchors date-to-relative-days conversion (defaults to
    today, UTC); inject it in tests and audit replays for determinism.
    """
    ref = reference_date or datetime.now(timezone.utc).date()

    out = text
    if ner_hook is not None:
        spans = ner_hook(out)
        for start, end, _label in sorted(spans, reverse=True):
            out = out[:start] + "[PII]" + out[end:]

    out = _ID_CARD.sub("[ID_CARD]", out)
    out = _BANK_CARD.sub("[BANK_CARD]", out)
    out = _PHONE.sub("[PHONE]", out)
    out = _NAME_LABELED.sub(lambda m: m.group(1) + "[NAME]", out)
    out = _NAME_HONORIFIC.sub("[NAME]", out)
    out = _ADDRESS.sub("[ADDRESS]", out)
    out = _COORD.sub("<GEO_CITY>", out)
    out = _DATE_CN.sub(_date_sub(ref), out)
    out = _DATE_ISO.sub(_date_sub(ref), out)
    out = _AMOUNT.sub(_amount_sub, out)
    return out


def scan_pii(text: str) -> list[PiiHit]:
    """Scan for identity PII. Used as the final gate before anything leaves."""
    hits: list[PiiHit] = []
    for kind, pattern in (
        ("id_card", _ID_CARD),
        ("bank_card", _BANK_CARD),
        ("phone", _PHONE),
        ("name", _NAME_LABELED),
        ("name", _NAME_HONORIFIC),
        ("address", _ADDRESS),
        ("coordinates", _COORD),
    ):
        for m in pattern.finditer(text):
            hits.append(PiiHit(kind=kind, start=m.start(), end=m.end(), text=m.group(0)))
    return sorted(hits, key=lambda h: h.start)


_module_alert_hook: AlertHook | None = None


def set_alert_hook(hook: AlertHook | None) -> None:
    """Install a process-wide alert hook fired whenever the gate blocks."""
    global _module_alert_hook
    _module_alert_hook = hook


def verify_outbound(text: str, *, alert_hook: AlertHook | None = None) -> None:
    """Final gate: raise OutboundBlockedError if any PII remains in ``text``."""
    hits = scan_pii(text)
    if not hits:
        return
    if alert_hook is not None:
        alert_hook(hits)
    if _module_alert_hook is not None:
        _module_alert_hook(hits)
    kinds = sorted({h.kind for h in hits})
    raise OutboundBlockedError(f"outbound payload blocked: residual PII {kinds}")
