"""机制二:策略记忆组件测试(死路跳过、regime 经验重提、声誉退役)。

MemoryPolicy 是臂 A 的唯一记忆状态;ArmCloud 共用预排序候选表,
policy=None 时退化为臂 B 的盲枚举(顺序与候选表严格一致)。
"""

from __future__ import annotations

from cloud.contracts import TaskPackage, validate_result
from eval.clab_memory_ab import ArmCloud, MemoryPolicy


def _cand(name: str, direction: str) -> dict:
    return {"name": name, "expression": "df.x", "rationale": "r",
            "direction": direction, "kind": "single", "prior_iv": 0.0}


def _ranked() -> list[dict]:
    # d1 两个变体 + d2 一个 + d3 两个变体
    return [
        _cand("c1", "single:a:>"), _cand("c2", "single:b:>"),
        _cand("c3", "single:a:>"), _cand("c4", "single:c:>"),
        _cand("c5", "single:c:>"),
    ]


def _task(m: int = 2, task_id: str = "selflearn-r01") -> TaskPackage:
    return TaskPackage(
        task_id=task_id, task_type="feature_proposal",
        context={"case_profiles": [], "existing_features": [], "dead_ends": []},
        constraints={"max_features": m, "must_be_executable": "x",
                     "no_future_info": True},
        output_schema={"type": "object"},
    )


class TestMemoryPolicy:
    def test_dead_direction_excluded_from_repropose(self) -> None:
        pol = MemoryPolicy()
        pol.record_regime_iv("single:a:>", 0, 0.3)
        pol.add_dead("single:a:>", "区分度不足")
        assert "single:a:>" in pol.dead
        assert pol.repropose_directions(1) == []

    def test_repropose_sorted_by_past_iv_and_gated_by_bar(self) -> None:
        pol = MemoryPolicy(iv_bar=0.1)
        pol.record_regime_iv("d_low", 0, 0.05)   # 低于门槛
        pol.record_regime_iv("d_mid", 0, 0.20)
        pol.record_regime_iv("d_high", 1, 0.30)
        assert pol.repropose_directions(2) == ["d_high", "d_mid"]

    def test_repropose_respects_seen_in_current_regime(self) -> None:
        pol = MemoryPolicy(iv_bar=0.1)
        pol.record_regime_iv("d1", 0, 0.3)
        pol.record_regime_iv("d2", 0, 0.2)
        pol.mark_proposed("d1", 1)
        assert pol.repropose_directions(1) == ["d2"]
        # 换一个 regime,d1 尚未验证,重新可被优先
        assert pol.repropose_directions(2) == ["d1", "d2"]

    def test_repropose_caps_at_p(self) -> None:
        pol = MemoryPolicy(iv_bar=0.1, repropose_p=2)
        for i in range(5):
            pol.record_regime_iv(f"d{i}", 0, 0.1 + 0.01 * i)
        assert len(pol.repropose_directions(1)) == 2

    def test_retire_after_k_consecutive_low_iv(self) -> None:
        pol = MemoryPolicy(retire_k=3, iv_bar=0.1)
        assert pol.track_feature("f", 0.05) is False
        assert pol.track_feature("f", 0.04) is False
        assert pol.track_feature("f", 0.03) is True  # 第 3 连低 → 退役

    def test_streak_resets_on_recovery(self) -> None:
        pol = MemoryPolicy(retire_k=3, iv_bar=0.1)
        assert pol.track_feature("f", 0.05) is False
        assert pol.track_feature("f", 0.20) is False  # 回升,清零
        assert pol.track_feature("f", 0.05) is False
        assert pol.track_feature("f", 0.05) is False
        assert pol.track_feature("f", 0.05) is True


class TestArmCloud:
    def test_blind_arm_follows_ranked_order(self) -> None:
        cloud = ArmCloud(_ranked(), policy=None)
        r1 = cloud.execute(_task(m=2))
        r2 = cloud.execute(_task(m=2, task_id="selflearn-r02"))
        names = [f["name"] for f in r1.content["features"]] + \
                [f["name"] for f in r2.content["features"]]
        assert names == ["c1", "c2", "c3", "c4"]
        validate_result("feature_proposal", r1.content)

    def test_memory_arm_skips_dead_directions(self) -> None:
        pol = MemoryPolicy()
        cloud = ArmCloud(_ranked(), policy=pol)
        r1 = cloud.execute(_task(m=2))
        assert [f["name"] for f in r1.content["features"]] == ["c1", "c2"]
        # c1 验证失败 → 方向 single:a:> 进死路;下一轮其变体 c3 必须被跳过
        pol.add_dead("single:a:>", "区分度不足")
        r2 = cloud.execute(_task(m=2, task_id="selflearn-r02"))
        assert [f["name"] for f in r2.content["features"]] == ["c4", "c5"]

    def test_memory_arm_never_reproposes(self) -> None:
        pol = MemoryPolicy()
        cloud = ArmCloud(_ranked(), policy=pol)
        seen: list[str] = []
        for r in range(1, 5):
            res = cloud.execute(_task(m=2, task_id=f"selflearn-r{r:02d}"))
            seen.extend(f["name"] for f in res.content["features"])
        assert len(seen) == len(set(seen)) == 5  # 穷尽且不重复

    def test_regime_reproposal_front_loads_deep_candidate(self) -> None:
        pol = MemoryPolicy(iv_bar=0.1)
        # c5 的方向在历史 regime IV 高且当前 regime 未验证 → 优先重提到队首
        pol.record_regime_iv("single:c:>", 0, 0.4)
        cloud = ArmCloud(_ranked(), policy=pol)
        cloud.current_regime = 1
        res = cloud.execute(_task(m=2))
        assert res.content["features"][0]["name"] == "c4"  # c5 方向的第一个未提变体
        # 同 regime 已提过该方向,下一轮回退到顺序枚举
        res2 = cloud.execute(_task(m=3, task_id="selflearn-r02"))
        assert res2.content["features"][0]["name"] != "c5"

    def test_execute_contract_valid(self) -> None:
        cloud = ArmCloud(_ranked(), policy=MemoryPolicy())
        res = cloud.execute(_task(m=3))
        validate_result("feature_proposal", res.content)
        assert res.provenance.provider == "clab-auto"
