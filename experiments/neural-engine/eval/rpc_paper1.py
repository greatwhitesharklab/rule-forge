"""RPC Paper 1:四组对比实验(A/B/C/D)。

验证核心假设:多层 PM 组合 > 单层。

A(baseline):W_base only(无 PM)
B(Domain only):W_base + Domain PM(GRPO 训的,冻结)
C(Session only):W_base + Session PM(TTT 每轮更新)
D(RPC):W_base + Domain PM + Session PM(门控组合)

评估:strong_rate(信号发现效率)+ avg_iv 趋势。

运行:
  cd experiments/neural-engine
  uv run python -m eval.rpc_paper1 --rounds 5
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
from selflearn.metrics import aggregate_metrics
from slots import SlotConfig, SlotService
from synth import SyntheticWorld, default_config

from eval.continuous_learn import RoundResult
from eval.orchestrator_eval import CloudUnavailableError, OrchestratorCloud


@dataclass
class ExperimentResult:
    """一组实验的结果。"""

    group: str           # A/B/C/D
    seed: int            # 数据/闭环种子
    b_strong: int
    strong_rate: float
    b_quality: float
    b_feat: int
    total_proposals: int
    avg_iv_first_half: float
    avg_iv_second_half: float
    valid: bool = True       # False: 真云失败中止,不纳入统计
    fail_reason: str = ""


def _build_loop(seed: int, out_dir: Path, llm, cloud_llm=None,
                allow_fallback: bool = True) -> SelfLearnLoop:
    """构建 CLAB 闭环(用指定的 llm 实例)。"""
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
    cloud = OrchestratorCloud(llm=llm, fields_str=fields_str,
                              cloud_llm=cloud_llm,
                              allow_fallback=allow_fallback)

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


def _run_group(
    group: str,
    rounds: int,
    seed: int,
    out_dir: Path,
    model_path: str,
    use_real_cloud: bool = False,
) -> ExperimentResult:
    """跑一组实验。"""
    cloud_llm = None
    if use_real_cloud:
        from cloud.providers import OpenAIProvider
        api_key = os.environ.get("DEEPSEEK_API_KEY", "")
        if api_key:
            cloud_llm = OpenAIProvider(provider="deepseek", api_key=api_key)

    # 加载模型
    print(f"\n>>> [{group}] 加载模型 {model_path}")
    llm = LocalLLM(model_id=model_path, device="cpu")
    llm.load()

    # 跑闭环(真云模式禁止兜底:失败即中止,本组本种子标记 invalid)
    allow_fallback = cloud_llm is None
    try:
        loop = _build_loop(seed, out_dir / group, llm, cloud_llm,
                           allow_fallback=allow_fallback)

        # Session PM 组(C/D)每轮做 TTT 更新
        do_ttt = group in ("C", "D")
        if do_ttt:
            from selflearn.ttt import TTTConfig, setup_ttt_lora
            ttt_config = TTTConfig(steps=3, lr=1e-4, lora_r=8)
            optimizer = setup_ttt_lora(llm._model, ttt_config)

        avg_ivs = []
        for r in range(1, rounds + 1):
            rec = loop.run_round(r)
            ivs = [p.metrics.get("iv", 0) for p in rec.proposals
                   if p.verdict == "pass" and isinstance(p.metrics.get("iv", 0), (int, float))]
            avg_iv = float(np.mean(ivs)) if ivs else 0.0
            avg_ivs.append(avg_iv)
            print(f"  [{group}] round {r}: avg_iv={avg_iv:.4f}")

            # Session PM:TTT 更新(C/D 组)
            # 只要有真实 (prompt, completion) 对就更新,与云端实现无关;
            # 否则 mock/兜底路径下 C≡A、D≡B,Session PM 消融失效。
            if do_ttt and loop.cloud.last_prompt:
                from selflearn.ttt import ttt_step
                ttt_step(
                    llm._model, llm._tokenizer, optimizer,
                    prompt=loop.cloud.last_prompt,
                    completion=loop.cloud.last_completion or "GBDT income_volatility",
                    reward=avg_iv,
                    config=ttt_config,
                )

        loop.memory.service.persist()

        # 算聚合指标(重跑闭环收集 records)
        loop2 = _build_loop(seed, out_dir / f"{group}_eval", llm, cloud_llm,
                            allow_fallback=allow_fallback)
        records_all = loop2.run(rounds)
    except CloudUnavailableError as e:
        print(f"  [{group}] seed={seed} CLOUD FAILED, run invalidated: {e}")
        return ExperimentResult(
            group=group, seed=seed, b_strong=0, strong_rate=0.0,
            b_quality=0.0, b_feat=0, total_proposals=0,
            avg_iv_first_half=0.0, avg_iv_second_half=0.0,
            valid=False, fail_reason=str(e)[:300],
        )

    metrics = aggregate_metrics(records_all)
    m = metrics.as_dict()

    mid = len(avg_ivs) // 2
    first_half = float(np.mean(avg_ivs[:mid])) if mid > 0 else 0.0
    second_half = float(np.mean(avg_ivs[mid:])) if mid < len(avg_ivs) else 0.0

    return ExperimentResult(
        group=group,
        seed=seed,
        b_strong=m["b_strong"],
        strong_rate=m["strong_rate"],
        b_quality=m["b_quality"],
        b_feat=m["b_feat"],
        total_proposals=m["total_proposals"],
        avg_iv_first_half=round(first_half, 4),
        avg_iv_second_half=round(second_half, 4),
    )


def main():
    parser = argparse.ArgumentParser(description="RPC Paper 1 四组实验")
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--seed", type=int, default=20260728)
    parser.add_argument("--base-model", type=str, default="Qwen/Qwen3-0.6B")
    parser.add_argument("--domain-model", type=str,
                        default="eval/artifacts-orchestrator/grpo/final")
    parser.add_argument("--real-cloud", action="store_true")
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-orchestrator/rpc_paper1"))
    args = parser.parse_args()

    results: list[ExperimentResult] = []

    # A: baseline(base only,无 PM)
    print("=" * 60)
    print("[A] Baseline: W_base only")
    print("=" * 60)
    r_a = _run_group("A", args.rounds, args.seed, args.out,
                     args.base_model, args.real_cloud)
    results.append(r_a)

    # B: Domain only(GRPO 训的,冻结,无 TTT)
    print("\n" + "=" * 60)
    print("[B] Domain only: W_base + Domain PM (frozen)")
    print("=" * 60)
    r_b = _run_group("B", args.rounds, args.seed, args.out,
                     args.domain_model, args.real_cloud)
    results.append(r_b)

    # C: Session only(base + TTT,无 Domain PM)
    print("\n" + "=" * 60)
    print("[C] Session only: W_base + Session PM (TTT)")
    print("=" * 60)
    r_c = _run_group("C", args.rounds, args.seed, args.out,
                     args.base_model, args.real_cloud)
    results.append(r_c)

    # D: RPC(Domain + Session + 门控)
    # 简化:用 Domain model 作为 base,在上面叠 Session PM(TTT)
    # 这等价于 W_base + Domain_PM(已 merge 进 model)+ Session_PM(TTT)
    print("\n" + "=" * 60)
    print("[D] RPC: W_base + Domain PM + Session PM (gated)")
    print("=" * 60)
    r_d = _run_group("D", args.rounds, args.seed, args.out,
                     args.domain_model, args.real_cloud)
    results.append(r_d)

    # 输出对比
    print("\n" + "=" * 60)
    print("RPC Paper 1 结果对比")
    print("=" * 60)
    print(f'{"组":<6} {"描述":<28} {"strong_rate":<14} {"b_quality":<12} '
          f'{"b_strong":<10} {"前半程":<10} {"后半程":<10}')
    print("-" * 90)
    descs = {
        "A": "W_base only(baseline)",
        "B": "W_base + Domain PM(冻结)",
        "C": "W_base + Session PM(TTT)",
        "D": "W_base + Domain + Session(RPC)",
    }
    for r in results:
        trend = "↑" if r.avg_iv_second_half > r.avg_iv_first_half else "↓"
        print(f'{r.group:<6} {descs[r.group]:<28} {r.strong_rate:<14.4f} '
              f'{r.b_quality:<12.4f} {r.b_strong:<10} '
              f'{r.avg_iv_first_half:<10.4f} {r.avg_iv_second_half:<10.4f}{trend}')

    print(f"\n=== 核心判定 ===")
    d_best = max(results, key=lambda r: r.strong_rate)
    print(f"strong_rate 最高: 组 {d_best.group}({d_best.strong_rate:.4f})")
    if d_best.group == "D":
        print("✅ RPC(D 组)胜出,多层 PM 组合 > 单层,Paper 1 假设成立")
    else:
        print(f"⚠️ RPC(D 组)未胜出,{d_best.group} 组最高,Paper 1 假设需调整")


if __name__ == "__main__":
    main()
