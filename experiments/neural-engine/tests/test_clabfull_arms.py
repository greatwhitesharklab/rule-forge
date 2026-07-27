"""三方臂结构全等测试:同世界、同 tau、同轮数、同判卷口径,唯一变量是云端。

用小世界 + fake 小模型(不加载真实权重)验证实验骨架:
  - 两臂共享同一 RuleGrader(同一 tau 标定结果)与同一 dev 帧;
  - 每臂产出结构一致的 ArmResult(发现轮次表/假阳性/提案数);
  - 我在环臂接口存在但默认不跑(留接口给主 agent)。
"""

from __future__ import annotations

import json

import numpy as np
import pytest

from eval.clabfull_comparison import (
    ArmResult,
    ComparisonConfig,
    run_seed,
)
from selflearn.clabfull import LocalLLMCloud, build_agent_bridge_cloud

_TINY_LGBM = {
    "n_estimators": 20, "num_leaves": 7, "min_child_samples": 10,
    "learning_rate": 0.1, "verbose": -1, "n_jobs": 1,
}


def _cfg(tmp_path, **kw) -> ComparisonConfig:
    base = dict(
        seeds=(13,), episodes=8, per_episode=120, dev_episodes=6,
        rounds=2, top_m=4, llm_max_proposals=2, top_k=10,
        dev_holdout_episodes=2, n_null=20, n_experience=4, n_heldout=2,
        lgbm_params=dict(_TINY_LGBM), out_dir=tmp_path,
    )
    base.update(kw)
    return ComparisonConfig(**base)


def _fake_llm_cloud() -> LocalLLMCloud:
    """结构真实、产出固定的小模型臂(不加载权重)。"""
    payload = json.dumps({"features": [
        {"name": "llm_seq_hi", "expression": "(df.seq_paste_count > 2.0)",
         "rationale": "粘贴多疑似包装"},
        {"name": "llm_freq_hi", "expression": "(df.device_id_freq > 3.0)",
         "rationale": "同设备聚集"},
    ]}, ensure_ascii=False)
    prompts: list[str] = []

    def gen(prompt: str) -> str:
        prompts.append(prompt)
        return payload

    cloud = LocalLLMCloud(gen, max_proposals=2)
    cloud._test_prompts = prompts  # type: ignore[attr-defined]
    return cloud


class TestStructuralEquality:
    def test_arms_share_world_tau_and_rounds(self, tmp_path) -> None:
        out = run_seed(_cfg(tmp_path), seed=13, arms=("enum", "llm"),
                       llm_cloud=_fake_llm_cloud())
        assert out["tau"] > 0
        assert set(out["arms"]) == {"enum", "llm"}
        for arm in out["arms"].values():
            assert isinstance(arm, ArmResult)
            assert len(arm.rounds) == 2  # 同轮数
            # 每条规则都有发现轮次条目(含未发现)
            assert len(arm.discovery_rounds) == 6  # 4 经验 + 2 保留
        # 两臂判卷器同 tau(种子级共享)
        assert out["arms"]["enum"].tau == out["arms"]["llm"].tau == out["tau"]

    def test_arm_result_metrics_shape(self, tmp_path) -> None:
        out = run_seed(_cfg(tmp_path), seed=13, arms=("enum", "llm"),
                       llm_cloud=_fake_llm_cloud())
        for arm in out["arms"].values():
            d = arm.as_dict()
            for key in ("total_proposals", "false_positive_count",
                        "heldout_found", "discovery_rounds", "rounds"):
                assert key in d
            assert d["total_proposals"] > 0
            assert 0 <= d["heldout_found"] <= 2  # n_heldout=2

    def test_llm_arm_actually_called_llm(self, tmp_path) -> None:
        cloud = _fake_llm_cloud()
        out = run_seed(_cfg(tmp_path), seed=13, arms=("llm",),
                       llm_cloud=cloud)
        assert out["arms"]["llm"].total_proposals > 0
        assert len(cloud._test_prompts) == 2  # 每轮一次调用  # type: ignore[attr-defined]

    def test_enum_arm_is_deterministic(self, tmp_path) -> None:
        a = run_seed(_cfg(tmp_path / "a"), seed=13, arms=("enum",))
        b = run_seed(_cfg(tmp_path / "b"), seed=13, arms=("enum",))
        assert (a["arms"]["enum"].discovery_rounds
                == b["arms"]["enum"].discovery_rounds)
        assert (a["arms"]["enum"].total_proposals
                == b["arms"]["enum"].total_proposals)


class TestBridgeArmInterface:
    def test_bridge_factory_exists(self, tmp_path) -> None:
        from cloud.agent_bridge import AgentBridgeProvider

        cloud = build_agent_bridge_cloud(bridge_dir=tmp_path / "bridge")
        assert isinstance(cloud, AgentBridgeProvider)
        # 接口留位:默认臂集合不含 bridge(主 agent 后续手动接入)
        assert "bridge" not in ComparisonConfig().arms
