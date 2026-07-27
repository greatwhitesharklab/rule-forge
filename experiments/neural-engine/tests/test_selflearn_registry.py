"""L2 特征运行时注册测试:表达式 -> 注册表 -> GBDT 重训吃新特征。

§8.2 定义形式:name/version/author(云端 provenance)/docstring(假设陈述);
§8.3 铁律一:训练与推理走同一份计算代码(registry.compute);
§8.4 增量价值:与现有特征相关性 >= 0.9 拒绝入库。
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from scoring.features import FeatureRegistry
from selflearn.features import (
    compile_l2_expression,
    max_abs_correlation,
    register_l2_feature,
)
from selflearn.gbdt import train_gbdt


def _df(n: int = 300, seed: int = 5) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    return pd.DataFrame({
        "dti": rng.uniform(0, 40, n),
        "annual_inc": rng.uniform(20000, 150000, n),
        "loan_amnt": rng.uniform(1000, 35000, n),
    })


class TestCompileL2Expression:
    def test_vectorized_expression_computes(self) -> None:
        fn = compile_l2_expression("df.dti * df.loan_amnt / (df.annual_inc + 1.0)")
        df = _df()
        out = fn(df)
        assert len(out) == len(df)
        expected = df["dti"] * df["loan_amnt"] / (df["annual_inc"] + 1.0)
        np.testing.assert_allclose(out.to_numpy(dtype=float), expected.to_numpy())

    def test_evil_expression_rejected_at_compile_time(self) -> None:
        with pytest.raises(ValueError, match="whitelist"):
            compile_l2_expression("__import__('os').system('id')")

    def test_non_whitelisted_name_rejected(self) -> None:
        with pytest.raises(ValueError):
            compile_l2_expression("open('x').read()")


class TestRegisterL2Feature:
    def test_register_carries_provenance_and_assumption(self) -> None:
        reg = FeatureRegistry()
        spec = register_l2_feature(
            reg, name="dti_x_loan",
            expression="df.dti * df.loan_amnt",
            rationale="高 dti 与大额借款交互放大违约风险",
            author="replay:replay-file#selflearn-r01",
        )
        assert spec.level == "L2"
        assert spec.author == "replay:replay-file#selflearn-r01"
        assert "高 dti" in spec.assumption  # docstring = 假设陈述
        assert "dti_x_loan" in reg.names

    def test_gbdt_retrain_eats_new_feature(self) -> None:
        # 结局完全由 hidden 交互决定;新特征注册后 GBDT 能把它学出来
        df = _df(n=600)
        score = df["dti"].to_numpy() * df["loan_amnt"].to_numpy() / (
            df["annual_inc"].to_numpy() + 1.0
        )
        y = (score > np.median(score)).astype(np.int8)

        reg = FeatureRegistry()
        register_l2_feature(reg, name="dti_x_loan",
                            expression="df.dti * df.loan_amnt / (df.annual_inc + 1.0)",
                            rationale="交互假设", author="test")
        X_l2 = reg.compute(df)
        assert list(X_l2.columns) == ["dti_x_loan"]

        X_base = df[["dti", "annual_inc", "loan_amnt"]].to_numpy()
        X_full = np.hstack([X_base, X_l2.to_numpy()])
        model = train_gbdt(X_full, y, params={
            "n_estimators": 30, "num_leaves": 7, "min_child_samples": 10,
            "random_state": 7, "verbose": -1, "n_jobs": 1,
        }, seed=7)
        proba = model.predict_proba(X_full)[:, 1]
        from sklearn.metrics import roc_auc_score
        assert roc_auc_score(y, proba) > 0.95


class TestMaxAbsCorrelation:
    def test_identical_column_gives_one(self) -> None:
        ref = pd.DataFrame({"a": [1.0, 2.0, 3.0, 4.0]})
        assert max_abs_correlation([2.0, 4.0, 6.0, 8.0], ref) == pytest.approx(1.0)

    def test_orthogonal_gives_near_zero(self) -> None:
        rng = np.random.default_rng(1)
        a = rng.normal(size=5000)
        b = rng.normal(size=5000)
        assert max_abs_correlation(a, pd.DataFrame({"b": b})) < 0.1

    def test_nan_pairs_handled(self) -> None:
        ref = pd.DataFrame({"a": [1.0, 2.0, np.nan, 4.0, 5.0]})
        v = [2.0, 4.0, 6.0, 8.0, 10.0]
        assert max_abs_correlation(v, ref) == pytest.approx(1.0)

    def test_empty_ref_gives_zero(self) -> None:
        assert max_abs_correlation([1.0, 2.0], pd.DataFrame()) == 0.0
