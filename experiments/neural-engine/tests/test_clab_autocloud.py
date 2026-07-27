"""自动候选云端(暴力基线臂)测试:枚举正确性、契约格式、轮次推进。

基线臂回答「给定足够候选,闭环能不能筛出保留规则」:程序化枚举二阶交互
与单字段分箱,按先验 IV 粗排,每轮以 feature_proposal 契约喂 top-M。
候选表达式只能引用 8 个可观测字段(沙箱白名单 + 字段白名单双重断言)。
"""

from __future__ import annotations

import re

import numpy as np
import pandas as pd
import pytest

from cloud.contracts import TaskPackage, validate_result
from selflearn.clab import CLAB_FIELDS, ClabAutoCloud
from verify.sandbox import check_expression_ast, run_expression

_DF_FIELD_RE = re.compile(r"df\.([A-Za-z_][A-Za-z0-9_]*)")


def _dev_like(n: int = 3000, seed: int = 3) -> tuple[pd.DataFrame, np.ndarray]:
    rng = np.random.default_rng(seed)
    df = pd.DataFrame({f: rng.gamma(2.0, 0.3, n) for f in CLAB_FIELDS})
    z = df[CLAB_FIELDS[0]] + rng.normal(0, 0.5, n)
    y = (rng.random(n) < 1.0 / (1.0 + np.exp(-(z - z.mean())))).astype(np.int8)
    return df, y


def _task(max_features: int = 4, task_id: str = "selflearn-r01") -> TaskPackage:
    return TaskPackage(
        task_id=task_id,
        task_type="feature_proposal",
        context={"case_profiles": [], "existing_features": [], "dead_ends": []},
        constraints={"max_features": max_features,
                     "must_be_executable": "x", "no_future_info": True},
        output_schema={"type": "object"},
    )


class TestEnumeration:
    def test_no_duplicate_names_or_expressions(self) -> None:
        df, _ = _dev_like()
        cands = ClabAutoCloud.enumerate_candidates(df)
        names = [c["name"] for c in cands]
        exprs = [c["expression"] for c in cands]
        assert len(names) == len(set(names))
        assert len(exprs) == len(set(exprs))
        assert len(cands) > 100  # 暴力基线:候选量要足够大

    def test_covers_all_candidate_forms(self) -> None:
        df, _ = _dev_like()
        kinds = {c["kind"] for c in ClabAutoCloud.enumerate_candidates(df)}
        assert {"single", "product", "ratio", "conjunction"} <= kinds

    def test_expressions_pass_ast_whitelist(self) -> None:
        df, _ = _dev_like()
        for c in ClabAutoCloud.enumerate_candidates(df):
            assert check_expression_ast(c["expression"]) == [], c["expression"]

    def test_expressions_reference_only_observable_fields(self) -> None:
        df, _ = _dev_like()
        for c in ClabAutoCloud.enumerate_candidates(df):
            used = set(_DF_FIELD_RE.findall(c["expression"]))
            assert used <= set(CLAB_FIELDS), c["expression"]

    def test_expressions_execute_in_sandbox(self) -> None:
        df, _ = _dev_like(n=200)
        cands = ClabAutoCloud.enumerate_candidates(df)
        rng = np.random.default_rng(0)
        for c in rng.choice(np.array(cands, dtype=object), size=8, replace=False):
            res = run_expression(c["expression"], df, timeout_s=10)
            assert res.ok, (c["expression"], res.error)
            assert len(res.values) == len(df)

    def test_deterministic(self) -> None:
        df, _ = _dev_like()
        a = ClabAutoCloud.enumerate_candidates(df)
        b = ClabAutoCloud.enumerate_candidates(df)
        assert [c["expression"] for c in a] == [c["expression"] for c in b]


class TestContractAndRounds:
    def test_execute_returns_contract_valid_result(self) -> None:
        df, y = _dev_like()
        cloud = ClabAutoCloud(df, y, seed=1)
        res = cloud.execute(_task(max_features=4))
        validate_result("feature_proposal", res.content)
        feats = res.content["features"]
        assert len(feats) == 4
        for f in feats:
            assert f["name"] and f["expression"] and f["rationale"]
        assert res.provenance.provider == "clab-auto"

    def test_rounds_advance_without_repeats(self) -> None:
        df, y = _dev_like()
        cloud = ClabAutoCloud(df, y, seed=1)
        seen: list[str] = []
        for r in range(1, 4):
            res = cloud.execute(_task(max_features=5, task_id=f"selflearn-r{r:02d}"))
            seen.extend(f["name"] for f in res.content["features"])
        assert len(seen) == 15
        assert len(set(seen)) == 15  # 跨轮不重复

    def test_exhaustion_returns_empty_batch(self) -> None:
        df, y = _dev_like(n=500)
        cloud = ClabAutoCloud(df, y, seed=1)
        total = len(cloud.candidates)
        big = _task(max_features=total + 10)
        res = cloud.execute(big)
        assert len(res.content["features"]) == total
        assert cloud.execute(_task()).content["features"] == []

    def test_prior_ranking_by_iv_descending(self) -> None:
        df, y = _dev_like()
        cloud = ClabAutoCloud(df, y, seed=1)
        ivs = [c["prior_iv"] for c in cloud.candidates]
        assert ivs == sorted(ivs, reverse=True)

    def test_context_is_recorded_for_isolation_audit(self) -> None:
        df, y = _dev_like()
        cloud = ClabAutoCloud(df, y, seed=1)
        cloud.execute(_task())
        assert len(cloud.seen_contexts) == 1
