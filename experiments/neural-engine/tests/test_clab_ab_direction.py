"""机制二:候选「方向」归一化测试(死路档案的粒度)。

方向 = 结构归一化键:乘积/合取交换律变体同键、同方向不同分位同键、
比值有序。死路按方向归档,枚举云端按方向跳过。
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from selflearn.clab import CLAB_FIELDS, ClabAutoCloud, direction_key


class TestDirectionKey:
    def test_product_commutes(self) -> None:
        a = direction_key("product", (("dti", "*"), ("revol", "*")))
        b = direction_key("product", (("revol", "*"), ("dti", "*")))
        assert a == b

    def test_conjunction_commutes_with_ops_attached(self) -> None:
        a = direction_key("conjunction", (("dti", ">"), ("revol", "<")))
        b = direction_key("conjunction", (("revol", "<"), ("dti", ">")))
        assert a == b
        # 比较方向绑在字段上:换方向即换方向键
        c = direction_key("conjunction", (("dti", "<"), ("revol", ">")))
        assert c != a

    def test_ratio_is_ordered(self) -> None:
        a = direction_key("ratio", (("dti", "/"), ("revol", "/")))
        b = direction_key("ratio", (("revol", "/"), ("dti", "/")))
        assert a != b

    def test_single_key_by_field_and_op(self) -> None:
        assert direction_key("single", (("dti", ">"),)) == \
            direction_key("single", (("dti", ">"),))
        assert direction_key("single", (("dti", ">"),)) != \
            direction_key("single", (("dti", "<"),))

    def test_unknown_kind_raises(self) -> None:
        with pytest.raises(ValueError, match="kind"):
            direction_key("cubic", (("a", "*"),))


def _df(n: int = 500, seed: int = 3) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    return pd.DataFrame({f: rng.gamma(2.0, 0.3, n) for f in CLAB_FIELDS})


class TestEnumeratorEmitsDirections:
    def test_every_candidate_has_direction(self) -> None:
        for c in ClabAutoCloud.enumerate_candidates(_df()):
            assert c["direction"], c

    def test_quantile_variants_share_direction(self) -> None:
        cands = ClabAutoCloud.enumerate_candidates(_df())
        f0 = CLAB_FIELDS[0]
        singles = [c for c in cands
                   if c["kind"] == "single" and c["name"].startswith("sg_f0_gt")]
        assert len(singles) >= 2  # 多个分位变体
        assert {c["direction"] for c in singles} == {
            direction_key("single", ((f0, ">"),))
        }

    def test_conjunction_variants_share_direction(self) -> None:
        cands = ClabAutoCloud.enumerate_candidates(_df())
        conjs = [c for c in cands if c["kind"] == "conjunction"
                 and c["name"].startswith("cj_f0_gg_f1_")]
        assert len(conjs) >= 2  # 分位组合变体
        assert len({c["direction"] for c in conjs}) == 1

    def test_directions_fewer_than_candidates(self) -> None:
        cands = ClabAutoCloud.enumerate_candidates(_df())
        assert len({c["direction"] for c in cands}) < len(cands)
