"""RPC Paper 1: wired per-layer gating, 30-round retrain (Group D config).

Retrains the gated composition with the TRUE per-layer wiring
(ParameterComposer.wire): every transformer layer's q_proj/v_proj output
becomes ``linear(x) + g_D,l * domain_delta(x) + g_S,l * session_lora(x)``,
so all domain/session gates sit in the computation graph. The domain PM is
reconstructed as a dense weight delta (GRPO final minus Qwen3-0.6B base);
the session PM is the trainable per-layer LoRA. Gates are zero-initialized
(sigma(0) = 0.5), i.e. the wired model starts at the additive average.

Question: with a real gradient path, do the gates SPECIALIZE over 30 rounds
of reward-weighted TTT (Section 3.3 lives) or stay flat (additive
composition is simply optimal — a clean negative result)?

An additive baseline (merged domain model + peft TTT, the original Exp1-D
configuration) is run with the same seed/rounds for strong_rate comparison.
Cloud is the deterministic fallback enumeration (no API key needed); the
gate-dynamics question is about the gradient path, not the cloud.

Run:
  cd experiments/neural-engine
  uv run python -m eval.rpc_gate_wired --rounds 30
"""

from __future__ import annotations

import argparse
import json
import os
from dataclasses import asdict
from pathlib import Path

import numpy as np
import torch

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

from llm.local_llm import LocalLLM
from selflearn.composer import (
    CompositionConfig,
    ParameterComposer,
    PhiType,
    compute_weight_deltas,
)
from selflearn.metrics import aggregate_metrics
from selflearn.ttt import TTTConfig, ttt_step

from eval.rpc_gate_analysis import (
    evaluate_hypothesis,
    plot_gate_history,
    summarize_history,
)
from eval.rpc_paper1 import _build_loop, _run_group

BASE_MODEL = "Qwen/Qwen3-0.6B"
DOMAIN_MODEL = "eval/artifacts-orchestrator/grpo/final"


def _base_safetensors_path() -> str:
    """Local HF cache path of the base model's safetensors (offline)."""
    from huggingface_hub import snapshot_download

    snap = snapshot_download(BASE_MODEL, local_files_only=True)
    return str(Path(snap) / "model.safetensors")


def run_wired(rounds: int, seed: int, out_dir: Path) -> dict:
    """Train the wired gated composer for `rounds` rounds of TTT.

    Returns the gate-dynamics report plus eval-pass metrics.
    """
    print(f">>> [wired] loading base {BASE_MODEL}")
    llm = LocalLLM(model_id=BASE_MODEL, device="cpu")
    llm.load()

    print(f">>> [wired] computing dense domain deltas vs {DOMAIN_MODEL}")
    deltas = compute_weight_deltas(_base_safetensors_path(),
                                   str(Path(DOMAIN_MODEL) / "model.safetensors"))
    nonzero = sum(1 for d in deltas.values() if d.abs().max() > 0)
    print(f">>> [wired] {len(deltas)} target modules, {nonzero} with "
          f"non-zero domain delta")

    n_layers = int(getattr(llm._model.config, "num_hidden_layers", 28))
    composer = ParameterComposer(
        llm._model,
        CompositionConfig(n_layers=n_layers, phi_type=PhiType.GATE_ADD),
    )
    composer.init_session_lora(r=8)
    n_hooked = composer.wire(domain_deltas=deltas)
    print(f">>> [wired] {n_hooked} modules hooked "
          f"({n_layers} layers x q/v proj)")

    ttt_config = TTTConfig(steps=3, lr=1e-4, lora_r=8)
    optimizer = torch.optim.AdamW(composer.get_trainable_parameters(),
                                  lr=ttt_config.lr)

    loop = _build_loop(seed, out_dir / "wired_train", llm, None)
    history: list[np.ndarray] = [composer.get_gate_values().numpy().copy()]
    ttt_metrics: list[dict] = []
    for r in range(1, rounds + 1):
        rec = loop.run_round(r)
        ivs = [p.metrics.get("iv", 0) for p in rec.proposals
               if p.verdict == "pass"
               and isinstance(p.metrics.get("iv", 0), (int, float))]
        avg_iv = float(np.mean(ivs)) if ivs else 0.0
        if loop.cloud.last_prompt:
            m = ttt_step(
                llm._model, llm._tokenizer, optimizer,
                prompt=loop.cloud.last_prompt,
                completion=loop.cloud.last_completion
                or "GBDT income_volatility",
                reward=avg_iv,
                config=ttt_config,
            )
            ttt_metrics.append({"round": r, "avg_iv": round(avg_iv, 4),
                                "sft_loss": round(m.get("sft_loss", 0.0), 4)})
        history.append(composer.get_gate_values().numpy().copy())
        if r % 5 == 0:
            g = history[-1]
            print(f"  [wired] round {r}: avg_iv={avg_iv:.4f} "
                  f"domain_gate_mean={g[:, 0].mean():.4f} "
                  f"session_gate_mean={g[:, 2].mean():.4f}")
    loop.memory.service.persist()

    # Eval pass: fresh loop, no TTT, collect metrics with the trained model.
    loop2 = _build_loop(seed, out_dir / "wired_eval", llm, None)
    records = loop2.run(rounds)
    m = aggregate_metrics(records).as_dict()

    report = summarize_history(history)
    report.update(evaluate_hypothesis(history[-1]))
    # Active columns only (domain=0, session=2; user column has no PM here).
    drift = history[-1] - history[0]
    active_drift = np.abs(drift[:, [0, 2]])
    report["active_columns"] = ["domain", "session"]
    report["n_active_gates"] = int(active_drift.size)
    report["n_active_moved_gt_0.01"] = int((active_drift > 0.01).sum())
    report["max_active_drift"] = round(float(active_drift.max()), 6)
    report["user_column"] = "inactive (no User PM in Group D config)"
    report["metrics"] = {k: m[k] for k in
                         ("strong_rate", "b_quality", "b_strong", "b_feat",
                          "total_proposals")}
    report["ttt_metrics"] = ttt_metrics
    report["final_gates"] = np.round(history[-1], 4).tolist()
    return report, history


def main() -> None:
    parser = argparse.ArgumentParser(description="Wired per-layer gating, "
                                     "30-round retrain vs additive baseline")
    parser.add_argument("--rounds", type=int, default=30)
    parser.add_argument("--seed", type=int, default=20260728)
    parser.add_argument("--skip-baseline", action="store_true")
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-rpc/gate_wired"))
    args = parser.parse_args()

    report, history = run_wired(args.rounds, args.seed, args.out)
    report["rounds"] = args.rounds
    report["seed"] = args.seed
    report["cloud_mode"] = "mock_fallback_enumerate"

    if not args.skip_baseline:
        print("\n>>> [baseline] additive Group D (merged domain + peft TTT)")
        r = _run_group("D", args.rounds, args.seed, args.out / "baseline",
                       DOMAIN_MODEL, False)
        report["baseline_additive"] = asdict(r)
        wm = report["metrics"]
        report["comparison"] = {
            "wired_strong_rate": wm["strong_rate"],
            "baseline_strong_rate": r.strong_rate,
            "wired_minus_baseline": round(wm["strong_rate"] - r.strong_rate, 4),
        }

    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "gate_wired.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False))
    plot_gate_history(history, args.out / "gate_wired.png")

    print("\n=== Wired gating: 30-round verdict ===")
    print(f"active gates moved >0.01: {report['n_active_moved_gt_0.01']}"
          f"/{report['n_active_gates']} "
          f"(max drift {report['max_active_drift']})")
    print(f"first snapshot with drift >0.01: "
          f"{report['first_snapshot_drift_gt_0.01']}")
    print(f"narrative match (Section 6.2): {report['narrative_match']}")
    if "comparison" in report:
        c = report["comparison"]
        print(f"strong_rate: wired {c['wired_strong_rate']:.4f} vs "
              f"additive baseline {c['baseline_strong_rate']:.4f} "
              f"(delta {c['wired_minus_baseline']:+.4f})")
    print(f"artifacts: {args.out / 'gate_wired.json'}, "
          f"{args.out / 'gate_wired.png'}")


if __name__ == "__main__":
    main()
