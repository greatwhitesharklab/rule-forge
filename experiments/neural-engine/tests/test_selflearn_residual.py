"""残余信号指路测试(§8.5 ①增强):同 proba 箱内对照,暴露 GBDT 未利用的信号。

核心构造:x_seen 是 GBDT 已利用的字段(proba 完全由它决定),h_hidden 是
GBDT 看不见的隐藏风险维度(结局由 x+h 共同决定)。漏网坏账 = 低 x 但因 h
高而违约;同箱 good = 同样低 x 且 h 低。因此 h_hidden 的残余效应必须显著
大于 x_seen —— 这就是"控制已学信息、暴露残余信号"的语义。
"""

from __future__ import annotations

import json

import numpy as np
import pandas as pd
import pytest

from selflearn.gbdt import residual_signal_analysis, unexplained_bads


def _make(n: int = 4000, seed: int = 0):
    rng = np.random.default_rng(seed)
    x = rng.normal(size=n)  # GBDT 可见并已利用
    h = rng.normal(size=n)  # GBDT 看不见的隐藏风险维度
    p_bad = 1.0 / (1.0 + np.exp(-(x + 2.0 * h)))
    y = (rng.random(n) < p_bad).astype(np.int8)
    proba = 1.0 / (1.0 + np.exp(-x))  # GBDT 只用 x 打分
    df = pd.DataFrame({
        "x_seen": x,
        "h_hidden": h,
        "grade": np.where(h > 0.5, "G", "A"),
        "emp_title_norm": np.where(h > 0.5, "unemployed worker", "acme corp"),
    })
    return df, y, proba


class TestResidualVsLearned:
    def test_hidden_field_dominates_residual_ranking(self) -> None:
        df, y, proba = _make()
        res = residual_signal_analysis(df, y, proba, top_k=100)
        assert res["n_missed"] == 100
        assert res["n_controls"] > 0
        numeric = {e["feature"]: e for e in res["numeric"]}
        assert res["numeric"][0]["feature"] == "h_hidden"
        # 残余驱动(h)的效应量必须显著大于已利用字段(x)
        assert abs(numeric["h_hidden"]["cohens_d"]) > 0.5
        assert abs(numeric["h_hidden"]["cohens_d"]) > 2 * abs(
            numeric["x_seen"]["cohens_d"]
        )
        assert numeric["h_hidden"]["direction"] == "missed_higher"
        # 分布统计齐全且为聚合标量
        for key in ("mean", "median", "p25", "p75"):
            assert key in numeric["h_hidden"]["missed"]
            assert key in numeric["h_hidden"]["control"]

    def test_perfectly_learned_field_leaves_tiny_residual(self) -> None:
        # 结局完全由 x 决定且 proba 同序:箱内 x 分布几乎重合,残余 ≈ 0
        rng = np.random.default_rng(1)
        x = rng.normal(size=4000)
        y = (x > 0).astype(np.int8)
        proba = 1.0 / (1.0 + np.exp(-x))
        df = pd.DataFrame({"x": x})
        res = residual_signal_analysis(df, y, proba, top_k=100)
        assert abs(res["numeric"][0]["cohens_d"]) < 0.3


class TestBinEdges:
    def test_constant_proba_single_bin_uses_all_goods(self) -> None:
        df = pd.DataFrame({"x": [1.0, 2.0, 3.0, 4.0]})
        y = np.array([0, 1, 0, 1], dtype=np.int8)
        proba = np.full(4, 0.5)  # 全部同箱:对照 = 全部 good
        res = residual_signal_analysis(df, y, proba, top_k=1)
        assert res["n_missed"] == 1
        assert res["n_controls"] == 2

    def test_no_bads_returns_empty(self) -> None:
        df = pd.DataFrame({"x": [1.0, 2.0]})
        res = residual_signal_analysis(
            df, np.zeros(2, dtype=np.int8), np.zeros(2), top_k=3
        )
        assert res["n_missed"] == 0
        assert res["numeric"] == [] and res["categorical"] == []

    def test_duplicate_proba_ties_stable(self) -> None:
        # 大量 proba 并列:qcut duplicates=drop 不炸,分箱数 < 请求数
        rng = np.random.default_rng(2)
        y = (rng.random(500) < 0.3).astype(np.int8)
        proba = np.round(rng.random(500), 1)  # 11 个离散值,大量并列
        df = pd.DataFrame({"x": rng.normal(size=500)})
        res = residual_signal_analysis(df, y, proba, top_k=10)
        assert res["n_missed"] == 10
        assert res["n_controls"] > 0


class TestCategoricalAndTokens:
    def test_categorical_share_diff(self) -> None:
        df, y, proba = _make()
        res = residual_signal_analysis(df, y, proba, top_k=100)
        top = res["categorical"][0]
        assert top["column"] == "grade"
        assert top["value"] == "G"
        assert top["diff"] > 0  # 漏网组 G 占比显著高于同箱 good
        assert top["missed_share"] > top["control_share"]

    def test_token_frequency_gap(self) -> None:
        df, y, proba = _make()
        res = residual_signal_analysis(df, y, proba, top_k=100)
        tokens = {t["token"]: t for t in res["emp_title_tokens"]}
        assert "unemployed" in tokens
        assert tokens["unemployed"]["diff"] > 0
        assert tokens["unemployed"]["missed_freq"] > tokens["unemployed"]["control_freq"]

    def test_min_token_count_filters_one_offs(self) -> None:
        df = pd.DataFrame({
            "x": np.arange(10.0),
            "emp_title_norm": [
                "uniqueco", "rare inc", "rare inc", "acme", "acme",
                "acme", "acme", "acme", "acme", "acme",
            ],
        })
        y = np.array([1] + [0] * 9, dtype=np.int8)
        proba = np.full(10, 0.5)
        res = residual_signal_analysis(df, y, proba, top_k=1,
                                       min_token_count=2)
        # 漏网组只有 1 行,任何 token 计数都是 1 < min_count -> 空
        assert res["emp_title_tokens"] == []


class TestOutputShape:
    def test_json_serializable_aggregate_only(self) -> None:
        df, y, proba = _make()
        res = residual_signal_analysis(df, y, proba, top_k=50)
        json.dumps(res)  # 必须可直接进 prompt context
        # top-N 截断生效
        assert len(res["numeric"]) <= 8
        assert len(res["categorical"]) <= 5
        assert len(res["emp_title_tokens"]) <= 10

    def test_top_n_configurable(self) -> None:
        df, y, proba = _make()
        res = residual_signal_analysis(df, y, proba, top_k=100,
                                       top_numeric=1, top_tokens=1)
        assert len(res["numeric"]) == 1
        assert len(res["emp_title_tokens"]) == 1

    def test_missing_columns_skipped(self) -> None:
        df = pd.DataFrame({"x": np.arange(20.0)})
        y = np.array([1, 0] * 10, dtype=np.int8)
        proba = np.linspace(0.1, 0.9, 20)
        res = residual_signal_analysis(df, y, proba, top_k=3)
        assert res["categorical"] == []  # grade/purpose/... 不存在,静默跳过
        assert res["emp_title_tokens"] == []  # text_col 不存在
