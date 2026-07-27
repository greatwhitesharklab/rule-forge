"""CLAB-full 适配层测试:帧列齐(数值/类别/频次/seq)、时间红线、确定性。

红线(与 lite 版同语义):
  1. dev 帧只含前 dev_episodes 个 episode,且标签全部在 dev 窗末已成熟;
  2. 帧列 = episode + outcome + 8 数值观测 + 3 类别池内索引(int)+ 3 频次
     编码 + 10 个 seq_* 序列统计量,规则内容/潜因子绝不入帧;
  3. 类别高基处理:池内索引 int 列(表达式可 isin 引用值集)+ 频次编码列
     (值必须与帧内 value_counts 一致)。
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from selflearn.clabfull import (
    ALL_FEATURE_COLUMNS,
    CAT_FIELDS,
    CLABFULL_FIELD_STATEMENTS,
    FREQ_FIELDS,
    OBS_FIELDS,
    SEQ_FIELDS,
    ClabFullAutoCloud,
    build_clabfull_split,
    clabfull_base_features,
)
from synthfull import FullWorld, SEQ_STAT_NAMES, default_config


def _world(episodes: int = 10, per_episode: int = 80, seed: int = 42):
    cfg = default_config(seed=seed, n_experience=4, n_heldout=2,
                         switch_prob=0.0, pilot_size=1024)
    return FullWorld(cfg).run(episodes, per_episode)


class TestDevSplit:
    def test_frame_columns_exact(self) -> None:
        data = _world()
        split = build_clabfull_split(data, dev_episodes=7)
        df = split.dev_df
        expected = ({"episode", "outcome"} | set(OBS_FIELDS) | set(CAT_FIELDS)
                    | set(FREQ_FIELDS) | set(SEQ_FIELDS))
        assert set(df.columns) == expected
        assert tuple(ALL_FEATURE_COLUMNS) == (
            OBS_FIELDS + CAT_FIELDS + FREQ_FIELDS + SEQ_FIELDS
        )
        # seq 列命名:全部 seq_ 前缀,与 SEQ_STAT_NAMES 一一对应
        assert len(SEQ_FIELDS) == len(SEQ_STAT_NAMES) == 10
        for c in SEQ_FIELDS:
            assert c.startswith("seq_")

    def test_time_red_line_labels_matured_by_dev_end(self) -> None:
        data = _world()
        split = build_clabfull_split(data, dev_episodes=7)
        idx = split.dev_case_idx
        assert data.ledger.visible_episode[idx].max() <= 6
        assert data.casebook.episode[idx].max() < 7
        np.testing.assert_array_equal(
            split.dev_df["outcome"].to_numpy(), data.ledger.outcome[idx]
        )

    def test_unmatured_dev_cases_excluded(self) -> None:
        data = _world(episodes=10, per_episode=200)
        split = build_clabfull_split(data, dev_episodes=7)
        n_dev_total = int((data.casebook.episode < 7).sum())
        assert len(split.dev_df) < n_dev_total

    def test_deterministic_same_seed(self) -> None:
        a = build_clabfull_split(_world(), dev_episodes=7)
        b = build_clabfull_split(_world(), dev_episodes=7)
        pd.testing.assert_frame_equal(a.dev_df, b.dev_df)
        np.testing.assert_array_equal(a.dev_case_idx, b.dev_case_idx)

    def test_too_few_dev_episodes_rejected(self) -> None:
        data = _world()
        with pytest.raises(ValueError, match="dev_episodes"):
            build_clabfull_split(data, dev_episodes=0)


class TestCategoricalEncoding:
    def test_cat_columns_are_pool_index_ints(self) -> None:
        data = _world()
        split = build_clabfull_split(data, dev_episodes=7)
        idx = split.dev_case_idx
        for c in CAT_FIELDS:
            np.testing.assert_array_equal(
                split.dev_df[c].to_numpy(),
                data.casebook.categorical(c)[idx],
            )

    def test_freq_encoding_matches_value_counts(self) -> None:
        data = _world()
        split = build_clabfull_split(data, dev_episodes=7)
        df = split.dev_df
        for c, f in zip(CAT_FIELDS, FREQ_FIELDS):
            vc = df[c].value_counts()
            expected = df[c].map(vc).to_numpy(dtype=np.float64)
            np.testing.assert_array_equal(df[f].to_numpy(dtype=np.float64),
                                          expected)

    def test_seq_columns_match_seq_stats(self) -> None:
        from synthfull import seq_stats as compute_seq_stats

        data = _world()
        split = build_clabfull_split(data, dev_episodes=7)
        idx = split.dev_case_idx
        stats = compute_seq_stats(
            data.casebook.seq_events[idx],
            data.casebook.seq_durations[idx],
            data.casebook.seq_len[idx],
        )
        for j, name in enumerate(SEQ_STAT_NAMES):
            col = "seq_" + name if not name.startswith("seq_") else name
            np.testing.assert_allclose(
                split.dev_df[col].to_numpy(dtype=np.float64), stats[:, j],
                rtol=1e-6,
            )


class TestFieldStatementsAndBase:
    def test_statements_cover_all_feature_columns(self) -> None:
        assert set(CLABFULL_FIELD_STATEMENTS) == set(ALL_FEATURE_COLUMNS)
        for stmt in CLABFULL_FIELD_STATEMENTS.values():
            assert stmt.strip()

    def test_base_features_shape_and_finite(self) -> None:
        data = _world()
        split = build_clabfull_split(data, dev_episodes=7)
        feat_df = split.dev_df[list(ALL_FEATURE_COLUMNS)]
        X, names = clabfull_base_features(feat_df)
        assert X.shape == (len(feat_df), len(names))
        for f in OBS_FIELDS:
            assert f in names
        for f in CAT_FIELDS + FREQ_FIELDS + SEQ_FIELDS:
            assert f in names
        assert np.isfinite(X).all()


class TestEnumCloudSmoke:
    """枚举云端在适配帧上可构造(先验粗排 + 契约批吐)。"""

    def test_construct_and_execute(self) -> None:
        from cloud.contracts import TaskPackage

        data = _world(episodes=8, per_episode=60)
        split = build_clabfull_split(data, dev_episodes=6)
        df = split.dev_df[list(ALL_FEATURE_COLUMNS)]
        y = split.dev_df["outcome"].to_numpy().astype(np.int8)
        cloud = ClabFullAutoCloud(df, y, prior_sample=200)
        assert len(cloud.candidates) > 0
        kinds = {c["kind"] for c in cloud.candidates}
        assert {"single", "product", "ratio", "conjunction", "catset"} <= kinds
        task = TaskPackage(
            task_id="t1", task_type="feature_proposal",
            context={"case_profiles": [], "existing_features": [],
                     "dead_ends": []},
            constraints={"max_features": 5, "must_be_executable": "x",
                         "no_future_info": True},
            output_schema={"type": "object"},
        )
        res = cloud.execute(task)
        feats = res.content["features"]
        assert 0 < len(feats) <= 5
        for f in feats:
            assert {"name", "expression", "rationale"} <= set(f)
