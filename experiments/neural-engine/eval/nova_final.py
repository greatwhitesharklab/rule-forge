"""Nova 真实数据终考。

在 Nova 真实信贷数据上验证编排器范式:
  baseline:ClabAutoCloud 暴力枚举(不用编排器)
  编排器:0.6B + deepseek-v4-flash(真云端)
比的是信号发现效率(strong_rate = 强特征/总提案)。

运行(需 DEEPSEEK_API_KEY):
  cd experiments/neural-engine
  uv run python -m eval.nova_final --rounds 5 --real-cloud
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path

import numpy as np
import pandas as pd

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

from llm.local_llm import LocalLLM
from selflearn import DEFAULT_LGBM_PARAMS, LoopConfig, SelfLearnLoop, StrategyMemory
from selflearn.clab import ClabAutoCloud
from selflearn.metrics import OrchestrationMetrics, aggregate_metrics
from slots import SlotConfig, SlotService

from lending.nova_adapter import (
    NOVA_FIELDS,
    NOVA_FIELD_STATEMENTS,
    build_nova_split,
    nova_base_features,
)


def _build_loop(seed: int, out_dir: Path, cloud_factory) -> SelfLearnLoop:
    """构建 Nova 数据上的闭环。"""
    split = build_nova_split()

    work = out_dir / "work"
    if work.exists():
        import shutil
        shutil.rmtree(work)
    work.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)

    memory = StrategyMemory(SlotService(work / "slots.db", SlotConfig()))
    memory.init_field_slots(NOVA_FIELD_STATEMENTS)

    feature_df = split.dev_df[list(NOVA_FIELDS)]
    labels = split.dev_df["outcome"].to_numpy().astype(np.int8)
    cloud = cloud_factory(feature_df, labels)

    episodes = sorted(split.dev_df["episode"].unique())
    dev_end = episodes[-1]
    eval_start = f"{int(dev_end) + 1:03d}"  # 虚构 eval(不会用到)

    loop_cfg = LoopConfig(
        dev_start=episodes[0], dev_end=dev_end,
        eval_start=eval_start, eval_end=f"{int(eval_start) + 10:03d}",
        top_k=20, max_features_per_round=20,
        dev_holdout_episodes=1,
        lgbm_params=dict(DEFAULT_LGBM_PARAMS), seed=seed,
    )
    loop = SelfLearnLoop(
        split.dev_df, config=loop_cfg,
        base_features=nova_base_features,
        cloud=cloud, memory=memory,
    )
    return loop, split


def run_baseline(rounds: int, seed: int, out_dir: Path) -> dict:
    """跑 baseline(ClabAutoCloud 暴力枚举)。"""
    def cloud_factory(feature_df, labels):
        return ClabAutoCloud(feature_df, labels, seed=seed)

    loop, _ = _build_loop(seed, out_dir, cloud_factory)
    records = loop.run(rounds)
    loop.memory.service.persist()
    metrics = aggregate_metrics(records)
    return {"metrics": metrics.as_dict(), "n_rounds": rounds}


def run_orchestrator(
    rounds: int, seed: int, out_dir: Path,
    model_path: str, use_real_cloud: bool = False,
) -> dict:
    """跑编排器(0.6B + 真云端)。"""
    from eval.orchestrator_eval import OrchestratorCloud

    cloud_llm = None
    if use_real_cloud:
        from cloud.providers import OpenAIProvider
        api_key = os.environ.get("DEEPSEEK_API_KEY", "")
        if api_key:
            cloud_llm = OpenAIProvider(provider="deepseek", api_key=api_key)
            print(">>> 真云端在环:deepseek-v4-flash")

    fields_str = " ".join(NOVA_FIELDS)

    def cloud_factory(feature_df, labels):
        llm = LocalLLM(model_id=model_path, device="cpu")
        llm.load()
        return OrchestratorCloud(llm=llm, fields_str=fields_str, cloud_llm=cloud_llm)

    loop, _ = _build_loop(seed, out_dir, cloud_factory)
    records = loop.run(rounds)
    loop.memory.service.persist()
    metrics = aggregate_metrics(records)
    return {"metrics": metrics.as_dict(), "n_rounds": rounds}


def main():
    parser = argparse.ArgumentParser(description="Nova 真实数据终考")
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--seed", type=int, default=20260729)
    parser.add_argument("--model", type=str,
                        default="eval/artifacts-orchestrator/grpo/final")
    parser.add_argument("--real-cloud", action="store_true")
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-orchestrator/nova"))
    parser.add_argument("--skip-baseline", action="store_true",
                        help="跳过 baseline(已跑过)")
    args = parser.parse_args()

    # 1. Baseline
    if not args.skip_baseline:
        print(f">>> [1/2] Baseline(ClabAutoCloud,{args.rounds} 轮)...")
        bl = run_baseline(args.rounds, args.seed, args.out / "baseline")
        bl_m = bl["metrics"]
        print(f"  b_eff={bl_m['b_eff']:.2f} b_strong={bl_m['b_strong']} "
              f"strong_rate={bl_m['strong_rate']:.4f} b_quality={bl_m['b_quality']:.4f}")
    else:
        # 用之前的实测值
        bl_m = {"b_eff": 0, "b_strong": 0, "strong_rate": 0, "b_quality": 0,
                "b_rep": 0, "total_proposals": 0, "b_feat": 0}
        print(">>> [1/2] Baseline 跳过")

    # 2. 编排器
    print(f"\n>>> [2/2] 编排器(0.6B + 真云端,{args.rounds} 轮)...")
    orch = run_orchestrator(
        args.rounds, args.seed, args.out / "orchestrator",
        args.model, use_real_cloud=args.real_cloud,
    )
    orch_m = orch["metrics"]
    print(f"  b_eff={orch_m['b_eff']:.2f} b_strong={orch_m['b_strong']} "
          f"strong_rate={orch_m['strong_rate']:.4f} b_quality={orch_m['b_quality']:.4f}")

    # 3. 对比判定
    print(f"\n=== Nova 终考结果 ===")
    print(f'{"指标":<24} {"baseline":<14} {"编排器":<14} {"优势":<10}')
    print("-" * 62)
    if bl_m["strong_rate"] > 0 or orch_m["strong_rate"] > 0:
        ratio = orch_m["strong_rate"] / bl_m["strong_rate"] if bl_m["strong_rate"] > 0 else float("inf")
        print(f'{"strong_rate":<24} {bl_m["strong_rate"]:<14.4f} {orch_m["strong_rate"]:<14.4f} {ratio:.2f}x')
    print(f'{"b_quality(平均IV)":<24} {bl_m["b_quality"]:<14.4f} {orch_m["b_quality"]:<14.4f}')
    print(f'{"b_strong(强特征数)":<24} {bl_m["b_strong"]:<14} {orch_m["b_strong"]:<14}')
    print(f'{"b_feat(总特征)":<24} {bl_m["b_feat"]:<14} {orch_m["b_feat"]:<14}')
    print(f'{"total_proposals":<24} {bl_m["total_proposals"]:<14} {orch_m["total_proposals"]:<14}')
    print(f'{"b_rep(死路重复率)":<24} {bl_m["b_rep"]:<14.4f} {orch_m["b_rep"]:<14.4f}')


if __name__ == "__main__":
    main()
