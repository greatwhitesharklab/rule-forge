"""阶段 1.2:baseline instrumentation 测试。

给 selflearn/loop.py 加最小 instrumentation,让它跑完一轮后能产出 RoundExtras
(cloud_calls / dead_end_repeats),供 reward 计算消费。

设计(最小改动,baseline 增强非污染):
- RoundRecord 加可选字段 extras: RoundExtras | None = None
  (默认 None,不破坏现有调用方 —— run()/验收脚本不感知)
- run_round 在验证循环里计数 dead_end_repeats:
  提案的 name 如果已经出现在死路档案的 case_id(f"死路:{name}")里 = 重复
- cloud_calls 固定 = 1(现在 run_round 一次只调一次云端;留字段为未来编排器扩展)
"""

from __future__ import annotations

import pytest

from selflearn.loop import RoundRecord
from selflearn.reward import RoundExtras


class TestRoundRecordExtras:
    """RoundRecord 加 extras 字段,默认 None 不破坏现有用法。"""

    def test_extras_默认_None(self):
        # Given: 不传 extras
        rec = RoundRecord(
            round_no=1, task_id="t", n_unexplained=0,
            auc_before=None, auc_after=None, proposals=[], accepted=[],
        )
        # Then: extras 存在且默认 None
        assert hasattr(rec, "extras")
        assert rec.extras is None

    def test_extras_可填入(self):
        # Given
        extras = RoundExtras(cloud_calls=1, dead_end_repeats=2)
        rec = RoundRecord(
            round_no=1, task_id="t", n_unexplained=0,
            auc_before=None, auc_after=None, proposals=[], accepted=[],
            extras=extras,
        )
        # Then
        assert rec.extras is extras
        assert rec.extras.cloud_calls == 1
        assert rec.extras.dead_end_repeats == 2

    def test_as_dict_含_extras_当存在时(self):
        # Given
        extras = RoundExtras(cloud_calls=1, dead_end_repeats=0)
        rec = RoundRecord(
            round_no=1, task_id="t", n_unexplained=0,
            auc_before=None, auc_after=None, proposals=[], accepted=[],
            extras=extras,
        )
        # When
        d = rec.as_dict()
        # Then: dict 含 extras 子段(落 jsonl 审计用)
        assert "extras" in d
        assert d["extras"]["cloud_calls"] == 1

    def test_as_dict_extras_None_时不出现(self):
        # Given: extras=None
        rec = RoundRecord(
            round_no=1, task_id="t", n_unexplained=0,
            auc_before=None, auc_after=None, proposals=[], accepted=[],
        )
        # When
        d = rec.as_dict()
        # Then: extras 键不出现(保持 jsonl 干净,老格式兼容)
        assert "extras" not in d
