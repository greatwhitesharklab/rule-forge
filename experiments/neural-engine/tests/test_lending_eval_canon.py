"""LendingClub canonical-text mapping tests (L3 memory retrieval keys).

The canonical text is the retrieval key for writable memory: it must be a
deterministic, banded rendering of decision-time fields (设计文档 §1.2 的
canonicalize 在 LendingClub 数据上的对应物)。emp_title_norm 刻意不进
canonical 组合(高基数爆炸),只作为 value_text 附注。
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from eval.lending_canon import canonical_series, value_text


def _df(**overrides) -> pd.DataFrame:
    base = {
        "grade": ["B"],
        "term_months": pd.array([36], dtype="Int64"),
        "purpose": ["debt_consolidation"],
        "dti": [15.0],
        "annual_inc": [60000.0],
        "inq_last_6mths": [0.0],
        "revol_util": [45.0],
    }
    base.update(overrides)
    return pd.DataFrame(base)


class TestCanonicalText:
    def test_exact_rendering(self):
        text = canonical_series(_df()).iloc[0]
        assert text == (
            "等级:B;期限:36月;用途:债务整合;负债收入比:中;"
            "年收入:中;征信查询:无;循环额度使用率:中"
        )

    def test_deterministic(self):
        df = _df()
        assert canonical_series(df).equals(canonical_series(df))

    def test_bin_boundaries(self):
        # dti edges: <=12 低, <=20 中, <=28 偏高, else 高
        assert "负债收入比:低" in canonical_series(_df(dti=[12.0])).iloc[0]
        assert "负债收入比:中" in canonical_series(_df(dti=[12.0001])).iloc[0]
        assert "负债收入比:偏高" in canonical_series(_df(dti=[28.0])).iloc[0]
        assert "负债收入比:高" in canonical_series(_df(dti=[28.5])).iloc[0]
        # annual_inc edges (upper inclusive): <=40k 低, <=70k 中, <=110k 高, else 极高
        assert "年收入:低" in canonical_series(_df(annual_inc=[39999.0])).iloc[0]
        assert "年收入:高" in canonical_series(_df(annual_inc=[70000.1])).iloc[0]
        assert "年收入:极高" in canonical_series(_df(annual_inc=[110000.1])).iloc[0]
        # inq: 0 无, 1 一次, >=2 多次
        assert "征信查询:一次" in canonical_series(_df(inq_last_6mths=[1.0])).iloc[0]
        assert "征信查询:多次" in canonical_series(_df(inq_last_6mths=[2.0])).iloc[0]
        # revol_util edges: <25 低, <50 中, <75 高, else 极高
        assert "循环额度使用率:低" in canonical_series(_df(revol_util=[24.9])).iloc[0]
        assert "循环额度使用率:高" in canonical_series(_df(revol_util=[75.0])).iloc[0]
        assert "循环额度使用率:极高" in canonical_series(_df(revol_util=[75.1])).iloc[0]

    def test_missing_values_render_as_缺失(self):
        df = _df(dti=[np.nan], purpose=[None], annual_inc=[np.nan])
        text = canonical_series(df).iloc[0]
        assert "负债收入比:缺失" in text
        assert "用途:缺失" in text
        assert "年收入:缺失" in text

    def test_term_and_grade(self):
        text = canonical_series(
            _df(term_months=pd.array([60], dtype="Int64"), grade=["G"])
        ).iloc[0]
        assert "期限:60月" in text
        assert "等级:G" in text

    def test_unknown_purpose_falls_back_to_raw_token(self):
        text = canonical_series(_df(purpose=["space_travel"])).iloc[0]
        assert "用途:space_travel" in text


class TestValueText:
    def test_outcome_and_emp_annotation(self):
        t = value_text("等级:B", "bad", "abc construction")
        assert t == "等级:B;结局:违约;雇主:abc construction"

    def test_good_outcome(self):
        assert "结局:正常还款" in value_text("x", "good", "")

    def test_no_outcome_no_emp(self):
        assert value_text("等级:B", None, "") == "等级:B"

    def test_emp_title_not_in_canonical(self):
        df = _df()
        df["emp_title_norm"] = ["secret employer llc"]
        assert "secret" not in canonical_series(df).iloc[0]
