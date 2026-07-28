"""阶段 1.5:编排器闭环评估(真云端 deepseek-v4-flash)。

对比 baseline(写死策略 ClabAutoCloud 暴力枚举)vs 编排器(0.6B 产策略 ->
cloud_brief -> 真云端写特征),跑完整闭环,算真 B_eff/B_rep/B_feat。

关键改动(2026-07 阶段 1 证伪后修正):
  旧版:编排器调整 ClabAutoCloud 候选顺序(反而干扰枚举效率 -> 证伪)
  新版:编排器产 cloud_brief -> 真云端(deepseek-v4-flash)写特征表达式
       -> 免疫系统验证。编排器真正决定"探索什么",不是调整确定性枚举器。

证伪标准(DESIGN.md §5.1):
  B_eff  发现效率:编排器 >= baseline × 1.3
  B_rep  死路重复率:编排器 <= baseline
  B_feat 总有效特征:编排器 >= baseline

运行(需 GRPO 训练后的模型 + DEEPSEEK_API_KEY):
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


class OrchestratorCloud:
    """编排器驱动的真云端:编排器产 cloud_brief -> 真云端写特征。

    新版(2026-07 证伪后修正):编排器不再调整枚举器顺序(旧版证伪),
    而是生成 cloud_brief -> 真云端(deepseek-v4-flash)写特征表达式。
    编排器真正决定"探索什么方向",云端负责"怎么写特征代码"。

    云端产出过免疫系统验证(sandbox + IV/lift + 泄漏检测),只有通过的
    才算有效特征。
    """

    provider_name = "orchestrator"
    model_name = "orchestrator-v0"

    def __init__(
        self,
        llm: LocalLLM | None,
        fields_str: str,
        cloud_llm=None,  # OpenAIProvider 或 MockProvider
    ) -> None:
        self.llm = llm                    # 本地 0.6B(编排器,产 cloud_brief)
        self.fields_str = fields_str
        self.cloud_llm = cloud_llm        # 真云端(写特征表达式)
        self._call_count = 0

    def _orchestrator_brief(self) -> str:
        """编排器(0.6B)产 cloud_brief:探索方向摘要。"""
        if self.llm is None:
            return "找有区分度的新特征"  # 无编排器 = 泛化出题
        prompt = (
            f"信贷审批编排。可用字段:{self.fields_str}。\n"
            f"残余信号:savings_months(d=0.35),months_employed(d=0.22)。\n"
            f"工具:GBDT(黑箱准)/CART(可解释)。\n"
            f"示例:CART savings_months debt_to_income\n"
            f"示例:GBDT income_volatility\n"
            f"输出一个动作(工具名 + 探索字段,空格分隔,只一行):"
        )
        raw = self.llm.generate(prompt, max_new_tokens=32, temperature=0.3, top_p=0.9)
        from selflearn.action import parse_simple_action, action_to_cloud_brief
        action = parse_simple_action(raw)
        if action is None:
            return "找有区分度的新特征"
        return action_to_cloud_brief(action)

    def execute(self, task: Any) -> Any:
        """编排器产 brief -> 真云端写特征 -> 返 TaskResult。

        task 是 SelfLearnLoop 构造的 feature_proposal TaskPackage,含 context
        (case_profiles/existing_features/dead_ends/regime_stats/importance_top/
        residual_signals)。我们把编排器的 brief 注入 context,让云端聚焦。
        """
        from cloud.contracts import Provenance, TaskResult
        from datetime import datetime, timezone

        self._call_count += 1

        # 编排器产 brief
        brief = self._orchestrator_brief()

        if self.cloud_llm is not None:
            # 真云端:注入 brief 到 task context,调真云端
            # task.context 已有 case_profiles 等,我们追加 orchestrator_brief
            if hasattr(task, "context") and isinstance(task.context, dict):
                task.context["orchestrator_brief"] = brief
            result = self.cloud_llm.execute(task)
            return result

        # 无云端(兜底):用 brief 生成简单特征(ClabAutoCloud 风格)
        # 这个分支只在没配 API key 时走,不是主路径
        from selflearn.clab import ClabAutoCloud
        return self._fallback_enumerate(task, brief)

    def _fallback_enumerate(self, task: Any, brief: str) -> Any:
        """无真云端时的兜底:根据 brief 提到的字段枚举特征。"""
        from cloud.contracts import Provenance, TaskResult
        from datetime import datetime, timezone
        from selflearn.clab import CLAB_FIELDS

        # 从 brief 提取字段名
        mentioned = [f for f in CLAB_FIELDS if f in brief or f.replace("_obs", "") in brief]
        if not mentioned:
            mentioned = list(CLAB_FIELDS)[:3]

        # 生成简单特征(单字段分箱 + 乘积交互)
        features = []
        for f in mentioned[:2]:
            features.append({
                "name": f"orch_{f}_high",
                "expression": f"(df.{f} > df.{f}.median())",
                "rationale": f"{f} 高尾探索(编排器引导)",
            })
        if len(mentioned) >= 2:
            a, b = mentioned[0], mentioned[1]
            features.append({
                "name": f"orch_{a}_{b}_prod",
                "expression": f"df.{a} * df.{b}",
                "rationale": f"{a} 与 {b} 乘积交互(编排器引导)",
            })

        return TaskResult(
            task_id=task.task_id, task_type=task.task_type,
            content={"features": features[:5]},
            provenance=Provenance(
                provider=self.provider_name, model=self.model_name,
                model_version="fallback-v1",
                timestamp=datetime.now(timezone.utc).isoformat(),
                prompt_hash="", cost_tokens=0,
            ),
        )


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
    use_real_cloud: bool = False,
) -> dict:
    """跑编排器驱动的闭环,采集真 B_eff/B_rep/B_feat。

    use_real_cloud=True:编排器产 brief -> 真云端(deepseek-v4-flash)写特征
    use_real_cloud=False:编排器产 brief -> 兜底枚举(无 API key 时)
    """
    fields_str = " ".join(CLAB_FIELDS)

    # 构建真云端(如果要用)
    cloud_llm = None
    if use_real_cloud:
        import os
        from cloud.providers import OpenAIProvider
        api_key = os.environ.get("DEEPSEEK_API_KEY", "")
        if not api_key:
            print("⚠️  DEEPSEEK_API_KEY 未设,退回兜底枚举")
        else:
            cloud_llm = OpenAIProvider(provider="deepseek", api_key=api_key)
            print(f">>> 真云端在环:deepseek-v4-flash")

    def cloud_factory(feature_df, labels):
        llm = None
        if model_path:
            llm = LocalLLM(model_id=model_path, device="cpu")
            llm.load()
        return OrchestratorCloud(llm=llm, fields_str=fields_str, cloud_llm=cloud_llm)

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
    parser.add_argument("--real-cloud", action="store_true",
                        help="用真云端(deepseek-v4-flash),需 DEEPSEEK_API_KEY")
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-orchestrator/orch_eval"))
    args = parser.parse_args()

    model_path = args.model or MODEL_ID
    print(f">>> 编排器闭环评估(rounds={args.rounds}, model={model_path}, "
          f"real_cloud={args.real_cloud})")
    result = evaluate_orchestrator(args.rounds, args.seed, args.out,
                                   model_path, use_real_cloud=args.real_cloud)

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
