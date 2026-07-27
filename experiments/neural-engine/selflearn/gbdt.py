"""GBDT 指路(设计文档 §8.5 ①):训练/打分、解释不了的坏账、画像聚合、重要性。

指路的输出只有两类聚合信息:top-k 疑难坏账的**画像统计**(均值/分布,
绝不含逐行案例)与特征重要性 top 列表 —— 这是出题 context 的原料。
"""

from __future__ import annotations

import lightgbm as lgb
import numpy as np
import pandas as pd


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
