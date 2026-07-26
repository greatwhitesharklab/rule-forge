"""LendingClub canonical-text mapping: decision-time fields -> stable banded
retrieval text for writable memory (the LendingClub counterpart of
embed/canonicalize.py, which is synth-world specific).

Design constraints (see experiment brief):
- 组合空间必须有界:7 个字段(grade/term/purpose + dti/inc/inq/util 分档),
  全量 114 万行实测唯一文本 23,375 个 — 去重嵌入缓存后编码成本可忽略。
- emp_title_norm 不进 canonical(高基数爆炸),仅在写槽时作为 value_text
  附注保留,为后续文本记忆留口。
- 只用放款决策时刻可得字段(§8.3 铁律二),分档边界是业务常量,不从数据学习。
"""

from __future__ import annotations

import numpy as np
import pandas as pd

# 借款用途 -> 中文标签(审计友好);未列出用途回退原始 token。
PURPOSE_ZH: dict[str, str] = {
    "debt_consolidation": "债务整合",
    "credit_card": "信用卡还款",
    "home_improvement": "房屋装修",
    "other": "其他",
    "major_purchase": "大额消费",
    "small_business": "小微经营",
    "car": "购车",
    "medical": "医疗",
    "moving": "搬迁",
    "house": "购房",
    "vacation": "度假",
    "wedding": "婚礼",
    "renewable_energy": "新能源",
    "educational": "教育",
}

OUTCOME_ZH: dict[str, str] = {"good": "结局:正常还款", "bad": "结局:违约"}

_MISSING = "缺失"


def _band(series: pd.Series, edges: tuple[float, ...], labels: tuple[str, ...]) -> pd.Series:
    """Upper-bound-inclusive binning; NaN -> 缺失. edges ascending."""
    vals = pd.to_numeric(series, errors="coerce")
    idx = np.searchsorted(np.asarray(edges, dtype=float), vals.to_numpy(dtype=float), side="left")
    out = np.where(np.isnan(vals.to_numpy(dtype=float)), _MISSING,
                   np.asarray(labels)[np.clip(idx, 0, len(labels) - 1)])
    return pd.Series(out, index=series.index)


def canonical_series(df: pd.DataFrame) -> pd.Series:
    """Vectorized canonical text for a LendingClub episodes frame.

    Field order is fixed; every case renders the same 7 segments, so the text
    is a stable, auditable retrieval key.
    """
    grade = df["grade"].fillna(_MISSING).astype(str)
    term = df["term_months"].astype("float").map(
        lambda v: _MISSING if np.isnan(v) else f"{int(v)}月"
    )
    purpose = df["purpose"].map(
        lambda v: _MISSING if v is None or (isinstance(v, float) and np.isnan(v))
        else PURPOSE_ZH.get(str(v), str(v))
    )
    dti = _band(df["dti"], (12.0, 20.0, 28.0), ("低", "中", "偏高", "高"))
    inc = _band(df["annual_inc"], (40000.0, 70000.0, 110000.0),
                ("低", "中", "高", "极高"))
    inq_vals = pd.to_numeric(df["inq_last_6mths"], errors="coerce")
    inq = pd.Series(
        np.where(np.isnan(inq_vals), _MISSING,
                 np.where(inq_vals < 0.5, "无",
                          np.where(inq_vals < 1.5, "一次", "多次"))),
        index=df.index,
    )
    util = _band(df["revol_util"], (25.0, 50.0, 75.0), ("低", "中", "高", "极高"))

    return (
        "等级:" + grade
        + ";期限:" + term
        + ";用途:" + purpose
        + ";负债收入比:" + dti
        + ";年收入:" + inc
        + ";征信查询:" + inq
        + ";循环额度使用率:" + util
    )


def value_text(canon: str, outcome: str | None, emp_title_norm: str = "") -> str:
    """Slot value_text: canonical text + outcome statement + emp annotation.

    emp_title_norm 只作附注(不参与检索 key),为后续文本记忆留口。
    """
    parts = [canon]
    if outcome is not None:
        parts.append(OUTCOME_ZH[outcome])
    if emp_title_norm:
        parts.append(f"雇主:{emp_title_norm}")
    return ";".join(parts)
