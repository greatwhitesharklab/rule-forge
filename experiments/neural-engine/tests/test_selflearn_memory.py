"""策略知识记忆测试:特征槽初始化、新特征槽、死路槽、检索。

复用 slots 包(不改):字段槽 status=active,验证通过的新特征 status=shadow,
失败方向 status=retired 且 value_text 含死因(§8.4 死路档案)。
"""

from __future__ import annotations

import numpy as np
import pytest

from lending.prepare import FEATURE_COLS
from selflearn.memory import StrategyMemory
from slots import SlotConfig, SlotService


@pytest.fixture()
def mem(tmp_path):
    service = SlotService(tmp_path / "slots.db", SlotConfig())
    m = StrategyMemory(service)  # 默认 fake(hash) encoder,确定性
    yield m
    m.close()


class TestFieldSlotInit:
    def test_init_creates_one_active_slot_per_whitelist_field(self, mem) -> None:
        n = mem.init_field_slots(FEATURE_COLS)
        assert n == len(FEATURE_COLS) == 24
        slots = mem.service.store.all_slots()
        assert len(slots) == 24
        assert all(s.status == "active" for s in slots)
        # value_text 是字段陈述(含中文字段说明),可审计
        loan = [s for s in slots if s.value_text.startswith("字段:loan_amnt")]
        assert len(loan) == 1 and "申请贷款金额" in loan[0].value_text

    def test_init_is_idempotent(self, mem) -> None:
        mem.init_field_slots(FEATURE_COLS)
        assert mem.init_field_slots(FEATURE_COLS) == 0
        assert len(mem.service.store.all_slots()) == 24


class TestFeatureAndDeadEndSlots:
    def test_accepted_feature_becomes_shadow_slot(self, mem) -> None:
        slot = mem.add_feature_slot(
            "dti_x_loan", "高 dti 与大额借款交互放大违约风险",
            provenance="replay:replay-file#selflearn-r01",
        )
        assert slot.status == "shadow"
        assert "特征:dti_x_loan" in slot.value_text
        assert "高 dti" in slot.value_text

    def test_failed_direction_becomes_retired_dead_end_with_cause(self, mem) -> None:
        slot = mem.add_dead_end("const_feature", "常数特征无区分度", cause="区分度不足")
        assert slot.status == "retired"
        assert "死路:const_feature" in slot.value_text
        assert "死因:区分度不足" in slot.value_text

    def test_dead_end_list_returns_only_retired(self, mem) -> None:
        mem.init_field_slots(FEATURE_COLS)
        mem.add_feature_slot("ok_feat", "有用", provenance="p")
        mem.add_dead_end("bad_feat", "没用", cause="区分度不足")
        dead = mem.dead_end_list()
        assert len(dead) == 1
        assert "bad_feat" in dead[0] and "死因" in dead[0]
        assert all("ok_feat" not in d for d in dead)


class TestRetrieval:
    def test_experience_summaries_exclude_retired(self, mem) -> None:
        mem.init_field_slots(FEATURE_COLS)
        mem.add_feature_slot("good_one", "稳定收入假设", provenance="p")
        mem.add_dead_end("dead_one", "失败假设", cause="区分度不足")
        hits = mem.experience_summaries("收入 稳定 违约", k=5)
        assert isinstance(hits, list)
        assert all(isinstance(h, str) for h in hits)
        # 死路不进经验检索(死路由 dead_end_list 单独进 prompt)
        assert all("dead_one" not in h for h in hits)

    def test_experience_summaries_empty_query_returns_empty(self, mem) -> None:
        mem.init_field_slots(FEATURE_COLS)
        assert mem.experience_summaries("", k=5) == []
