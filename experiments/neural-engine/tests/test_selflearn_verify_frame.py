"""验证器 LendingClub 回测帧适配测试:注入帧替代 synth WorldData。

verify_feature 本身已接受任意 (df, labels);本组测试钉死新增的注入入口
backtest_frame_from_data:标签列剥离、非特征列剔除、黑名单列拒绝,
且 LendingClub 风格帧上验证流水线端到端可走通。
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from verify import FAIL, PASS, backtest_frame_from_data, verify_feature


def _lending_like(n: int = 2000, seed: int = 3) -> pd.DataFrame:
    """带 episode/outcome/派生文本列的 LendingClub 风格帧,z 驱动结局。"""
    rng = np.random.default_rng(seed)
    z = rng.normal(size=n)
    p_bad = 1.0 / (1.0 + np.exp(-(-1.0 + 1.5 * z)))
    y = (rng.random(n) < p_bad).astype(np.int8)
    return pd.DataFrame({
        "episode": np.repeat(["2013-01", "2013-02", "2013-03", "2013-04"], n // 4),
        "outcome": y,
        "dti": 15.0 + 5.0 * z,
        "annual_inc": 60000.0 + 1000.0 * rng.normal(size=n),
        "emp_title_norm": "employer",
    })


class TestBacktestFrameFromData:
    def test_splits_label_and_drops_non_feature_cols(self) -> None:
        raw = _lending_like()
        df, labels = backtest_frame_from_data(raw)
        assert "outcome" not in df.columns
        assert "episode" not in df.columns
        assert "dti" in df.columns and "emp_title_norm" in df.columns
        np.testing.assert_array_equal(labels, raw["outcome"].to_numpy())
        assert len(df) == len(raw)

    def test_missing_label_col_raises(self) -> None:
        with pytest.raises(ValueError, match="outcome"):
            backtest_frame_from_data(pd.DataFrame({"dti": [1.0]}))

    def test_extra_drop_cols(self) -> None:
        raw = _lending_like(20)
        df, _ = backtest_frame_from_data(raw, drop=("emp_title_norm",))
        assert "emp_title_norm" not in df.columns

    def test_post_decision_column_rejected(self) -> None:
        raw = _lending_like(20)
        raw["total_pymnt"] = 1.0  # 贷后字段,读了就是特征穿越
        with pytest.raises(ValueError, match="total_pymnt"):
            backtest_frame_from_data(raw)


class TestVerifyOnInjectedLendingFrame:
    def test_predictive_expression_passes_on_injected_frame(self) -> None:
        raw = _lending_like()
        df, y = backtest_frame_from_data(raw)
        v = verify_feature("df.dti", df, y)
        assert v.status == PASS, v.reasons

    def test_constant_expression_fails_on_injected_frame(self) -> None:
        raw = _lending_like()
        df, y = backtest_frame_from_data(raw)
        v = verify_feature("df.dti * 0.0 + 1.0", df, y)
        assert v.status == FAIL
