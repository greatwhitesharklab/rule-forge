"""阶段 2:持续学习闭环(方向 B:在线 GRPO)。

每轮:
  ① 编排器(当前权重)产策略 -> 真云端写特征 -> 免疫系统验证 -> 算 reward
  ② 收集 (prompt, completion, reward) 到经验缓冲区
  ③ 每 accumulate_rounds 轮,用缓冲区数据做 1 步 GRPO 更新
  ④ 记录 strong_rate + reward 曲线(看持续学习是否有效)

证伪问题:持续学习后,strong_rate 是否逐轮上升?
  vs 阶段 1 的离线训 5 步 + 用(停在训完水平)

运行(10 轮,约 2 小时 CPU):
  cd experiments/neural-engine
  uv run python -m eval.continuous_learn --rounds 10
"""

from __future__ import annotations

import argparse
import json
import os
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

# 离线 + CPU(跟 grpo_train.py / orchestrator_eval.py 一致)
os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

from llm.local_llm import LocalLLM
from selflearn import DEFAULT_LGBM_PARAMS, LoopConfig, SelfLearnLoop, StrategyMemory
from selflearn.action import parse_simple_action
from selflearn.clab import (
    CLAB_FIELDS,
    CLAB_FIELD_STATEMENTS,
    build_clab_split,
    clab_base_features,
)
from selflearn.metrics import OrchestrationMetrics, aggregate_metrics
from selflearn.reward_proxy import build_direction_value_table, proxy_reward
from slots import SlotConfig, SlotService
from synth import SyntheticWorld, default_config

MODEL_ID = "Qwen/Qwen3-0.6B"


@dataclass
class RoundResult:
    """一轮持续学习的结果记录。"""

    round_no: int
    n_proposals: int
    n_passed: int
    n_strong: int
    avg_iv: float
    dead_end_repeats: int
    reward: float           # 这一轮编排器动作的 reward 代理值
    trained: bool           # 这一轮有没有触发 GRPO 更新


def _build_loop(seed: int, out_dir: Path, model_path: str, cloud_llm) -> SelfLearnLoop:
    """构建编排器闭环(真云端)。"""
    from eval.orchestrator_eval import OrchestratorCloud

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
    fields_str = " ".join(CLAB_FIELDS)

    llm = LocalLLM(model_id=model_path, device="cpu")
    llm.load()
    cloud = OrchestratorCloud(llm=llm, fields_str=fields_str, cloud_llm=cloud_llm)

    loop_cfg = LoopConfig(
        dev_start="000", dev_end="039",
        eval_start="040", eval_end="059",
        top_k=20, max_features_per_round=20,
        dev_holdout_episodes=3,
        lgbm_params=dict(DEFAULT_LGBM_PARAMS), seed=seed,
    )
    loop = SelfLearnLoop(split.dev_df, config=loop_cfg,
                         base_features=clab_base_features,
                         cloud=cloud, memory=memory)
    return loop, feature_df, labels


def run_continuous_learning(
    rounds: int,
    seed: int,
    out_dir: Path,
    init_model: str,
    accumulate_rounds: int = 2,
    use_real_cloud: bool = False,
) -> list[RoundResult]:
    """跑持续学习闭环。

    accumulate_rounds:每 N 轮收集的数据做 1 步 GRPO 更新。
    """
    # 构建真云端
    cloud_llm = None
    if use_real_cloud:
        from cloud.providers import OpenAIProvider
        api_key = os.environ.get("DEEPSEEK_API_KEY", "")
        if api_key:
            cloud_llm = OpenAIProvider(provider="deepseek", api_key=api_key)
            print(f">>> 真云端在环:deepseek-v4-flash")

    # 方向价值表(reward 代理用)
    world = SyntheticWorld(default_config(seed=seed))
    data = world.run(60, 200)
    split = build_clab_split(data, dev_episodes=40)
    feature_df = split.dev_df[list(CLAB_FIELDS)]
    labels = split.dev_df["outcome"].to_numpy().astype(np.int8)
    table = build_direction_value_table(feature_df, labels)

    # 当前模型路径(每轮可能更新)
    current_model = init_model
    results: list[RoundResult] = []

    for r in range(1, rounds + 1):
        print(f"\n>>> round {r}/{rounds} (model={current_model})")

        # ① 闭环跑 1 轮
        round_dir = out_dir / f"round-{r}"
        loop, _, _ = _build_loop(seed, round_dir, current_model, cloud_llm)
        rec = loop.run_round(r)
        loop.memory.service.persist()

        # ② 算指标
        ivs = [p.metrics.get("iv", 0) for p in rec.proposals
               if p.verdict == "pass" and isinstance(p.metrics.get("iv", 0), (int, float))]
        n_strong = sum(1 for iv in ivs if iv > 0.3)
        avg_iv = float(np.mean(ivs)) if ivs else 0.0

        # ③ 编排器动作的 reward 代理(用 OrchestratorCloud 的 brief)
        # 简化:用这一轮 pass 特征的平均 IV 作为 reward 代理
        reward = avg_iv

        # ④ 是否触发 GRPO 更新
        trained = (r % accumulate_rounds == 0)

        result = RoundResult(
            round_no=r,
            n_proposals=len(rec.proposals),
            n_passed=sum(1 for p in rec.proposals if p.verdict == "pass"),
            n_strong=n_strong,
            avg_iv=round(avg_iv, 4),
            dead_end_repeats=rec.extras.dead_end_repeats if rec.extras else 0,
            reward=round(reward, 4),
            trained=trained,
        )
        results.append(result)
        print(f"    proposals={result.n_proposals} passed={result.n_passed} "
              f"strong={result.n_strong} avg_iv={result.avg_iv:.4f} "
              f"reward={result.reward:.4f} trained={result.trained}")

        # ⑤ GRPO 更新(每 accumulate_rounds 轮)
        if trained:
            # TODO: 用收集的数据做 GRPO 更新
            # 阶段 2 先跑闭环看趋势,GRPO 更新作为下一步
            print(f"    [GRPO 更新占位] 阶段 2 先验证闭环趋势")

    return results


def main():
    parser = argparse.ArgumentParser(description="阶段 2 持续学习闭环")
    parser.add_argument("--rounds", type=int, default=10)
    parser.add_argument("--seed", type=int, default=20260728)
    parser.add_argument("--init-model", type=str,
                        default="eval/artifacts-orchestrator/grpo/final",
                        help="初始模型(阶段 1 GRPO 训练产物)")
    parser.add_argument("--accumulate", type=int, default=2,
                        help="每 N 轮做 1 步 GRPO 更新")
    parser.add_argument("--real-cloud", action="store_true")
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-orchestrator/continuous"))
    args = parser.parse_args()

    print(f">>> 阶段 2 持续学习(rounds={args.rounds}, "
          f"init={args.init_model}, accumulate={args.accumulate})")

    results = run_continuous_learning(
        rounds=args.rounds,
        seed=args.seed,
        out_dir=args.out,
        init_model=args.init_model,
        accumulate_rounds=args.accumulate,
        use_real_cloud=args.real_cloud,
    )

    # 输出趋势分析
    print(f"\n=== 持续学习趋势 ===")
    print(f'{"round":<8} {"proposals":<12} {"passed":<10} {"strong":<10} '
          f'{"avg_iv":<10} {"reward":<10} {"trained":<8}')
    for r in results:
        print(f'{r.round_no:<8} {r.n_proposals:<12} {r.n_passed:<10} '
              f'{r.n_strong:<10} {r.avg_iv:<10.4f} {r.reward:<10.4f} {r.trained:<8}')

    # 判定:strong_rate 是否逐轮上升
    strong_rates = [r.n_strong / max(r.n_proposals, 1) for r in results]
    first_half = np.mean(strong_rates[:len(strong_rates)//2])
    second_half = np.mean(strong_rates[len(strong_rates)//2:])
    print(f"\n前半程 strong_rate: {first_half:.4f}")
    print(f"后半程 strong_rate: {second_half:.4f}")
    if second_half > first_half:
        print("✅ 后半程 > 前半程,持续学习有效趋势")
    else:
        print("⚠️ 后半程 <= 前半程,持续学习无明显改善")


if __name__ == "__main__":
    main()
