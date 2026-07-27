"""CLAB synth 适配层测试:dev/eval 切分、时间红线(LAG)、字段白名单。

红线:
  1. dev 帧只含前 dev_episodes 个 episode,且标签全部在 dev 窗末已成熟
     (visible_episode <= dev 窗末)——迭代永远看不到未成熟标签;
  2. 帧列 = episode + outcome + 8 可观测字段,规则内容/概念真值绝不入帧;
  3. 同种子世界两次构建 bit-identical。
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from synth import SyntheticWorld, default_config
from selflearn.clab import (
    CLAB_FIELDS,
    CLAB_FIELD_STATEMENTS,
    build_clab_split,
    clab_base_features,
)


def _world(episodes: int = 12, per_episode: int = 50, seed: int = 42):
    w = SyntheticWorld(default_config(seed=seed))
    return w.run(episodes, per_episode)


class TestDevSplit:
    def test_dev_frame_window_and_columns(self) -> None:
        data = _world()
        split = build_clab_split(data, dev_episodes=8)
        df = split.dev_df
        # 列 = episode + outcome + 8 可观测字段,无其他任何信息
        assert set(df.columns) == {"episode", "outcome"} | set(CLAB_FIELDS)
        # LAG:dev 窗末 episode(delay>=1,窗末无一成熟)整个不进帧;
        # 次末 episode 只有 delay=1 的部分案例成熟可见
        eps = sorted(df["episode"].unique())
        assert eps == [f"{i:03d}" for i in range(8 - 1)]
        assert df["outcome"].isin([0, 1]).all()

    def test_time_red_line_labels_matured_by_dev_end(self) -> None:
        data = _world()
        split = build_clab_split(data, dev_episodes=8)
        idx = split.dev_case_idx
        # LAG:dev 帧内所有案例的结局在 dev 窗末(episode 7)已可见
        assert data.ledger.visible_episode[idx].max() <= 7
        # eval 窗(episode >= 8)案例一律不入 dev 帧
        assert data.casebook.episode[idx].max() < 8
        # 标签逐行对齐 ledger
        np.testing.assert_array_equal(
            split.dev_df["outcome"].to_numpy(), data.ledger.outcome[idx]
        )
        # episode 列逐行对齐 casebook
        np.testing.assert_array_equal(
            split.dev_df["episode"].map(int).to_numpy(),
            data.casebook.episode[idx],
        )

    def test_unmatured_dev_cases_excluded(self) -> None:
        """dev 窗尾段(delay 2~3)未成熟案例必须被丢掉,而不是带标签混进来。"""
        data = _world(episodes=12, per_episode=200)
        split = build_clab_split(data, dev_episodes=8)
        n_dev_total = int((data.casebook.episode < 8).sum())
        assert len(split.dev_df) < n_dev_total  # 尾段未成熟案例被排除

    def test_deterministic_same_seed(self) -> None:
        a = build_clab_split(_world(), dev_episodes=8)
        b = build_clab_split(_world(), dev_episodes=8)
        pd.testing.assert_frame_equal(a.dev_df, b.dev_df)
        np.testing.assert_array_equal(a.dev_case_idx, b.dev_case_idx)

    def test_too_few_dev_episodes_rejected(self) -> None:
        data = _world()
        with pytest.raises(ValueError, match="dev_episodes"):
            build_clab_split(data, dev_episodes=0)
        with pytest.raises(ValueError, match="dev_episodes"):
            build_clab_split(data, dev_episodes=99)


class TestFieldStatements:
    def test_statements_cover_all_fields(self) -> None:
        assert set(CLAB_FIELD_STATEMENTS) == set(CLAB_FIELDS)
        for stmt in CLAB_FIELD_STATEMENTS.values():
            assert stmt.strip()


class TestBaseFeatures:
    def test_shape_and_names(self) -> None:
        data = _world()
        split = build_clab_split(data, dev_episodes=8)
        X, names = clab_base_features(split.dev_df[list(CLAB_FIELDS)])
        assert X.shape == (len(split.dev_df), len(names))
        for f in CLAB_FIELDS:
            assert f in names
        assert np.isfinite(X).all()
