"""阶段 2 方向 A:TTT 持续学习闭环。

跟方向 B(GRPO 增量更新)的区别:
- 不重建 trainer,不 group sampling
- 每轮跑完闭环,用 TTT(reward-weighted SFT)直接更新模型权重
- 单样本更新,不需要攒经验池
- 用 LoRA(只更新 q_proj/v_proj 的低秩矩阵),轻量

运行(8 轮,真云端,每轮 TTT 更新):
  cd experiments/neural-engine
  uv run python -m eval.continuous_ttt --rounds 8
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path

import numpy as np
import torch

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

from llm.local_llm import LocalLLM
from selflearn import DEFAULT_LGBM_PARAMS, LoopConfig, SelfLearnLoop, StrategyMemory
from selflearn.clab import (
    CLAB_FIELDS,
    CLAB_FIELD_STATEMENTS,
    build_clab_split,
    clab_base_features,
)
from slots import SlotConfig, SlotService
from synth import SyntheticWorld, default_config

from eval.continuous_learn import RoundResult


def run_ttt_continuous(
    rounds: int,
    seed: int,
    out_dir: Path,
    init_model: str,
    use_real_cloud: bool = False,
    ttt_steps: int = 3,
) -> list[RoundResult]:
    """跑 TTT 持续学习闭环。

    每轮:闭环跑 1 轮 -> 算指标 -> TTT 更新(单样本,reward-weighted SFT)。
    """
    from eval.orchestrator_eval import OrchestratorCloud
    from selflearn.ttt import TTTConfig, ttt_step, setup_ttt_lora

    # 构建真云端
    cloud_llm = None
    if use_real_cloud:
        from cloud.providers import OpenAIProvider
        api_key = os.environ.get("DEEPSEEK_API_KEY", "")
        if api_key:
            cloud_llm = OpenAIProvider(provider="deepseek", api_key=api_key)
            print(">>> 真云端在环:deepseek-v4-flash")

    fields_str = " ".join(CLAB_FIELDS)
    results: list[RoundResult] = []

    # 加载初始模型 + 挂 LoRA(只挂一次,后续复用)
    print(f">>> 加载模型 {init_model} + 挂 LoRA...")
    llm = LocalLLM(model_id=init_model, device="cpu")
    llm.load()
    model = llm._model
    tokenizer = llm._tokenizer

    ttt_config = TTTConfig(steps=ttt_steps, lr=1e-4, lora_r=8)
    optimizer = setup_ttt_lora(model, ttt_config)
    print(">>> LoRA 挂载完成,开始持续学习")

    # 构建闭环用的 world(固定 seed)
    world = SyntheticWorld(default_config(seed=seed))
    data = world.run(60, 200)
    split = build_clab_split(data, dev_episodes=40)

    for r in range(1, rounds + 1):
        print(f"\n>>> round {r}/{rounds}")

        # ① 闭环跑 1 轮(每轮新建 loop,但复用同一个 llm 实例)
        round_dir = out_dir / f"round-{r}"
        round_dir.mkdir(parents=True, exist_ok=True)
        work = round_dir / "work"
        if work.exists():
            import shutil
            shutil.rmtree(work)
        work.mkdir()

        memory = StrategyMemory(SlotService(work / "slots.db", SlotConfig()))
        memory.init_field_slots(CLAB_FIELD_STATEMENTS)

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
        rec = loop.run_round(r)
        memory.service.persist()

        # ② 算指标
        ivs = [p.metrics.get("iv", 0) for p in rec.proposals
               if p.verdict == "pass" and isinstance(p.metrics.get("iv", 0), (int, float))]
        n_strong = sum(1 for iv in ivs if iv > 0.3)
        avg_iv = float(np.mean(ivs)) if ivs else 0.0

        result = RoundResult(
            round_no=r,
            n_proposals=len(rec.proposals),
            n_passed=sum(1 for p in rec.proposals if p.verdict == "pass"),
            n_strong=n_strong,
            avg_iv=round(avg_iv, 4),
            dead_end_repeats=rec.extras.dead_end_repeats if rec.extras else 0,
            reward=round(avg_iv, 4),
            trained=True,  # TTT 每轮都更新
        )
        results.append(result)
        print(f"    proposals={result.n_proposals} passed={result.n_passed} "
              f"strong={result.n_strong} avg_iv={result.avg_iv:.4f}")

        # ③ TTT 更新(用这一轮真实的 prompt-completion + avg_iv 作为 reward)
        # 从 OrchestratorCloud 拿编排器实际看到的 prompt + 产出的 completion
        ttt_prompt = cloud.last_prompt
        ttt_completion = cloud.last_completion
        if ttt_prompt is None or ttt_completion is None:
            # 兜底:无编排器 llm 时(不该走到这,但防御)
            print(f"    [TTT] 跳过(无 prompt/completion)")
            continue

        metrics = ttt_step(
            model, tokenizer, optimizer,
            prompt=ttt_prompt,
            completion=ttt_completion,
            reward=avg_iv,  # 正 reward 强化好动作
            config=ttt_config,
        )
        print(f"    [TTT] sft_loss={metrics['sft_loss']:.4f} "
              f"weighted={metrics['weighted_loss']:.4f} "
              f"completion={ttt_completion[:40]!r}")

    # 存最终模型
    final_path = str(out_dir / "final")
    if hasattr(model, "save_pretrained"):
        model.save_pretrained(final_path)
        tokenizer.save_pretrained(final_path)
        print(f"\n>>> 最终模型存到 {final_path}")

    return results


def main():
    parser = argparse.ArgumentParser(description="阶段 2 方向 A TTT 持续学习")
    parser.add_argument("--rounds", type=int, default=8)
    parser.add_argument("--seed", type=int, default=20260728)
    parser.add_argument("--init-model", type=str,
                        default="eval/artifacts-orchestrator/grpo/final")
    parser.add_argument("--ttt-steps", type=int, default=3,
                        help="每次 TTT 更新的步数")
    parser.add_argument("--real-cloud", action="store_true")
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-orchestrator/ttt"))
    args = parser.parse_args()

    print(f">>> TTT 持续学习(rounds={args.rounds}, steps={args.ttt_steps})")
    results = run_ttt_continuous(
        rounds=args.rounds, seed=args.seed, out_dir=args.out,
        init_model=args.init_model, use_real_cloud=args.real_cloud,
        ttt_steps=args.ttt_steps,
    )

    print(f"\n=== TTT 持续学习趋势 ===")
    print(f'{"round":<8} {"proposals":<12} {"passed":<10} {"strong":<10} '
          f'{"avg_iv":<10} {"reward":<10}')
    for r in results:
        print(f'{r.round_no:<8} {r.n_proposals:<12} {r.n_passed:<10} '
              f'{r.n_strong:<10} {r.avg_iv:<10.4f} {r.reward:<10.4f}')

    avg_ivs = [r.avg_iv for r in results]
    first_half = np.mean(avg_ivs[:len(avg_ivs)//2])
    second_half = np.mean(avg_ivs[len(avg_ivs)//2:])
    print(f"\n前半程 avg_iv: {first_half:.4f}")
    print(f"后半程 avg_iv: {second_half:.4f}")
    if second_half > first_half:
        print("✅ 后半程 > 前半程,TTT 持续学习有效")
    else:
        print("⚠️ 后半程 <= 前半程,无明显改善")


if __name__ == "__main__":
    main()
