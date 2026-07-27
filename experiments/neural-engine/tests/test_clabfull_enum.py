"""CLAB-full 枚举云端测试:新模态候选形态(类别值集标志/seq 阈值/数值交互)。

枚举臂 = 暴力基线的 full 版:在 lite 的 single/product/ratio/conjunction
之上扩两类新模态 ——
  * catset:类别值集标志(df.<cat>.isin([...]).astype(float)),值集由
    先验坏账率粗排(风险方向 top + 保护方向)选定;
  * seq 统计量阈值:seq_* 列进 single 分箱枚举(与普通数值列同构)。
所有表达式必须过 §3.2 AST 白名单且在适配帧上可执行。
"""

from __future__ import annotations

import re

import numpy as np
import pandas as pd
import pytest

from selflearn.clabfull import (
    ALL_FEATURE_COLUMNS,
    CAT_FIELDS,
    OBS_FIELDS,
    SEQ_FIELDS,
    ClabFullAutoCloud,
)
from synthfull import FullWorld, default_config
from verify.sandbox import check_expression_ast, run_expression

_DF_FIELD_RE = re.compile(r"df\.([A-Za-z_][A-Za-z0-9_]*)")


def _frame(episodes: int = 8, per_episode: int = 120, seed: int = 5):
    from selflearn.clabfull import build_clabfull_split

    cfg = default_config(seed=seed, n_experience=4, n_heldout=2,
                         switch_prob=0.0, pilot_size=1024)
    data = FullWorld(cfg).run(episodes, per_episode)
    split = build_clabfull_split(data, dev_episodes=6)
    df = split.dev_df[list(ALL_FEATURE_COLUMNS)]
    y = split.dev_df["outcome"].to_numpy().astype(np.int8)
    return df, y


class TestEnumeration:
    def test_candidate_forms_cover_new_modalities(self) -> None:
        df, y = _frame()
        cloud = ClabFullAutoCloud(df, y, prior_sample=300)
        kinds = {c["kind"] for c in cloud.candidates}
        assert {"single", "product", "ratio", "conjunction", "catset"} <= kinds

    def test_seq_stats_enter_single_enumeration(self) -> None:
        df, y = _frame()
        cands = ClabFullAutoCloud.enumerate_candidates(df)
        single_fields = {
            m.group(1)
            for c in cands if c["kind"] == "single"
            for m in [_DF_FIELD_RE.search(c["expression"])] if m
        }
        for f in SEQ_FIELDS:
            assert f in single_fields
        # 类别池内索引列不做数值阈值枚举(序数无意义),值集走 catset
        for f in CAT_FIELDS:
            assert f not in single_fields

    def test_catset_candidates_reference_cat_fields(self) -> None:
        df, y = _frame()
        cloud = ClabFullAutoCloud(df, y, prior_sample=300)
        catset = [c for c in cloud.candidates if c["kind"] == "catset"]
        assert catset
        fields = {
            m.group(1) for c in catset
            for m in [_DF_FIELD_RE.search(c["expression"])] if m
        }
        assert fields <= set(CAT_FIELDS)
        assert len(fields) == len(CAT_FIELDS)  # 三个类别字段都有值集候选
        for c in catset:
            assert ".isin([" in c["expression"]

    def test_no_duplicate_names_or_expressions(self) -> None:
        df, y = _frame()
        cloud = ClabFullAutoCloud(df, y, prior_sample=300)
        names = [c["name"] for c in cloud.candidates]
        exprs = [c["expression"] for c in cloud.candidates]
        assert len(names) == len(set(names))
        assert len(exprs) == len(set(exprs))

    def test_expressions_pass_ast_and_execute(self) -> None:
        df, y = _frame()
        cloud = ClabFullAutoCloud(df, y, prior_sample=300)
        # 全量 AST;抽样沙箱执行(catset 必含)
        for c in cloud.candidates:
            assert check_expression_ast(c["expression"]) == [], c["expression"]
        sample = [c for c in cloud.candidates if c["kind"] == "catset"][:3]
        sample += cloud.candidates[:5]
        for c in sample:
            run = run_expression(c["expression"], df, timeout_s=15.0)
            assert run.ok, f"{c['name']}: {run.error}"
            assert run.values.shape == (len(df),)

    def test_prior_ranking_deterministic(self) -> None:
        df, y = _frame()
        a = ClabFullAutoCloud(df, y, prior_sample=300)
        b = ClabFullAutoCloud(df, y, prior_sample=300)
        assert [c["name"] for c in a.candidates] == [
            c["name"] for c in b.candidates
        ]

    def test_cursor_advances_without_repeats(self) -> None:
        from cloud.contracts import TaskPackage

        df, y = _frame()
        cloud = ClabFullAutoCloud(df, y, prior_sample=300)

        def task(tid: str) -> TaskPackage:
            return TaskPackage(
                task_id=tid, task_type="feature_proposal",
                context={"case_profiles": [], "existing_features": [],
                         "dead_ends": []},
                constraints={"max_features": 7, "must_be_executable": "x",
                             "no_future_info": True},
                output_schema={"type": "object"},
            )

        r1 = cloud.execute(task("t1"))
        r2 = cloud.execute(task("t2"))
        n1 = {f["name"] for f in r1.content["features"]}
        n2 = {f["name"] for f in r2.content["features"]}
        assert len(n1) == 7 and len(n2) == 7
        assert n1.isdisjoint(n2)


class TestCatValueSets:
    def test_risk_sets_have_higher_bad_rate(self) -> None:
        """粗排选出的风险值集,其经验坏账率必须高于保护值集(机制断言)。"""
        rng = np.random.default_rng(3)
        n = 4000
        region = rng.integers(0, 20, n)
        y = (rng.random(n) < np.where(np.isin(region, [3, 7]), 0.8, 0.1)
             ).astype(np.int8)
        df = pd.DataFrame({"region": region})
        sets = ClabFullAutoCloud.cat_value_sets(
            df, y, fields=("region",), min_count=30, top_k=2
        )
        rate = lambda v: y[region == v].mean()  # noqa: E731
        assert rate(max(sets["region"]["risk"], key=rate)) > rate(
            min(sets["region"]["protective"], key=rate)
        )

    def test_min_count_filters_rare_values(self) -> None:
        rng = np.random.default_rng(4)
        n = 2000
        region = rng.integers(0, 50, n)
        region[:5] = 49  # 极稀有值
        y = (rng.random(n) < 0.2).astype(np.int8)
        df = pd.DataFrame({"region": region})
        sets = ClabFullAutoCloud.cat_value_sets(
            df, y, fields=("region",), min_count=30, top_k=3
        )
        assert 49 not in sets["region"]["risk"]
