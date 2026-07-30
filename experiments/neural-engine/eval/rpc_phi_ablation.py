"""RPC Paper 1:Φ 消融实验。

固定 D 组配置(Domain PM + Session PM),变化 Φ(组合函数):
  Φ-Add(Level 0):base + delta(无门控)
  Φ-Gate(Level 1):base + g × delta(层级门控)
  Φ-Attn(Level 2):base + Attn(Q,K,V)
  Φ-MLP(Level 3):base + MLP([base,delta])

加消融:
  Global Gate:全局标量门控(vs 层级门控)
  No Session:只 Domain PM(无 TTT,验证 Session 贡献)

运行:
  cd experiments/neural-engine
  uv run python -m eval.rpc_phi_ablation --real-cloud
"""

from __future__ import annotations

import argparse
import os
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

from llm.local_llm import LocalLLM
from selflearn import DEFAULT_LGBM_PARAMS, LoopConfig, SelfLearnLoop, StrategyMemory
from selflearn.clab import CLAB_FIELDS, CLAB_FIELD_STATEMENTS, build_clab_split, clab_base_features
from selflearn.composer import CompositionConfig, ParameterComposer, PhiType
from selflearn.metrics import aggregate_metrics
from selflearn.ttt import TTTConfig, ttt_step
from slots import SlotConfig, SlotService
from synth import SyntheticWorld, default_config

MODEL_ID = "Qwen/Qwen3-0.6B"
DOMAIN_MODEL = "eval/artifacts-orchestrator/grpo/final"


@dataclass
class PhiResult:
    """一组 Φ 消融的结果。"""
    name: str
    phi_type: str
    strong_rate: float
    b_quality: float
    b_strong: int
    b_feat: int
    total_proposals: int
    avg_iv_first_half: float
    avg_iv_second_half: float


def _build_loop(seed: int, out_dir: Path, llm, cloud_llm=None):
    """构建 CLAB 闭环。"""
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

    fields_str = " ".join(CLAB_FIELDS)
    from eval.orchestrator_eval import OrchestratorCloud
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
    return loop


def run_phi_experiment(
    name: str,
    phi_type: PhiType,
    rounds: int,
    seed: int,
    out_dir: Path,
    model_path: str,
    cloud_llm=None,
    do_ttt: bool = True,
) -> PhiResult:
    """跑一组 Φ 实验。

    do_ttt=False 时跳过 Session PM 更迁(消融:No Session)。
    """
    print(f"\n{'='*60}")
    print(f"[{name}] Φ={phi_type.value}, model={model_path}")
    print(f"{'='*60}")

    llm = LocalLLM(model_id=model_path, device="cpu")
    llm.load()

    # TTT 设置(如果需要)
    optimizer = None
    ttt_config = TTTConfig(steps=3, lr=1e-4, lora_r=8)
    if do_ttt:
        from selflearn.ttt import setup_ttt_lora
        optimizer = setup_ttt_lora(llm._model, ttt_config)

    avg_ivs = []
    all_records = []

    # 跑两遍:第一遍 TTT 更新,第二遍收集 records(不带 TTT)
    # 第一遍:TTT 更新模型
    loop1 = _build_loop(seed, out_dir / f"{name}_train", llm, cloud_llm)
    for r in range(1, rounds + 1):
        rec = loop1.run_round(r)
        ivs = [p.metrics.get("iv", 0) for p in rec.proposals
               if p.verdict == "pass" and isinstance(p.metrics.get("iv", 0), (int, float))]
        avg_iv = float(np.mean(ivs)) if ivs else 0.0
        avg_ivs.append(avg_iv)
        print(f"  [{name}] round {r}: avg_iv={avg_iv:.4f}")

        if do_ttt and optimizer is not None and loop1.cloud.last_prompt:
            ttt_step(
                llm._model, llm._tokenizer, optimizer,
                prompt=loop1.cloud.last_prompt,
                completion=loop1.cloud.last_completion or "GBDT income_volatility",
                reward=avg_iv,
                config=ttt_config,
            )
    loop1.memory.service.persist()

    # 第二遍:收集 records(用 TTT 更新后的模型)
    loop2 = _build_loop(seed, out_dir / f"{name}_eval", llm, cloud_llm)
    all_records = loop2.run(rounds)

    metrics = aggregate_metrics(all_records)
    m = metrics.as_dict()

    mid = len(avg_ivs) // 2
    return PhiResult(
        name=name,
        phi_type=phi_type.value,
        strong_rate=m["strong_rate"],
        b_quality=m["b_quality"],
        b_strong=m["b_strong"],
        b_feat=m["b_feat"],
        total_proposals=m["total_proposals"],
        avg_iv_first_half=round(float(np.mean(avg_ivs[:mid])) if mid > 0 else 0, 4),
        avg_iv_second_half=round(float(np.mean(avg_ivs[mid:])) if mid < len(avg_ivs) else 0, 4),
    )


def main():
    parser = argparse.ArgumentParser(description="RPC Φ 消融实验")
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--seed", type=int, default=20260728)
    parser.add_argument("--real-cloud", action="store_true")
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-orchestrator/phi_ablation"))
    args = parser.parse_args()

    cloud_llm = None
    if args.real_cloud:
        from cloud.providers import OpenAIProvider
        api_key = os.environ.get("DEEPSEEK_API_KEY", "")
        if api_key:
            cloud_llm = OpenAIProvider(provider="deepseek", api_key=api_key)
            print(">>> 真云端在环:deepseek-v4-flash")

    results: list[PhiResult] = []

    # 1. Φ-Add(Level 0:无门控)
    results.append(run_phi_experiment(
        "Phi-Add", PhiType.ADD, args.rounds, args.seed,
        args.out, DOMAIN_MODEL, cloud_llm, do_ttt=True))

    # 2. Φ-Gate(Level 1:层级门控,主实验)
    results.append(run_phi_experiment(
        "Phi-Gate", PhiType.GATE_ADD, args.rounds, args.seed,
        args.out, DOMAIN_MODEL, cloud_llm, do_ttt=True))

    # 3. Φ-Attn(Level 2:Attention)
    results.append(run_phi_experiment(
        "Phi-Attn", PhiType.ATTENTION, args.rounds, args.seed,
        args.out, DOMAIN_MODEL, cloud_llm, do_ttt=True))

    # 4. Φ-MLP(Level 3:Tiny MLP)
    results.append(run_phi_experiment(
        "Phi-MLP", PhiType.MLP, args.rounds, args.seed,
        args.out, DOMAIN_MODEL, cloud_llm, do_ttt=True))

    # 5. 消融:No Session(只 Domain PM,无 TTT)
    results.append(run_phi_experiment(
        "No-Session", PhiType.GATE_ADD, args.rounds, args.seed,
        args.out, DOMAIN_MODEL, cloud_llm, do_ttt=False))

    # 输出对比
    print("\n" + "=" * 70)
    print("RPC Φ 消融实验结果")
    print("=" * 70)
    print(f'{"组":<14} {"Φ":<12} {"strong_rate":<14} {"b_quality":<12} '
          f'{"b_strong":<10} {"前半程":<10} {"后半程":<10}')
    print("-" * 82)
    for r in results:
        trend = "↑" if r.avg_iv_second_half > r.avg_iv_first_half else "↓"
        print(f'{r.name:<14} {r.phi_type:<12} {r.strong_rate:<14.4f} '
              f'{r.b_quality:<12.4f} {r.b_strong:<10} '
              f'{r.avg_iv_first_half:<10.4f} {r.avg_iv_second_half:<10.4f}{trend}')

    # 排名
    print(f"\n=== strong_rate 排名 ===")
    ranked = sorted(results, key=lambda r: -r.strong_rate)
    for i, r in enumerate(ranked):
        print(f"  {i+1}. {r.name}({r.phi_type}): {r.strong_rate:.4f}")


if __name__ == "__main__":
    main()
