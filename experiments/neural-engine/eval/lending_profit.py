"""真实损益评估:total_pymnt join + approve 率对齐。

total_pymnt 是贷后字段(prepare.py 黑名单成员),本模块是唯一允许触碰它
的地方,且只用于利润评估,产出绝不进入任何特征/记忆路径。

join 方法:prepare.py 的流水线(issue_d 解析 -> 结局映射 -> since 过滤 ->
per-episode 采样 cap -> episode 稳定排序)在给定参数下完全确定,因此用同一
组参数重读原始 CSV 的 4 列即可复现逐行对齐;加载后用行数、逐 episode 计数、
loan_amnt 全等三重断言钉死对齐,任一不符直接 raise。
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

from lending.prepare import (
    DEFAULT_GOOD,
    DEFAULT_BAD,
    DEFAULT_INPUT,
    map_outcome,
)

_REPO_ROOT = Path(__file__).resolve().parents[3]


def profit_per_case(
    approve: np.ndarray,
    outcome: np.ndarray,
    loan_amnt: np.ndarray,
    total_pymnt: np.ndarray,
) -> np.ndarray:
    """每笔利润:approve&good -> pymnt-amnt;approve&bad -> -(amnt-pymnt);
    reject -> 0。口径与实验简报一致(两种 approve 情形代数上同为
    pymnt-amnt,但按业务口径显式分支,便于审计)。"""
    approve = np.asarray(approve, dtype=bool)
    outcome = np.asarray(outcome)
    amnt = np.asarray(loan_amnt, dtype=np.float64)
    pymnt = np.asarray(total_pymnt, dtype=np.float64)
    profit = np.zeros(len(approve), dtype=np.float64)
    good = approve & (outcome == 0)
    bad = approve & (outcome == 1)
    profit[good] = pymnt[good] - amnt[good]
    profit[bad] = -(amnt[bad] - pymnt[bad])
    return profit


def approve_mask(proba: np.ndarray, n_approve: int) -> np.ndarray:
    """取 P(bad) 最低的 n_approve 笔为 approve;并列按行序稳定裁决。"""
    proba = np.asarray(proba, dtype=np.float64)
    n_approve = max(0, min(int(n_approve), len(proba)))
    order = np.argsort(proba, kind="stable")
    mask = np.zeros(len(proba), dtype=bool)
    mask[order[:n_approve]] = True
    return mask


def load_profit_fields(
    parquet_df: pd.DataFrame,
    input_path: Path | str = DEFAULT_INPUT,
    since: str | None = "2008-01",
    per_episode_cap: int | None = 20000,
    seed: int = 42,
) -> pd.Series:
    """按 prepare 同款确定流水线重读原始 CSV,返回与 parquet_df 逐行对齐的
    total_pymnt。参数必须与产出 parquet 时用的完全一致(默认值即如此)。
    """
    df = pd.read_csv(
        input_path,
        usecols=["issue_d", "loan_status", "loan_amnt", "total_pymnt"],
        low_memory=False,
    )
    issue = pd.to_datetime(df["issue_d"], format="%b-%Y", errors="coerce")
    df = df.loc[issue.notna()].copy()
    df["episode"] = issue.loc[issue.notna()].dt.strftime("%Y-%m")
    outcome = map_outcome(df["loan_status"], good=DEFAULT_GOOD, bad=DEFAULT_BAD)
    df = df.loc[outcome.notna()].copy()
    if since is not None:
        df = df.loc[df["episode"] >= since].copy()
    if per_episode_cap is not None:
        counts = df.groupby("episode", observed=True).size()
        big = counts[counts > per_episode_cap].index
        if len(big):
            parts = [df[~df["episode"].isin(big)]]
            parts.append(
                df[df["episode"].isin(big)]
                .groupby("episode", observed=True)
                .sample(n=per_episode_cap, random_state=seed)
            )
            df = pd.concat(parts)
    df = df.sort_values(["episode"], kind="stable").reset_index(drop=True)
    df["loan_amnt"] = pd.to_numeric(df["loan_amnt"], errors="coerce")
    df["total_pymnt"] = pd.to_numeric(df["total_pymnt"], errors="coerce")

    # 三重对齐断言:行数 / 逐 episode 计数 / loan_amnt 全等。
    assert len(df) == len(parquet_df), (
        f"行数不一致: 重读 {len(df)} vs parquet {len(parquet_df)}"
    )
    cnt_new = df["episode"].value_counts().sort_index()
    cnt_ref = parquet_df["episode"].value_counts().sort_index()
    assert cnt_new.equals(cnt_ref), "逐 episode 计数不一致,采样未复现"
    assert np.allclose(
        df["loan_amnt"].to_numpy(dtype=float),
        parquet_df["loan_amnt"].to_numpy(dtype=float),
        equal_nan=True,
    ), "loan_amnt 不对齐,join 不可信"
    return df["total_pymnt"]
