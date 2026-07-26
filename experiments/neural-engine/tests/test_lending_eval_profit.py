"""利润口径与 approve 率对齐测试。

利润口径(与实验简报一致):approve 且 good: total_pymnt - loan_amnt;
approve 且 bad: -(loan_amnt - total_pymnt);reject: 0。total_pymnt 是贷后
字段,只允许从原始 CSV join 回来做利润评估,绝不允许进特征。
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from eval.lending_profit import approve_mask, load_profit_fields, profit_per_case


class TestProfitPerCase:
    def test_known_values(self):
        outcome = np.array([0, 1, 0, 1])
        amnt = np.array([1000.0, 1000.0, 2000.0, 2000.0])
        pymnt = np.array([1300.0, 400.0, 2100.0, 2500.0])
        approve = np.array([True, True, False, True])
        p = profit_per_case(approve, outcome, amnt, pymnt)
        # approve+good: 1300-1000=300; approve+bad: -(1000-400)=-600
        # reject: 0; approve+bad(超额回收): -(2000-2500)=+500
        assert p.tolist() == [300.0, -600.0, 0.0, 500.0]

    def test_all_reject_is_zero(self):
        p = profit_per_case(
            np.zeros(3, dtype=bool), np.array([0, 1, 0]),
            np.ones(3) * 500, np.ones(3) * 600,
        )
        assert (p == 0.0).all()


class TestApproveMask:
    def test_exact_count_and_lowest_proba(self):
        proba = np.array([0.5, 0.1, 0.9, 0.2, 0.7])
        m = approve_mask(proba, 2)
        assert m.sum() == 2
        assert m[1] and m[3]  # 最低 proba 的两个

    def test_tie_break_deterministic(self):
        proba = np.array([0.3, 0.3, 0.3, 0.9])
        m1 = approve_mask(proba, 2)
        m2 = approve_mask(proba, 2)
        assert (m1 == m2).all()
        assert m1.sum() == 2

    def test_rate_helper_matches_count(self):
        rng = np.random.default_rng(0)
        proba = rng.random(97)
        n = int(np.floor(0.7 * 97))
        m = approve_mask(proba, n)
        assert m.sum() == n


class TestLoadProfitFields:
    def _tiny_csv(self, path, n=40):
        rng = np.random.default_rng(1)
        rows = {
            "issue_d": (["Jan-2008"] * (n // 2)) + (["Feb-2008"] * (n - n // 2)),
            "loan_status": (["Fully Paid", "Charged Off"] * ((n + 1) // 2))[:n],
            "loan_amnt": 1000.0 + np.arange(n) * 100.0,
            "total_pymnt": 1200.0 + np.arange(n) * 90.0,
        }
        pd.DataFrame(rows).to_csv(path, index=False)
        ep = (["2008-01"] * (n // 2)) + (["2008-02"] * (n - n // 2))
        parquet_df = pd.DataFrame({
            "episode": ep,
            "loan_amnt": 1000.0 + np.arange(n) * 100.0,
        }).sort_values("episode", kind="stable").reset_index(drop=True)
        return parquet_df

    def test_alignment_and_values(self, tmp_path):
        csv = tmp_path / "tiny.csv"
        parquet_df = self._tiny_csv(csv)
        pymnt = load_profit_fields(
            parquet_df, input_path=csv, since=None, per_episode_cap=None,
        )
        assert len(pymnt) == len(parquet_df)
        assert pymnt.tolist() == (
            1200.0 + np.arange(len(parquet_df)) * 90.0
        ).tolist()

    def test_alignment_mismatch_raises(self, tmp_path):
        csv = tmp_path / "tiny.csv"
        parquet_df = self._tiny_csv(csv)
        broken = parquet_df.iloc[2:].reset_index(drop=True)  # 行数不一致
        with pytest.raises(AssertionError):
            load_profit_fields(broken, input_path=csv, since=None,
                               per_episode_cap=None)
