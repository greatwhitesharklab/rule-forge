"""GBDT 指路(设计文档 §8.5 ①):训练/打分、解释不了的坏账、画像聚合、重要性。

指路的输出只有两类聚合信息:top-k 疑难坏账的**画像统计**(均值/分布,
绝不含逐行案例)与特征重要性 top 列表 —— 这是出题 context 的原料。
"""

from __future__ import annotations

from collections import Counter
from typing import Sequence

import lightgbm as lgb
import numpy as np
import pandas as pd
from scipy.stats import ks_2samp


def train_gbdt(
    X: np.ndarray,
    y: np.ndarray,
    *,
    params: dict[str, object],
    seed: int,
) -> lgb.LGBMClassifier:
    """同参同种子训练;random_state 由 seed 注入,调用方不重复设置。"""
    model = lgb.LGBMClassifier(**{**params, "random_state": seed})
    model.fit(X, y)
    return model


def predict_bad_proba(model: lgb.LGBMClassifier, X: np.ndarray) -> np.ndarray:
    """P(bad) 概率向量(outcome=1 为 bad)。"""
    return model.predict_proba(X)[:, 1]


def unexplained_bads(y: np.ndarray, proba: np.ndarray, top_k: int) -> np.ndarray:
    """"解释不了的坏账":outcome=1 但 P(bad) 最低的行索引,稳定升序,top_k 截断。"""
    if top_k <= 0:
        return np.array([], dtype=np.int64)
    y = np.asarray(y)
    proba = np.asarray(proba, dtype=np.float64)
    bad_idx = np.nonzero(y == 1)[0]
    order = bad_idx[np.argsort(proba[bad_idx], kind="stable")]
    return order[:top_k]


def profile_unexplained(
    df: pd.DataFrame,
    idx: np.ndarray,
    *,
    categorical_cols: tuple[str, ...] = (
        "grade", "purpose", "home_ownership", "verification_status",
    ),
    top_values: int = 3,
) -> dict:
    """疑难坏账画像:纯聚合统计(数值均值 vs dev 对照 + 类别 top 占比)。

    返回 dict 全部 JSON 可序列化;不含任何逐行数据(PII 红线,§2.2)。
    """
    n_dev = len(df)
    if len(idx) == 0:
        return {"n": 0, "share_of_dev": 0.0}
    sub = df.iloc[idx]
    num = df.select_dtypes("number").drop(columns=["outcome"], errors="ignore")
    numeric_means = {
        c: round(float(sub[c].mean()), 4) for c in num.columns
    }
    numeric_means_dev = {
        c: round(float(num[c].mean()), 4) for c in num.columns
    }
    top: dict[str, list[list]] = {}
    for c in categorical_cols:
        if c not in df.columns:
            continue
        shares = sub[c].fillna("缺失").astype(str).value_counts(normalize=True)
        top[c] = [[str(v), round(float(s), 4)] for v, s in shares.head(top_values).items()]
    return {
        "n": int(len(idx)),
        "share_of_dev": round(len(idx) / max(n_dev, 1), 6),
        "numeric_means": numeric_means,
        "numeric_means_dev": numeric_means_dev,
        "top_values": top,
    }


def regime_stats(df: pd.DataFrame) -> list[dict]:
    """dev 窗按年分段的 bad_rate(regime 段统计,出题 context 用)。"""
    year = df["episode"].astype(str).str[:4]
    grouped = df.groupby(year)["outcome"].agg(n="size", bad_rate="mean")
    return [
        {"year": str(y), "n": int(r.n), "bad_rate": round(float(r.bad_rate), 4)}
        for y, r in grouped.iterrows()
    ]


def importance_top(
    model: lgb.LGBMClassifier,
    feature_names: list[str] | tuple[str, ...],
    n: int,
) -> list[dict]:
    """特征重要性 top-n,降序;0 重要性特征垫底但保留(审计完整性)。"""
    imp = np.asarray(model.feature_importances_, dtype=np.float64)
    order = np.argsort(-imp, kind="stable")
    return [
        {"feature": str(feature_names[i]), "importance": float(imp[i])}
        for i in order[:n]
    ]


# ---------------------------------------------------------------------------
# Residual-signal指路(§8.5 ①增强):同 proba 箱内对照,暴露 GBDT 未利用的信号。
# "漏网坏账 vs 全 dev"的均值差混着 GBDT 已学到的信息(如 int_rate),据此提
# 特征必然冗余;正确对照是同 proba 箱内的 good —— 箱内差异 ≈ 残余判别信号。
# ---------------------------------------------------------------------------

_DEFAULT_CAT_COLS = (
    "grade", "sub_grade", "purpose", "home_ownership",
    "verification_status", "addr_state", "term",
)


def _dist_stats(v: np.ndarray) -> dict[str, float]:
    """Distribution summary over finite values only (all-NaN -> NaN stats)."""
    v = np.asarray(v, dtype=np.float64)
    v = v[np.isfinite(v)]
    if v.size == 0:
        nan = float("nan")
        return {"mean": nan, "median": nan, "p25": nan, "p75": nan}
    return {
        "mean": round(float(v.mean()), 4),
        "median": round(float(np.median(v)), 4),
        "p25": round(float(np.percentile(v, 25)), 4),
        "p75": round(float(np.percentile(v, 75)), 4),
    }


def _token_gaps(
    s: pd.Series | None,
    missed: np.ndarray,
    controls: np.ndarray,
    top_n: int,
    min_count: int,
) -> list[dict]:
    """Token frequency gaps (missed - control) over a normalized text column."""
    if s is None:
        return []

    def counts(rows: np.ndarray) -> Counter:
        c: Counter = Counter()
        for text in s.iloc[rows]:
            for tok in str(text).split():
                if len(tok) >= 3:  # drop "of", "at", single letters
                    c[tok] += 1
        return c

    m_cnt = counts(missed)
    c_cnt = counts(controls)
    entries = []
    for tok, mc in m_cnt.items():
        if mc < min_count:
            continue
        m_freq = mc / max(len(missed), 1)
        c_freq = c_cnt.get(tok, 0) / max(len(controls), 1)
        entries.append({
            "token": tok,
            "missed_freq": round(m_freq, 4),
            "control_freq": round(c_freq, 4),
            "diff": round(m_freq - c_freq, 4),
        })
    entries.sort(key=lambda e: -e["diff"])
    return entries[:top_n]


def residual_signal_analysis(
    df: pd.DataFrame,
    labels: np.ndarray,
    proba: np.ndarray,
    top_k: int,
    *,
    numeric_cols: Sequence[str] | None = None,
    categorical_cols: Sequence[str] = _DEFAULT_CAT_COLS,
    text_col: str = "emp_title_norm",
    n_bins: int = 10,
    top_numeric: int = 8,
    top_categorical: int = 5,
    top_tokens: int = 10,
    min_token_count: int = 2,
) -> dict:
    """Residual signal map: missed bads vs goods in the SAME proba bin.

    ``df``/``labels``/``proba`` must be the holdout slice (out-of-sample;
    training rows are explained by construction). Controls = good rows whose
    proba bin contains at least one missed bad. Effect size for numerics is
    Cohen's d against the FULL-frame std (bin-local mean gaps in a learned
    field are tiny relative to its overall spread; residual drivers are not),
    with two-sample KS reported alongside. Output is aggregate-only and JSON
    serializable (PII red line).
    """
    labels = np.asarray(labels)
    proba = np.asarray(proba, dtype=np.float64)
    missed = unexplained_bads(labels, proba, top_k)
    out: dict = {"n_missed": int(missed.size)}
    if missed.size == 0:
        out.update(n_controls=0, numeric=[], categorical=[], emp_title_tokens=[])
        return out

    if np.unique(proba).size < 2:
        bins = np.zeros(len(proba), dtype=np.int64)  # constant proba: one bin
    else:
        bins = (
            pd.qcut(pd.Series(proba), n_bins, labels=False, duplicates="drop")
            .to_numpy()
            .astype(np.int64)
        )
    missed_bins = np.unique(bins[missed])
    controls = np.nonzero((labels == 0) & np.isin(bins, missed_bins))[0]
    out["n_controls"] = int(controls.size)
    if controls.size == 0:
        out.update(numeric=[], categorical=[], emp_title_tokens=[])
        return out

    # ---- numeric: distribution stats + KS + Cohen's d (sorted by |d|) ----
    num_cols = (
        list(numeric_cols) if numeric_cols is not None
        else list(df.select_dtypes("number").columns)
    )
    numeric: list[dict] = []
    for c in num_cols:
        v = pd.to_numeric(df[c], errors="coerce").to_numpy(dtype=np.float64)
        a = v[missed]
        b = v[controls]
        a_f = a[np.isfinite(a)]
        b_f = b[np.isfinite(b)]
        if a_f.size < 2 or b_f.size < 2:
            continue
        overall_std = float(np.nanstd(v))
        d = 0.0 if overall_std <= 0 else float((a_f.mean() - b_f.mean()) / overall_std)
        ks = ks_2samp(a_f, b_f)
        numeric.append({
            "feature": c,
            "cohens_d": round(d, 4),
            "ks": round(float(ks.statistic), 4),
            "p_value": float(ks.pvalue),
            "direction": "missed_higher" if d > 0 else "missed_lower",
            "missed": _dist_stats(a),
            "control": _dist_stats(b),
        })
    numeric.sort(key=lambda e: -abs(e["cohens_d"]))
    out["numeric"] = numeric[:top_numeric]

    # ---- categorical: missed-share minus control-share per value ----
    cat_entries: list[dict] = []
    for c in categorical_cols:
        if c not in df.columns:
            continue
        s = df[c].fillna("缺失").astype(str)
        m_share = s.iloc[missed].value_counts(normalize=True)
        c_share = s.iloc[controls].value_counts(normalize=True)
        for val, ms in m_share.items():
            cs = float(c_share.get(val, 0.0))
            cat_entries.append({
                "column": c,
                "value": str(val),
                "missed_share": round(float(ms), 4),
                "control_share": round(cs, 4),
                "diff": round(float(ms) - cs, 4),
            })
    cat_entries.sort(key=lambda e: -abs(e["diff"]))
    out["categorical"] = cat_entries[:top_categorical]

    # ---- text: token frequency gaps (direction hints for text features) ----
    out["emp_title_tokens"] = _token_gaps(
        df[text_col] if text_col in df.columns else None,
        missed, controls, top_tokens, min_token_count,
    )
    return out
