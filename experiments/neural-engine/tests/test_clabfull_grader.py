"""CLAB-full 判卷适配测试:synthfull.rules/v1 消费、三类条件 fire、方向与权重。

判卷器是唯一的 ground truth 消费者。适配点:
  1. fire 由判卷侧按 payload 条件自行计算(num 阈值/cat 值集/seq 统计量),
     不抄 world.truth.rule_fired;两者必须一致(机制一致性,不涉及规则内容);
  2. 方向判定用「dev 窗末当期权重」(regime 漂移回放),不是 regime 0 基权重。
"""

from __future__ import annotations

import numpy as np
import pytest

from eval.clabfull_comparison import (
    dev_end_weights,
    rule_fires_from_fullworld,
    rule_fires_from_payload,
)
from synthfull import FullWorld, default_config
from synthfull.rulegen import FeatureView, rules_payload


def _handmade_view(n: int = 200) -> FeatureView:
    rng = np.random.default_rng(0)
    obs = rng.normal(size=(n, 2))
    cats = {
        "device_id": rng.integers(0, 50, n).astype(np.int32),
        "region": rng.integers(0, 10, n).astype(np.int32),
    }
    stats = np.column_stack([
        rng.integers(4, 40, n).astype(np.float64),  # seq_len
        rng.poisson(2.0, n).astype(np.float64),     # paste_count
    ])
    return FeatureView(
        observables=obs, observable_index={"f0": 0, "f1": 1},
        categories=cats, stats=stats, stat_index={"seq_len": 0, "paste_count": 1},
    )


def _handmade_payload() -> dict:
    """手工构造的规则(测试自有内容,与世界生成器无关)。"""
    return {
        "format": "synthfull.rules/v1",
        "pools": ["experience", "heldout"],
        "rules": [
            {"rule_id": "EXP-01", "pool": "experience", "weight": 0.6,
             "text": "", "conditions": [
                 {"kind": "num", "field": "f0", "op": ">",
                  "threshold": 0.5, "values": None}]},
            {"rule_id": "EXP-02", "pool": "experience", "weight": -0.4,
             "text": "", "conditions": [
                 {"kind": "cat", "field": "region", "op": "in",
                  "threshold": None, "values": [2, 5]}]},
            {"rule_id": "HLD-01", "pool": "heldout", "weight": 0.7,
             "text": "", "conditions": [
                 {"kind": "seq", "field": "paste_count", "op": ">",
                  "threshold": 3.0, "values": None},
                 {"kind": "num", "field": "f1", "op": "<",
                  "threshold": -0.2, "values": None}]},
        ],
    }


class TestPayloadFireComputation:
    def test_num_condition_threshold(self) -> None:
        fv = _handmade_view()
        fires = rule_fires_from_payload(_handmade_payload(), fv)
        f0 = fv.observables[:, 0]
        np.testing.assert_array_equal(fires[0].fire, f0 > 0.5)
        assert fires[0].rule_id == "EXP-01"
        assert fires[0].pool == "experience"
        assert fires[0].weight == pytest.approx(0.6)

    def test_cat_condition_value_set(self) -> None:
        fv = _handmade_view()
        fires = rule_fires_from_payload(_handmade_payload(), fv)
        region = fv.categories["region"]
        np.testing.assert_array_equal(fires[1].fire, np.isin(region, [2, 5]))
        assert fires[1].weight == pytest.approx(-0.4)

    def test_seq_condition_and_conjunction(self) -> None:
        fv = _handmade_view()
        fires = rule_fires_from_payload(_handmade_payload(), fv)
        paste = fv.stats[:, 1]
        f1 = fv.observables[:, 1]
        expected = (paste > 3.0) & (f1 < -0.2)
        np.testing.assert_array_equal(fires[2].fire, expected)
        assert fires[2].pool == "heldout"

    def test_format_check(self) -> None:
        fv = _handmade_view()
        bad = {**_handmade_payload(), "format": "synth.rules/v1"}
        with pytest.raises(ValueError, match="format"):
            rule_fires_from_payload(bad, fv)

    def test_weights_override(self) -> None:
        fv = _handmade_view()
        fires = rule_fires_from_payload(
            _handmade_payload(), fv, weights={"EXP-01": -0.9}
        )
        assert fires[0].weight == pytest.approx(-0.9)
        assert fires[1].weight == pytest.approx(-0.4)  # 未覆盖的用基权重


def _small_world(seed: int = 11, switch_prob: float = 0.0,
                 drift: float = 0.3, episodes: int = 8):
    cfg = default_config(seed=seed, n_experience=4, n_heldout=2,
                         switch_prob=switch_prob, drift_rule_fraction=drift,
                         pilot_size=1024)
    world = FullWorld(cfg)
    return world, world.run(episodes, 60)


class TestFullWorldFires:
    def test_payload_fires_match_truth(self) -> None:
        """判卷侧按 payload 重算的 fire 必须与 world.truth 逐比特一致。"""
        world, data = _small_world()
        n = len(data.casebook.case_ids)
        idx = np.arange(n)
        fires = rule_fires_from_fullworld(world, data, idx, dev_episodes=6)
        assert len(fires) == 6
        for j, f in enumerate(fires):
            np.testing.assert_array_equal(
                f.fire, data.truth.rule_fired[idx, j]
            )
            assert f.rule_id == data.truth.rule_ids[j]

    def test_static_world_weights_are_base(self) -> None:
        world, data = _small_world(switch_prob=0.0)
        idx = np.arange(len(data.casebook.case_ids))
        fires = rule_fires_from_fullworld(world, data, idx, dev_episodes=6)
        base = {r.rule_id: r.weight for r in world.rules}
        for f in fires:
            assert f.weight == pytest.approx(base[f.rule_id])

    def test_regime_drift_replay_uses_current_weights(self) -> None:
        """强制每 episode 切换且全规则漂移:dev 窗末权重 ≠ 基权重,且等于
        从 regime 事件流独立回放出的值。"""
        world, data = _small_world(seed=13, switch_prob=1.0, drift=1.0,
                                   episodes=6)
        assert len(data.regimes) >= 2  # 每 ep>0 都切换
        idx = np.arange(len(data.casebook.case_ids))
        fires = rule_fires_from_fullworld(world, data, idx, dev_episodes=4)
        # 独立回放:基权重 + episode <= 3 的 mutation
        expected = {r.rule_id: r.weight for r in world.rules}
        for ev in data.regimes:
            if ev.episode <= 3:
                for m in ev.mutations:
                    expected[m.rule_id] = m.new_weight
        base = {r.rule_id: r.weight for r in world.rules}
        assert any(expected[k] != base[k] for k in base)  # 漂移确实发生
        replayed = dev_end_weights(world, data, dev_episodes=4)
        assert replayed == expected
        for f in fires:
            assert f.weight == pytest.approx(expected[f.rule_id])

    def test_case_idx_subset_alignment(self) -> None:
        world, data = _small_world()
        idx = np.array([0, 5, 17, 42])
        fires = rule_fires_from_fullworld(world, data, idx, dev_episodes=6)
        for j, f in enumerate(fires):
            np.testing.assert_array_equal(
                f.fire, data.truth.rule_fired[idx, j]
            )


class TestRulesPayloadRoundtrip:
    def test_world_payload_is_consumable(self) -> None:
        world, _ = _small_world()
        payload = rules_payload(world.rules)
        assert payload["format"] == "synthfull.rules/v1"
        assert len(payload["rules"]) == 6
