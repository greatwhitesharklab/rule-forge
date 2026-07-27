"""GBDT 指路逻辑测试:解释不了的坏账 top-k 选择、画像聚合、regime 统计、重要性。

设计文档 §8.5 ①:GBDT 指路 = 特征重要性 + "解释不了的坏账"清单。
画像必须是聚合统计(均值/分布),绝不含逐行案例(PII 红线)。
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from selflearn.gbdt import (
    importance_top,
    predict_bad_proba,
    profile_unexplained,
    regime_stats,
    train_gbdt,
    unexplained_bads,
)


class TestUnexplainedBads:
    def test_selects_lowest_proba_bads_in_stable_order(self) -> None:
        y = np.array([0, 1, 1, 0, 1, 1], dtype=np.int8)
        proba = np.array([0.9, 0.8, 0.1, 0.01, 0.05, 0.7])
        idx = unexplained_bads(y, proba, top_k=2)
        # proba 最低的两个坏账:行 4 (0.05) 与行 2 (0.1);行 3 是 good,再低也不选
        assert list(idx) == [4, 2]

    def test_top_k_larger_than_bads_returns_all_bads(self) -> None:
        y = np.array([1, 0, 1], dtype=np.int8)
        proba = np.array([0.3, 0.0, 0.2])
        idx = unexplained_bads(y, proba, top_k=10)
        assert list(idx) == [2, 0]

    def test_no_bads_returns_empty(self) -> None:
        idx = unexplained_bads(np.zeros(5, dtype=np.int8), np.zeros(5), top_k=3)
        assert len(idx) == 0

    def test_top_k_zero_returns_empty(self) -> None:
        y = np.array([1, 1], dtype=np.int8)
        assert len(unexplained_bads(y, np.array([0.1, 0.2]), top_k=0)) == 0


def _profile_df() -> pd.DataFrame:
    return pd.DataFrame({
        "episode": ["2013-01"] * 6,
        "outcome": [1, 1, 0, 0, 1, 0],
        "dti": [30.0, 28.0, 10.0, 12.0, 26.0, 8.0],
        "annual_inc": [30000.0, 32000.0, 80000.0, 90000.0, 31000.0, 100000.0],
        "grade": ["E", "F", "A", "B", "G", "A"],
        "purpose": ["small_business"] * 3 + ["car"] * 3,
    })


class TestProfileUnexplained:
    def test_aggregates_only_no_row_level_data(self) -> None:
        df = _profile_df()
        idx = np.array([0, 1, 4])  # 全部坏账行
        prof = profile_unexplained(df, idx, categorical_cols=("grade", "purpose"))
        assert prof["n"] == 3
        assert prof["share_of_dev"] == pytest.approx(0.5)
        assert prof["numeric_means"]["dti"] == pytest.approx(28.0)
        assert prof["numeric_means_dev"]["dti"] == pytest.approx(19.0)
        top_grades = dict(prof["top_values"]["grade"])
        assert top_grades["E"] == pytest.approx(1 / 3, abs=1e-4)
        # 每个标量都是聚合值:不存在 list/dict 嵌套的逐行数据
        for v in prof["numeric_means"].values():
            assert isinstance(v, float)

    def test_empty_idx_gives_zero_profile(self) -> None:
        prof = profile_unexplained(_profile_df(), np.array([], dtype=int),
                                   categorical_cols=("grade",))
        assert prof["n"] == 0


class TestRegimeStats:
    def test_per_year_bad_rate(self) -> None:
        df = pd.DataFrame({
            "episode": ["2013-01", "2013-02", "2014-01", "2014-02"],
            "outcome": [0, 1, 1, 1],
        })
        stats = regime_stats(df)
        assert [s["year"] for s in stats] == ["2013", "2014"]
        assert stats[0]["bad_rate"] == pytest.approx(0.5)
        assert stats[1]["bad_rate"] == pytest.approx(1.0)
        assert stats[1]["n"] == 2


class TestTrainAndImportance:
    def test_train_predict_and_importance_top(self) -> None:
        rng = np.random.default_rng(0)
        n = 400
        z = rng.normal(size=n)
        X = np.column_stack([z, rng.normal(size=n)]).astype(np.float32)
        y = (z > 0).astype(np.int8)
        model = train_gbdt(X, y, params={
            "n_estimators": 20, "num_leaves": 7, "min_child_samples": 5,
            "random_state": 1, "verbose": -1, "n_jobs": 1,
        }, seed=1)
        proba = predict_bad_proba(model, X)
        assert proba.shape == (n,)
        assert float(proba[z > 0].mean()) > float(proba[z <= 0].mean())
        top = importance_top(model, ["signal", "noise"], n=1)
        assert len(top) == 1
        assert top[0]["feature"] == "signal"
        assert top[0]["importance"] > 0
