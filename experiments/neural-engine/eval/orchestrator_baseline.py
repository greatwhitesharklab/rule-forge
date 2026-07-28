"""阶段 1.2:baseline 编排质量评估(无编排器 = 现状 selflearn/loop.py)。

跑现有 CLAB 闭环(写死策略),采集编排质量基线指标:
  B_eff  发现效率 = Σ 有效特征 / Σ 云端调用  (跨所有轮聚合)
  B_rep  死路重复率 = Σ 死路重复 / Σ 提案数
  B_feat 总有效特征数(固定轮数内)

这些是阶段 1 证伪判定的 baseline 对照值(DESIGN.md §5.1)。
编排器(阶段 1.4-1.5)要在这三个指标上 beat baseline。

复用 eval/clab_discovery.py 的 world 构建 + loop 跑法,只加 reward 聚合。
不重写基础设施,保持与现有验收实验一致的实验设置。

运行:
  cd experiments/neural-engine
  uv run python -m eval.orchestrator_baseline --rounds 5
"""

from __future__ import annotations

import argparse
import time
from pathlib import Path

import numpy as np

from selflearn import DEFAULT_LGBM_PARAMS, LoopConfig, SelfLearnLoop, StrategyMemory
from selflearn.clab import (
    CLAB_FIELDS,
    CLAB_FIELD_STATEMENTS,
    ClabAutoCloud,
    build_clab_split,
    clab_base_features,
)
from selflearn.metrics import aggregate_metrics
from selflearn.reward import orchestrator_reward
from slots import SlotConfig, SlotService
from synth import SyntheticWorld, default_config

# 与 eval/clab_discovery.py 一致的默认实验设置(小规模,快速跑)
DEFAULT_EPISODES = 60
DEFAULT_DEV_EPISODES = 40
DEFAULT_PER_EPISODE = 200
DEFAULT_ROUNDS = 5
DEFAULT_TOP_K = 20
DEFAULT_TOP_M = 20
DEFAULT_DEV_HOLDOUT = 3


def _build_loop(seed: int, out_dir: Path) -> SelfLearnLoop:
    """构建 baseline loop(与 clab_discovery 一致的设置)。"""
    world = SyntheticWorld(default_config(seed=seed))
    data = world.run(DEFAULT_EPISODES, DEFAULT_PER_EPISODE)
    split = build_clab_split(data, dev_episodes=DEFAULT_DEV_EPISODES)

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
    cloud = ClabAutoCloud(feature_df, labels, seed=seed)

    loop_cfg = LoopConfig(
        dev_start="000", dev_end=f"{DEFAULT_DEV_EPISODES - 1:03d}",
        eval_start=f"{DEFAULT_DEV_EPISODES:03d}",
        eval_end=f"{DEFAULT_EPISODES - 1:03d}",
        top_k=DEFAULT_TOP_K, max_features_per_round=DEFAULT_TOP_M,
        dev_holdout_episodes=DEFAULT_DEV_HOLDOUT,
        lgbm_params=dict(DEFAULT_LGBM_PARAMS), seed=seed,
    )
    return SelfLearnLoop(split.dev_df, config=loop_cfg,
                         base_features=clab_base_features,
                         cloud=cloud, memory=memory)


def evaluate_baseline(rounds: int, seed: int, out_dir: Path) -> dict:
    """跑 baseline loop,采集编排质量指标 + 每轮 reward。

    返回 dict 含 metrics(B_eff/B_rep/B_feat)+ 每轮 reward 明细。
    指标聚合用 selflearn.metrics.aggregate_metrics(baseline 和编排器共用)。
    """
    loop = _build_loop(seed, out_dir)
    records = loop.run(rounds)
    loop.memory.service.persist()

    # 跨轮指标聚合(baseline 和编排器用同一套代码,保证对比公平)
    metrics = aggregate_metrics(records)

    # 每轮 reward 明细(训练监控 + 审计用)
    per_round_rewards = []
    for rec in records:
        if rec.extras is None:
            from selflearn.types import RoundExtras
            rec.extras = RoundExtras(cloud_calls=1, dead_end_repeats=0)
        br = orchestrator_reward(rec, rec.extras)
        per_round_rewards.append({
            "round": rec.round_no,
            "reward_a": round(br.reward_a, 4),
            "reward_b": round(br.reward_b, 4),
            "total": round(br.total, 4),
            "features_passed": sum(1 for p in rec.proposals if p.verdict == "pass"),
            "dead_end_repeats": rec.extras.dead_end_repeats,
            "n_proposals": len(rec.proposals),
        })

    return {
        "metrics": metrics.as_dict(),
        "per_round": per_round_rewards,
        "config": {
            "rounds": rounds, "seed": seed,
            "episodes": DEFAULT_EPISODES,
            "dev_episodes": DEFAULT_DEV_EPISODES,
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="阶段 1.2 baseline 编排质量评估")
    parser.add_argument("--rounds", type=int, default=DEFAULT_ROUNDS)
    parser.add_argument("--seed", type=int, default=20260728)
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-orchestrator/baseline"))
    args = parser.parse_args()

    t0 = time.time()
    print(f">>> baseline 编排质量评估(rounds={args.rounds}, seed={args.seed})")
    result = evaluate_baseline(args.rounds, args.seed, args.out)
    elapsed = time.time() - t0

    m = result["metrics"]
    print(f"\n=== baseline 编排质量(seed={args.seed}, {elapsed:.1f}s) ===")
    print(f"  B_eff  发现效率 = {m['b_eff']:.4f}  "
          f"({m['b_feat']} 有效特征 / {m['total_cloud_calls']} 云端调用)")
    print(f"  B_rep  死路重复率 = {m['b_rep']:.4f}  "
          f"({m['total_dead_end_repeats']} 重复 / {m['total_proposals']} 提案)")
    print(f"  B_feat 总有效特征 = {m['b_feat']}")

    print(f"\n=== 每轮 reward 明细 ===")
    for r in result["per_round"]:
        print(f"  round {r['round']}: A={r['reward_a']:+.4f} "
              f"B={r['reward_b']:+.4f} total={r['total']:+.4f}  "
              f"(pass={r['features_passed']}, repeats={r['dead_end_repeats']}, "
              f"proposals={r['n_proposals']})")

    print(f"\n阶段 1.5 证伪阈值(DESIGN.md §5.1):")
    print(f"  编排器要达 B_eff >= {m['b_eff'] * 1.3:.4f}  (baseline × 1.3)")
    print(f"  编排器要达 B_rep <= {m['b_rep']:.4f}  (不高于 baseline)")
    print(f"  编排器要达 B_feat >= {m['b_feat']}  (baseline)")


if __name__ == "__main__":
    main()
