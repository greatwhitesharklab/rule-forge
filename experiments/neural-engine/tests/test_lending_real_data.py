"""Real-data smoke test for the LendingClub pipeline (opt-in, default skip).

Enable with LENDING_REAL=1:

    LENDING_REAL=1 uv run pytest experiments/neural-engine/tests/test_lending_real_data.py -q

Reads the real 1.6GB CSV once; asserts headline numbers are sane.
"""

import os
from pathlib import Path

import pandas as pd
import pytest

from lending import prepare as lp

pytestmark = pytest.mark.skipif(
    os.environ.get("LENDING_REAL") != "1",
    reason="real 1.6GB LendingClub CSV smoke test; enable with LENDING_REAL=1",
)


def test_real_prepare_smoke(tmp_path):
    report = lp.prepare(input_path=lp.DEFAULT_INPUT, out_dir=tmp_path)
    df = pd.read_parquet(tmp_path / lp.EPISODES_PARQUET)

    assert report["rows_total"] > 2_000_000
    assert report["episodes"] > 100
    # 整体 bad_rate 在合理区间
    assert 0.1 <= report["bad_rate_overall"] <= 0.3
    # 每 episode 上限生效
    assert df.groupby("episode", observed=True).size().max() <= 20000
    # 时间漂移证据: episode bad_rate 有明显起伏
    rates = df.groupby("episode", observed=True)["outcome"].mean()
    assert rates.max() - rates.min() > 0.05
    assert not (set(lp.BLACKLIST_COLS) & set(df.columns))
