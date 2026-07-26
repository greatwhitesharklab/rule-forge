"""LendingClub accepted-loan preprocessing pipeline.

Turns the raw Kaggle CSV (accepted_2007_to_2018Q4.csv, ~2.26M rows, 151 cols)
into a time-redline-safe, episode-ready parquet:

- 时间红线 / as-of (设计文档 §3.3, §8.3 铁律二): only fields available at the
  loan-issuance decision moment are kept as features; all post-origination
  (贷后) fields are blacklisted and never read into the output.
- 线上线下一致性 (§8.3 铁律一): all feature derivations (credit-history months,
  emp_title normalization, ...) live here in exactly one implementation.

Episode definition mirrors the synth CLAB-lite world: episode = issue month
(YYYY-MM); outcome is the delayed label (1 = bad). Loans whose final state is
not yet known (Current / In Grace / short delinquency) are immature and
excluded by default, optionally exported as a censored set.

CLI:
    cd experiments/neural-engine && uv run python -m lending.prepare
"""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import pandas as pd

# ---------------------------------------------------------------------------
# Paths (derived from this file, never hardcoded absolute)
# ---------------------------------------------------------------------------
_REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_INPUT = _REPO_ROOT / "data" / "lendingclub" / "accepted_2007_to_2018Q4.csv"
DEFAULT_OUT_DIR = _REPO_ROOT / "data" / "lendingclub"
EPISODES_PARQUET = "lendingclub_episodes.parquet"
CENSORED_PARQUET = "lendingclub_censored.parquet"
REPORT_JSON = "prepare_report.json"

LABEL_COL = "loan_status"

# ---------------------------------------------------------------------------
# 字段白名单 (§8.3 铁律一落地): 只保留放款决策时刻可得字段。
# 每列注释说明其决策时刻可得性。
# ---------------------------------------------------------------------------
FEATURE_COLS: dict[str, str] = {
    "loan_amnt": "申请贷款金额 — 申请时填写",
    "term": "贷款期限(36/60 months) — 合同签订时确定",
    "int_rate": "贷款利率 — 放款定价决策结果,决策时刻已知",
    "installment": "月供 — 由金额/利率/期限在放款时算出",
    "grade": "LC 评级 — 放款前模型给出",
    "sub_grade": "LC 子评级 — 同上",
    "emp_title": "雇主名称(自由文本) — 申请时填写",
    "emp_length": "工龄 — 申请时填写",
    "home_ownership": "住房状态(RENT/MORTGAGE/OWN) — 申请时填写",
    "annual_inc": "自报年收入 — 申请时填写",
    "verification_status": "收入核验状态 — 放款前完成核验",
    "issue_d": "放款月 — 仅用于 episode 切分与时间红线,不作特征",
    "purpose": "借款用途 — 申请时填写",
    "addr_state": "州 — 申请时填写",
    "dti": "债务收入比 — 放款前由征信+自报算出",
    "delinq_2yrs": "近2年逾期次数 — 放款前征信记录",
    "earliest_cr_line": "最早征信开户月 — 放款前征信记录(转为信用历史月数)",
    "inq_last_6mths": "近6月征信查询次数 — 放款前征信记录",
    "open_acc": "当前开户数 — 放款前征信记录",
    "pub_rec": "公开不良记录数 — 放款前征信记录",
    "revol_bal": "循环信贷余额 — 放款前征信记录",
    "revol_util": "循环额度使用率 — 放款前征信记录",
    "total_acc": "累计开户数 — 放款前征信记录",
    "application_type": "个人/联合申请 — 申请时确定",
}

# 显式黑名单: 贷后字段 / 决策时刻不可得字段 — 读了就是特征穿越(泄漏)。
# 断言白名单∩黑名单=∅,防止误加。
BLACKLIST_COLS: dict[str, str] = {
    "funded_amnt": "实际放款金额 — 审批后才确定",
    "funded_amnt_inv": "投资者认购金额 — 放款后才知道",
    "total_pymnt": "累计已还金额 — 贷后",
    "total_pymnt_inv": "投资者累计回款 — 贷后",
    "total_rec_prncp": "已回收本金 — 贷后",
    "total_rec_int": "已回收利息 — 贷后",
    "total_rec_late_fee": "已收滞纳金 — 贷后(泄漏逾期)",
    "recoveries": "坏账回收 — 贷后(直接泄漏坏账)",
    "collection_recovery_fee": "催收费用 — 贷后",
    "last_pymnt_d": "最近还款日 — 贷后",
    "last_pymnt_amnt": "最近还款金额 — 贷后",
    "next_pymnt_d": "下一还款日 — 贷后",
    "last_credit_pull_d": "最近征信拉取日 — 贷后",
    "last_fico_range_high": "最近 FICO — 贷后更新值",
    "last_fico_range_low": "最近 FICO — 贷后更新值",
    "out_prncp": "剩余本金 — 贷后",
    "out_prncp_inv": "投资者剩余本金 — 贷后",
    "hardship_flag": "困难计划标记 — 贷后事件",
    "hardship_type": "困难计划类型 — 贷后",
    "deferral_term": "延期条款 — 贷后",
    "hardship_status": "困难计划状态 — 贷后",
    "settlement_status": "和解状态 — 贷后",
    "debt_settlement_flag": "债务和解标记 — 贷后",
}

assert not (set(FEATURE_COLS) & set(BLACKLIST_COLS)), (
    "白名单与黑名单相交: " + str(set(FEATURE_COLS) & set(BLACKLIST_COLS))
)

# Numeric columns coerced after read (junk rows become NaN).
NUMERIC_COLS = [
    "loan_amnt", "int_rate", "installment", "annual_inc", "dti",
    "delinq_2yrs", "inq_last_6mths", "open_acc", "pub_rec",
    "revol_bal", "revol_util", "total_acc",
]

# ---------------------------------------------------------------------------
# 结局映射 (可配): 默认 good={Fully Paid}, bad={Charged Off, Late (31-120 days)}
# "Does not meet the credit policy. Status:X" 归并到 X。
# 其余(Current/In Grace/Late 16-30/Default/缺失) → 未成熟,默认剔除。
# 注: Late (31-120 days) 虽技术上仍存续,但已属严重逾期,CLAB-lite 语义即 bad;
# "Default" 状态全表仅 40 行且语义含混,归入未成熟。
# ---------------------------------------------------------------------------
POLICY_PREFIX = "Does not meet the credit policy. Status:"
DEFAULT_GOOD = ("Fully Paid",)
DEFAULT_BAD = ("Charged Off", "Late (31-120 days)")


def normalize_status(status: pd.Series) -> pd.Series:
    """Strip the credit-policy prefix so policy variants merge into X."""
    return status.str.replace(POLICY_PREFIX, "", regex=False)


def map_outcome(status: pd.Series, good=DEFAULT_GOOD, bad=DEFAULT_BAD) -> pd.Series:
    """outcome: 1 = bad, 0 = good, NA = immature (censored)."""
    s = normalize_status(status)
    out = pd.Series(pd.NA, index=status.index, dtype="Int64")
    out[s.isin(good)] = 0
    out[s.isin(bad)] = 1
    return out


def normalize_emp_title(s: pd.Series) -> pd.Series:
    """lowercase + strip + collapse whitespace; missing -> '' (占位标记)."""
    return (
        s.fillna("")
        .str.lower()
        .str.strip()
        .str.replace(r"\s+", " ", regex=True)
    )


def _months_between(issue: pd.Series, start: pd.Series) -> pd.Series:
    """Whole months from `start` to `issue` (both datetime64, month resolution)."""
    return (issue.dt.year - start.dt.year) * 12 + (issue.dt.month - start.dt.month)


def prepare(
    input_path: Path | str = DEFAULT_INPUT,
    out_dir: Path | str = DEFAULT_OUT_DIR,
    since: str | None = "2008-01",
    per_episode_cap: int | None = 20000,
    seed: int = 42,
    export_censored: bool = False,
    good=DEFAULT_GOOD,
    bad=DEFAULT_BAD,
) -> dict:
    """Run the pipeline; write parquet(s) + report; return the report dict.

    since: 默认 2008-01。2007 为部分年(仅 603 行)且制度差异大,剔除;
    保留 2008-2009 危机期样本,其 bad_rate 抬升本身就是时间漂移证据。
    """
    t0 = time.time()
    input_path = Path(input_path)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    usecols = list(FEATURE_COLS) + [LABEL_COL]
    # 黑名单列即使存在于原始 CSV 也绝不读取(usecols 即白名单)。
    df = pd.read_csv(input_path, usecols=usecols, low_memory=False)
    report: dict = {
        "input_path": str(input_path),
        "rows_total": int(len(df)),
        "since": since,
        "per_episode_cap": per_episode_cap,
        "seed": seed,
        "good_statuses": list(good),
        "bad_statuses": list(bad),
        "feature_cols": list(FEATURE_COLS),
        "label_col": LABEL_COL,
    }

    # --- 1. issue_d 解析 (%b-%Y;早期有空值/整行垃圾行) ---
    issue = pd.to_datetime(df["issue_d"], format="%b-%Y", errors="coerce")
    bad_issue = issue.isna() & df["issue_d"].notna()
    null_issue = issue.isna() & df["issue_d"].isna()
    report["dropped_null_issue_d"] = int(null_issue.sum())
    report["dropped_bad_issue_d"] = int(bad_issue.sum())
    df = df.loc[issue.notna()].copy()
    issue = issue.loc[issue.notna()]
    df["issue_d_parsed"] = issue
    df["episode"] = issue.dt.strftime("%Y-%m")

    # --- 2. 结局映射 ---
    outcome = map_outcome(df[LABEL_COL], good=good, bad=bad)
    matured = outcome.notna()
    report["outcome_counts_full"] = {
        "good": int((outcome == 0).sum()),
        "bad": int((outcome == 1).sum()),
        "censored": int((~matured).sum()),
    }
    censored_df = None
    if export_censored and (~matured).any():
        censored_df = df.loc[~matured].copy()
        censored_df["maturity_status"] = normalize_status(
            censored_df[LABEL_COL]
        ).fillna("MISSING")
    df = df.loc[matured].copy()
    df["outcome"] = outcome.loc[matured].astype("int8")
    report["rows_after_outcome_filter"] = int(len(df))

    # --- 3. since 过滤 ---
    if since is not None:
        df = df.loc[df["episode"] >= since].copy()
    report["rows_after_since_filter"] = int(len(df))

    # --- 4. 特征派生(统一实现,§8.3 铁律一) ---
    ecl = pd.to_datetime(df["earliest_cr_line"], format="%b-%Y", errors="coerce")
    df["credit_history_months"] = _months_between(df["issue_d_parsed"], ecl)
    # 防御: 异常负值(征信记录错误)记 NaN,不臆造。
    df.loc[df["credit_history_months"] < 0, "credit_history_months"] = pd.NA
    df["credit_history_months"] = df["credit_history_months"].astype("Int64")
    df["emp_title_norm"] = normalize_emp_title(df["emp_title"])
    df["term_months"] = (
        df["term"].str.extract(r"(\d+)", expand=False).astype("Int64")
    )
    el = df["emp_length"].str.extract(r"(\d+)", expand=False)
    emp_years = pd.to_numeric(el, errors="coerce")
    # "< 1 year" 提取不到数字 → 0 年。
    emp_years[df["emp_length"].str.startswith("<", na=False)] = 0
    df["emp_length_years"] = emp_years.astype("Int64")

    for col in NUMERIC_COLS:
        df[col] = pd.to_numeric(df[col], errors="coerce")

    # --- 5. per-episode cap(固定种子随机采样) ---
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
    report["rows_after_cap"] = int(len(df))

    # --- 6. 输出 ---
    df = df.sort_values(["episode"], kind="stable").reset_index(drop=True)
    df = df.drop(columns=["issue_d_parsed"])
    out_path = out_dir / EPISODES_PARQUET
    df.to_parquet(out_path, index=False)
    report["episodes_parquet"] = str(out_path)

    if censored_df is not None:
        censored_df = censored_df.drop(columns=["issue_d_parsed"])
        cens_path = out_dir / CENSORED_PARQUET
        censored_df.to_parquet(cens_path, index=False)
        report["censored_parquet"] = str(cens_path)
        report["censored_rows"] = int(len(censored_df))

    ep_stats = (
        df.groupby("episode", observed=True)["outcome"]
        .agg(n="size", bad_rate="mean")
        .round(4)
    )
    report["episodes"] = int(ep_stats.shape[0])
    report["episode_stats"] = {
        ep: {"n": int(r.n), "bad_rate": float(r.bad_rate)}
        for ep, r in ep_stats.iterrows()
    }
    report["bad_rate_overall"] = round(float(df["outcome"].mean()), 4)
    report["bad_rate_min"] = round(float(ep_stats["bad_rate"].min()), 4)
    report["bad_rate_max"] = round(float(ep_stats["bad_rate"].max()), 4)
    report["output_cols"] = list(df.columns)
    report["elapsed_sec"] = round(time.time() - t0, 1)

    with open(out_dir / REPORT_JSON, "w") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    return report


def main(argv: list[str] | None = None) -> dict:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--input", default=str(DEFAULT_INPUT))
    ap.add_argument("--out-dir", default=str(DEFAULT_OUT_DIR))
    ap.add_argument("--since", default="2008-01",
                    help="保留 episode >= since(YYYY-MM);'none' 关闭")
    ap.add_argument("--per-episode-cap", type=int, default=20000,
                    help="每 episode 随机采样上限;0 = 不采样")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--export-censored", action="store_true",
                    help="未成熟(Current 等)样本导出为 censored parquet")
    args = ap.parse_args(argv)

    report = prepare(
        input_path=args.input,
        out_dir=args.out_dir,
        since=None if args.since.lower() == "none" else args.since,
        per_episode_cap=args.per_episode_cap or None,
        seed=args.seed,
        export_censored=args.export_censored,
    )

    # --- 终端摘要 ---
    print("=== LendingClub prepare 摘要 ===")
    print(f"输入: {report['input_path']}")
    print(f"总行数:            {report['rows_total']:>9,}")
    print(f"  空/坏 issue_d:   {report['dropped_null_issue_d'] + report['dropped_bad_issue_d']:>9,}")
    oc = report["outcome_counts_full"]
    print(f"  未成熟剔除:      {oc['censored']:>9,}  (good={oc['good']:,} bad={oc['bad']:,})")
    print(f"  since={report['since']} 后: {report['rows_after_since_filter']:>9,}")
    print(f"  cap={report['per_episode_cap']} 后:  {report['rows_after_cap']:>9,}")
    print(f"episode 数: {report['episodes']}, 整体 bad_rate={report['bad_rate_overall']}, "
          f"episode bad_rate 范围 [{report['bad_rate_min']}, {report['bad_rate_max']}]")
    print(f"输出: {report['episodes_parquet']}")
    if "censored_parquet" in report:
        print(f"censored: {report['censored_parquet']} ({report['censored_rows']:,} 行)")
    print(f"耗时: {report['elapsed_sec']}s")
    return report


if __name__ == "__main__":
    main()
