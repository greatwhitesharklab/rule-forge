"""阶段 1.5:编排器闭环评估(完整执行 + 真 reward A+B)。

对比 baseline(写死策略 ClabAutoCloud)vs 编排器(0.6B 产策略调整候选顺序),
跑完整闭环(GbdT 指路 + 云端出题 + 免疫系统验证 + 入库/死路),
算真 B_eff/B_rep/B_feat,跟 baseline 对比。

证伪标准(DESIGN.md §5.1):
  B_eff  发现效率:编排器 >= baseline × 1.3
  B_rep  死路重复率:编排器 <= baseline
  B_feat 总有效特征:编排器 >= baseline

运行(需 GRPO 训练后的模型):
  cd experiments/neural-engine
  uv run python -m eval.orchestrator_eval --rounds 5
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

# 离线 + CPU(跟 grpo_train.py 一致)
os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

from llm.local_llm import LocalLLM
from selflearn import DEFAULT_LGBM_PARAMS, LoopConfig, SelfLearnLoop, StrategyMemory
from selflearn.action import parse_simple_action
from selflearn.clab import (
    CLAB_FIELDS,
    CLAB_FIELD_STATEMENTS,
    ClabAutoCloud,
    build_clab_split,
    clab_base_features,
)
from selflearn.metrics import OrchestrationMetrics, aggregate_metrics
from slots import SlotConfig, SlotService
from synth import SyntheticWorld, default_config

MODEL_ID = "Qwen/Qwen3-0.6B"


class OrchestratorSteeredCloud:
    """编排器引导的云端:编排器产策略 -> 调整 ClabAutoCloud 的候选顺序。

    最小侵入接法:不重写 ClabAutoCloud,而是在它的候选列表里,
    把编排器选的字段相关的候选排到前面(优先出题)。

    如果编排器模型路径给定,用模型 generate;否则用固定策略(few-shot baseline)。
    """

    provider_name = "orchestrator-steered"
    model_name = "orchestrator-v0"

    def __init__(
        self,
        base_cloud: ClabAutoCloud,
        llm: LocalLLM | None = None,
        fields_str: str = "",
    ) -> None:
        self.base_cloud = base_cloud
        self.llm = llm
        self.fields_str = fields_str
        self._steered: bool = False

    def _orchestrator_prompt(self, importance_top: list[dict] | None = None,
                             dead_ends: list[str] | None = None) -> str:
        """简化版语境 prompt(跟 grpo_train.py 一致)。"""
        return (
            f"信贷审批编排。可用字段:{self.fields_str}。\n"
            f"残余信号:savings_months(d=0.35),months_employed(d=0.22)。\n"
            f"工具:GBDT(黑箱准)/CART(可解释)。\n"
            f"示例:CART savings_months debt_to_income\n"
            f"示例:GBDT income_volatility\n"
            f"输出一个动作(工具名 + 探索字段,空格分隔,只一行):"
        )

    def _steer_candidates(self, action_keywords: tuple[str, ...]) -> None:
        """根据编排器选的字段,调整候选顺序(选的字段相关的排前面)。"""
        if not action_keywords:
            return
        # 把候选里含编排器字段的排前面
        def relevance(cand: dict) -> int:
            expr = cand.get("expression", "")
            return sum(1 for kw in action_keywords if kw in expr)

        self.base_cloud.candidates.sort(
            key=lambda c: (-relevance(c), -c.get("prior_iv", 0))
        )
        self.base_cloud._cursor = 0  # 重置游标,从头吐
        self._steered = True

    def execute(self, task: Any) -> Any:
        """编排器产策略 -> 调整候选顺序 -> 委托 ClabAutoCloud 出题。"""
        # 编排器产策略(如果有 LLM)
        if self.llm is not None:
            prompt = self._orchestrator_prompt()
            raw = self.llm.generate(prompt, max_new_tokens=32, temperature=0.3, top_p=0.9)
            action = parse_simple_action(raw)
            if action is not None:
                self._steer_candidates(action.direction_keywords)
        # 委托 ClabAutoCloud 出题(候选顺序可能已被编排器调整)
        return self.base_cloud.execute(task)


def _build_loop(seed: int, out_dir: Path, cloud_factory) -> SelfLearnLoop:
    """构建 loop(用给定的 cloud 工厂)。"""
    world = SyntheticWorld(default_config(seed=seed))
    data = world.run(60, 200)
    split = build_clab_split(data, dev_episodes=40)

    work = out_dir / "work"
    if work.exists():
        import shutil
        shutil.rmtree(work)
    work.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)

    memory = StrategyMemory(SlotService(work / "slots.db", SlotConfig()))
    memory.init_field_slots(CLAB_FIELD_STATEMENTS)

    feature_df = split.dev_df[list(CLAB_FIELDS)]
    labels = split.dev_df["outcome"].to_numpy().astype(np.int8)
    cloud = cloud_factory(feature_df, labels)

    loop_cfg = LoopConfig(
        dev_start="000", dev_end="039",
        eval_start="040", eval_end="059",
        top_k=20, max_features_per_round=20,
        dev_holdout_episodes=3,
        lgbm_params=dict(DEFAULT_LGBM_PARAMS), seed=seed,
    )
    return SelfLearnLoop(split.dev_df, config=loop_cfg,
                         base_features=clab_base_features,
                         cloud=cloud, memory=memory)


def evaluate_orchestrator(
    rounds: int,
    seed: int,
    out_dir: Path,
    model_path: str | None = None,
) -> dict:
    """跑编排器引导的闭环,采集真 B_eff/B_rep/B_feat。"""
    fields_str = " ".join(CLAB_FIELDS)

    # 构建编排器引导的 cloud
    def cloud_factory(feature_df, labels):
        base = ClabAutoCloud(feature_df, labels, seed=seed)
        llm = None
        if model_path:
            llm = LocalLLM(model_id=model_path, device="cpu")
            llm.load()
        return OrchestratorSteeredCloud(base, llm=llm, fields_str=fields_str)

    loop = _build_loop(seed, out_dir, cloud_factory)
    records = loop.run(rounds)
    loop.memory.service.persist()

    metrics = aggregate_metrics(records)
    return {"metrics": metrics.as_dict(), "n_rounds": rounds, "seed": seed}


def main() -> None:
    parser = argparse.ArgumentParser(description="阶段 1.5 编排器闭环评估")
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--seed", type=int, default=20260728)
    parser.add_argument("--model", type=str, default=None,
                        help="GRPO 训练后的模型路径(不给则用 base 0.6B)")
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-orchestrator/orch_eval"))
    args = parser.parse_args()

    model_path = args.model or MODEL_ID
    print(f">>> 编排器闭环评估(rounds={args.rounds}, model={model_path})")
    result = evaluate_orchestrator(args.rounds, args.seed, args.out, model_path)

    m = result["metrics"]
    print(f"\n=== 编排器指标 ===")
    print(f"  B_eff  发现效率 = {m['b_eff']:.4f}")
    print(f"  B_rep  死路重复率 = {m['b_rep']:.4f}")
    print(f"  B_feat 总有效特征 = {m['b_feat']}")

    # baseline 对照(从 orchestrator_baseline 的实测值)
    baseline = OrchestrationMetrics(
        b_eff=16.6, b_rep=0.0, b_feat=83,
        total_cloud_calls=5, total_proposals=100,
        total_dead_end_repeats=0, n_rounds=5,
    )
    orch_metrics = OrchestrationMetrics(
        b_eff=m["b_eff"], b_rep=m["b_rep"], b_feat=m["b_feat"],
        total_cloud_calls=m["total_cloud_calls"],
        total_proposals=m["total_proposals"],
        total_dead_end_repeats=m["total_dead_end_repeats"],
        n_rounds=result["n_rounds"],
    )
    verdict = orch_metrics.beats(baseline, eff_factor=1.3)
    print(f"\n=== 证伪判定(vs baseline B_eff=16.6/B_rep=0/B_feat=83) ===")
    print(f"  passed: {verdict.passed}")
    for k, v in verdict.failed.items():
        print(f"  FAILED {k}: {v}")
    if verdict.passed:
        print("  ✅ 编排器 beat baseline,范式成立!")


if __name__ == "__main__":
    main()
