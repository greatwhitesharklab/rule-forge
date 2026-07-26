"""LendingClub prepare pipeline tests — small constructed CSV fixtures only.

Covers: whitelist/blacklist disjointness, outcome mapping (all branches incl.
credit-policy variants and censored exclusion), episode parsing & --since,
per-episode cap reproducibility, report count consistency, credit-history
months, emp_title normalization. Never reads the 1.6GB real file (see
test_lending_real_data.py for the opt-in smoke test).
"""

import json
from pathlib import Path

import pandas as pd
import pytest

from lending import prepare as lp


FEATURES = list(lp.FEATURE_COLS)


def _row(issue_d, loan_status, **over):
    row = {c: None for c in FEATURES + [lp.LABEL_COL]}
    row.update(
        issue_d=issue_d,
        loan_status=loan_status,
        loan_amnt=10000.0,
        term=" 36 months",
        int_rate=12.5,
        installment=334.0,
        grade="B",
        sub_grade="B3",
        emp_title="Engineer",
        emp_length="3 years",
        home_ownership="RENT",
        annual_inc=60000.0,
        verification_status="Verified",
        purpose="debt_consolidation",
        addr_state="CA",
        dti=15.0,
        delinq_2yrs=0.0,
        earliest_cr_line="Jan-2000",
        inq_last_6mths=0.0,
        open_acc=5.0,
        pub_rec=0.0,
        revol_bal=3000.0,
        revol_util=40.0,
        total_acc=10.0,
        application_type="Individual",
    )
    row.update(over)
    return row


@pytest.fixture()
def raw_csv(tmp_path: Path) -> Path:
    """~30-row CSV covering every branch + blacklist columns present."""
    rows = [
        _row("Dec-2015", "Fully Paid"),
        _row("Dec-2015", "Charged Off"),
        _row("Dec-2015", "Late (31-120 days)"),
        _row("Dec-2015", "Current"),
        _row("Dec-2015", "In Grace Period"),
        _row("Dec-2015", "Late (16-30 days)"),
        _row("Dec-2015", "Default"),
        _row("Dec-2015", "Does not meet the credit policy. Status:Fully Paid"),
        _row("Dec-2015", "Does not meet the credit policy. Status:Charged Off"),
        _row(None, "Fully Paid"),                # 空 issue_d → 剔除
        _row("not-a-date", "Fully Paid"),        # 坏 issue_d → 剔除
        _row("Jan-2007", "Fully Paid"),          # since 之前 → 剔除
        _row("Mar-2016", "Charged Off", emp_title=None),  # 缺失 emp_title
        _row("Mar-2016", "Fully Paid", emp_title="  ABC   DEF  "),
        _row("Mar-2016", "Fully Paid", emp_length="< 1 year",
             earliest_cr_line="Mar-2014"),       # 信用历史恰好 24 个月
    ]
    df = pd.DataFrame(rows)
    # 黑名单(贷后泄漏)列存在于原始 CSV,必须不进输出
    df["total_pymnt"] = 123.0
    df["recoveries"] = 0.0
    df["funded_amnt"] = 10000.0
    path = tmp_path / "raw.csv"
    df.to_csv(path, index=False)
    return path


def _run(raw_csv, tmp_path, **kw):
    return lp.prepare(input_path=raw_csv, out_dir=tmp_path / "out", **kw)


def test_whitelist_blacklist_disjoint():
    assert not (set(lp.FEATURE_COLS) & set(lp.BLACKLIST_COLS))
    # 黑名单必须覆盖经典贷后泄漏字段
    for col in ("total_pymnt", "recoveries", "total_rec_late_fee",
                "last_pymnt_d", "funded_amnt"):
        assert col in lp.BLACKLIST_COLS


def test_outcome_mapping_all_branches(raw_csv, tmp_path):
    report = _run(raw_csv, tmp_path, since=None, per_episode_cap=None)
    df = pd.read_parquet(tmp_path / "out" / lp.EPISODES_PARQUET)
    # good: Fully Paid + policy Fully Paid + Jan-2007 + Mar-2016 ×2 = 5
    # bad: Charged Off + Late31-120 + policy Charged Off + Mar-2016 = 4
    # censored: Current, In Grace, Late16-30, Default = 4
    dec = df[df.episode == "2015-12"]
    assert sorted(dec.outcome.tolist()) == [0, 0, 1, 1, 1]
    oc = report["outcome_counts_full"]
    assert oc == {"good": 5, "bad": 4, "censored": 4}
    # 未成熟默认剔除
    assert len(df) == report["rows_after_cap"] == 9


def test_censored_export(raw_csv, tmp_path):
    report = _run(raw_csv, tmp_path, since=None, per_episode_cap=None,
                  export_censored=True)
    cens = pd.read_parquet(tmp_path / "out" / lp.CENSORED_PARQUET)
    assert len(cens) == report["censored_rows"] == 4
    assert set(cens.maturity_status) == {
        "Current", "In Grace Period", "Late (16-30 days)", "Default"}
    assert "outcome" not in cens.columns


def test_episode_parsing_and_since_filter(raw_csv, tmp_path):
    report = _run(raw_csv, tmp_path, since="2016-01", per_episode_cap=None)
    df = pd.read_parquet(tmp_path / "out" / lp.EPISODES_PARQUET)
    assert set(df.episode) == {"2016-03"}
    assert report["dropped_null_issue_d"] == 1
    assert report["dropped_bad_issue_d"] == 1
    # Jan-2007 行在 since 过滤中被剔除(不进入 episode 统计)
    assert report["rows_after_since_filter"] == 3


def test_per_episode_cap_and_seed_reproducibility(tmp_path):
    rows = [_row("Jun-2016", "Fully Paid" if i % 2 else "Charged Off")
            for i in range(50)]
    path = tmp_path / "big.csv"
    pd.DataFrame(rows).to_csv(path, index=False)
    r1 = lp.prepare(path, tmp_path / "o1", since=None, per_episode_cap=10, seed=7)
    r2 = lp.prepare(path, tmp_path / "o2", since=None, per_episode_cap=10, seed=7)
    df1 = pd.read_parquet(tmp_path / "o1" / lp.EPISODES_PARQUET)
    df2 = pd.read_parquet(tmp_path / "o2" / lp.EPISODES_PARQUET)
    assert r1["rows_after_cap"] == 10
    pd.testing.assert_frame_equal(df1, df2)  # 同种子可复现


def test_report_counts_consistent(raw_csv, tmp_path):
    report = _run(raw_csv, tmp_path, since=None, per_episode_cap=None)
    oc = report["outcome_counts_full"]
    assert (report["rows_total"] - report["dropped_null_issue_d"]
            - report["dropped_bad_issue_d"]
            == oc["good"] + oc["bad"] + oc["censored"])
    assert report["rows_after_outcome_filter"] == oc["good"] + oc["bad"]
    n_by_ep = sum(e["n"] for e in report["episode_stats"].values())
    assert n_by_ep == report["rows_after_cap"]
    # report JSON 落盘且自洽
    on_disk = json.loads((tmp_path / "out" / lp.REPORT_JSON).read_text())
    assert on_disk["episodes"] == report["episodes"]
    assert on_disk["bad_rate_overall"] == report["bad_rate_overall"]


def test_credit_history_months(raw_csv, tmp_path):
    _run(raw_csv, tmp_path, since=None, per_episode_cap=None)
    df = pd.read_parquet(tmp_path / "out" / lp.EPISODES_PARQUET)
    row = df[(df.episode == "2016-03") & (df.emp_length == "< 1 year")].iloc[0]
    assert row.credit_history_months == 24      # Mar-2014 → Mar-2016
    assert row.emp_length_years == 0            # "< 1 year" → 0
    dec = df[df.episode == "2015-12"].iloc[0]
    assert dec.credit_history_months == (2015 - 2000) * 12 + (12 - 1)


def test_emp_title_norm(raw_csv, tmp_path):
    _run(raw_csv, tmp_path, since=None, per_episode_cap=None)
    df = pd.read_parquet(tmp_path / "out" / lp.EPISODES_PARQUET)
    mar = df[df.episode == "2016-03"]
    norms = set(mar.emp_title_norm)
    assert "" in norms            # 缺失 → 空串占位
    assert "abc def" in norms     # 小写 + strip + 空白折叠
    assert "engineer" in set(df.emp_title_norm)


def test_blacklist_columns_dropped(raw_csv, tmp_path):
    report = _run(raw_csv, tmp_path, since=None, per_episode_cap=None)
    df = pd.read_parquet(tmp_path / "out" / lp.EPISODES_PARQUET)
    for col in ("total_pymnt", "recoveries", "funded_amnt"):
        assert col not in df.columns
    # 白名单特征 + 标签 + 派生列都在
    for col in lp.FEATURE_COLS:
        assert col in df.columns
    for col in ("episode", "outcome", "emp_title_norm",
                "credit_history_months", "term_months", "emp_length_years"):
        assert col in report["output_cols"]
