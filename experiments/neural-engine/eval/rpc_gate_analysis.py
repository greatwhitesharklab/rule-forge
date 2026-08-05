"""RPC Paper 1: per-layer gate analysis (tests the Section 6.2 hypothesis).

Section 6.2 claims a layer-wise division of labor among per-layer gates:
  lower layers (1-10)  -> favor Base    (all gates low)
  middle layers (11-20) -> favor Domain (domain gate high)
  upper layers (21-28) -> favor Session (session gate high)

This script trains a Group-D-style configuration (Domain model as base +
Session PM via reward-weighted TTT through the Gated ParameterComposer),
records the full [n_layers x 3] gate matrix after every round, and checks
whether the measured gate pattern matches the narrative.

Honesty note: in the original Exp1 runs (eval/rpc_paper1.py) the composer
was never instantiated, so no trained gates existed before this script.

Run:
  cd experiments/neural-engine
  uv run python -m eval.rpc_gate_analysis --rounds 5
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

import numpy as np
import torch

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

from llm.local_llm import LocalLLM
from selflearn.composer import CompositionConfig, ParameterComposer, PhiType
from selflearn.ttt import TTTConfig, ttt_step

from eval.rpc_paper1 import _build_loop

DOMAIN_MODEL = "eval/artifacts-orchestrator/grpo/final"

# Layer segments from the Section 6.2 narrative (0-based, end-exclusive).
SEGMENTS = {"low": (0, 10), "mid": (10, 20), "high": (20, 28)}
PM_COLUMNS = ("domain", "user", "session")

# Deviation from the 0.5 init beyond which a gate counts as "moved".
MOVED_THRESHOLD = 0.05


def segment_means(gates: np.ndarray) -> dict[str, dict[str, float]]:
    """Mean gate value per segment per PM column.

    gates: [n_layers, 3] array of sigmoid-ed gate values.
    Returns {segment: {pm_column: mean}}.
    """
    out: dict[str, dict[str, float]] = {}
    n = gates.shape[0]
    for seg, (lo, hi) in SEGMENTS.items():
        # Clamp to valid range so models with fewer layers still work.
        lo_c = min(lo, n - 1)
        hi_c = max(min(hi, n), lo_c + 1)
        block = gates[lo_c:hi_c]
        out[seg] = {
            col: round(float(block[:, i].mean()), 4)
            for i, col in enumerate(PM_COLUMNS)
        }
    return out


def evaluate_hypothesis(final_gates: np.ndarray) -> dict:
    """Check the Section 6.2 layer-division narrative against final gates.

    Criteria:
    - specialization: at least one gate deviates from the 0.5 init by more
      than MOVED_THRESHOLD (otherwise gates collapsed at init).
    - narrative: domain column peaks in the mid segment, session column
      peaks in the high segment, and the low segment has the lowest
      overall gate level (Base favored).
    """
    seg = segment_means(final_gates)
    max_dev = float(np.abs(final_gates - 0.5).max())
    specialization = max_dev > MOVED_THRESHOLD

    domain_by_seg = [seg[s]["domain"] for s in ("low", "mid", "high")]
    session_by_seg = [seg[s]["session"] for s in ("low", "mid", "high")]
    overall_by_seg = [
        float(np.mean([seg[s][c] for c in PM_COLUMNS]))
        for s in ("low", "mid", "high")
    ]
    narrative = (
        domain_by_seg.index(max(domain_by_seg)) == 1
        and session_by_seg.index(max(session_by_seg)) == 2
        and overall_by_seg.index(min(overall_by_seg)) == 0
    )

    return {
        "segment_means": seg,
        "max_abs_deviation_from_init": round(max_dev, 6),
        "specialization_detected": specialization,
        "narrative_match": bool(narrative and specialization),
        "domain_by_segment_low_mid_high": domain_by_seg,
        "session_by_segment_low_mid_high": session_by_seg,
        "overall_by_segment_low_mid_high": [round(v, 4) for v in overall_by_seg],
    }


def summarize_history(history: list[np.ndarray]) -> dict:
    """Build the JSON report from a list of per-round gate snapshots."""
    final = history[-1]
    report = evaluate_hypothesis(final)
    report["n_snapshots"] = len(history)
    report["n_layers"] = int(final.shape[0])
    # Per-column drift between init and final.
    drift = np.abs(history[-1] - history[0])
    report["max_drift_over_training"] = round(float(drift.max()), 6)
    flat = drift.reshape(drift.shape[0], -1)
    report["n_gates_moved_gt_0.01"] = int((flat > 0.01).sum())
    report["n_gates_total"] = int(flat.size)
    # Drift-from-init per snapshot: shows WHEN gates start moving.
    drift_series = [round(float(np.abs(h - history[0]).max()), 6)
                    for h in history]
    report["drift_series_per_snapshot"] = drift_series
    moved = [i for i, d in enumerate(drift_series) if d > 0.01]
    report["first_snapshot_drift_gt_0.01"] = moved[0] if moved else None
    return report


def plot_gate_history(history: list[np.ndarray], path: Path) -> None:
    """Two panels: final per-layer gate values + segment-mean evolution."""
    final = history[-1]
    layers = np.arange(1, final.shape[0] + 1)
    rounds = np.arange(0, len(history))

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(13, 5))

    colors = {"domain": "tab:blue", "user": "tab:green", "session": "tab:red"}
    for i, col in enumerate(PM_COLUMNS):
        ax1.plot(layers, final[:, i], marker="o", ms=3, label=col,
                 color=colors[col])
    for boundary in (10.5, 20.5):
        ax1.axvline(boundary, color="gray", ls="--", lw=0.8)
    ax1.axhline(0.5, color="black", ls=":", lw=0.8, label="init (0.5)")
    ax1.set_xlabel("layer")
    ax1.set_ylabel("gate value (sigmoid)")
    ax1.set_title("Final per-layer gates")
    ax1.set_ylim(0, 1)
    ax1.legend(fontsize=8)

    # Evolution: segment means per round, one line per (segment, column).
    for i, col in enumerate(PM_COLUMNS):
        for seg_name in SEGMENTS:
            series = [segment_means(h)[seg_name][col] for h in history]
            ax2.plot(rounds, series, color=colors[col],
                     ls={"low": ":", "mid": "--", "high": "-"}[seg_name],
                     label=f"{col}/{seg_name}")
    ax2.set_xlabel("snapshot (0 = init, k = after round k)")
    ax2.set_ylabel("segment-mean gate value")
    ax2.set_title("Gate evolution over training")
    ax2.set_ylim(0, 1)
    ax2.legend(fontsize=6, ncol=3)

    fig.tight_layout()
    path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(path, dpi=150)
    plt.close(fig)


def run_gate_experiment(
    rounds: int,
    seed: int,
    out_dir: Path,
    model_path: str = DOMAIN_MODEL,
    cloud_llm=None,
) -> dict:
    """Train Group-D-style gated composition and record gate history.

    The composer wraps the (frozen) domain model; the TTT optimizer updates
    the Session LoRA plus all per-layer gates jointly. Gate snapshots are
    taken at init and after each round's TTT update.
    """
    print(f">>> [gate-analysis] loading {model_path}")
    llm = LocalLLM(model_id=model_path, device="cpu")
    llm.load()

    n_layers = int(getattr(llm._model.config, "num_hidden_layers", 28))
    composer = ParameterComposer(
        llm._model,
        CompositionConfig(n_layers=n_layers, phi_type=PhiType.GATE_ADD),
    )
    composer.init_session_lora(r=8)
    ttt_config = TTTConfig(steps=3, lr=1e-4, lora_r=8)
    optimizer = torch.optim.AdamW(composer.get_trainable_parameters(),
                                  lr=ttt_config.lr)

    loop = _build_loop(seed, out_dir / "work", llm, cloud_llm)

    history: list[np.ndarray] = [composer.get_gate_values().numpy().copy()]
    ttt_metrics: list[dict] = []
    for r in range(1, rounds + 1):
        rec = loop.run_round(r)
        ivs = [p.metrics.get("iv", 0) for p in rec.proposals
               if p.verdict == "pass"
               and isinstance(p.metrics.get("iv", 0), (int, float))]
        avg_iv = float(np.mean(ivs)) if ivs else 0.0
        print(f"  [gate-analysis] round {r}: avg_iv={avg_iv:.4f}")

        if loop.cloud.last_prompt:
            m = ttt_step(
                composer, llm._tokenizer, optimizer,
                prompt=loop.cloud.last_prompt,
                completion=loop.cloud.last_completion
                or "GBDT income_volatility",
                reward=avg_iv,
                config=ttt_config,
            )
            m["round"] = r
            m["avg_iv"] = round(avg_iv, 4)
            ttt_metrics.append(m)
        history.append(composer.get_gate_values().numpy().copy())

    loop.memory.service.persist()

    report = summarize_history(history)
    report["rounds"] = rounds
    report["seed"] = seed
    report["model_path"] = model_path
    report["ttt_metrics"] = ttt_metrics
    report["final_gates"] = np.round(history[-1], 4).tolist()
    return report, history


def main() -> None:
    parser = argparse.ArgumentParser(description="RPC per-layer gate analysis")
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--seed", type=int, default=20260728)
    parser.add_argument("--model", type=str, default=DOMAIN_MODEL)
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-rpc"))
    args = parser.parse_args()

    report, history = run_gate_experiment(
        args.rounds, args.seed, args.out / "gate_analysis_work", args.model)

    json_path = args.out / "gate_analysis.json"
    png_path = args.out / "gate_values.png"
    args.out.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(report, indent=2, ensure_ascii=False))
    plot_gate_history(history, png_path)

    print("\n=== Section 6.2 hypothesis check ===")
    print(json.dumps({k: v for k, v in report.items()
                      if k not in ("final_gates", "ttt_metrics")},
                     indent=2, ensure_ascii=False))
    print(f"artifacts: {json_path}, {png_path}")


if __name__ == "__main__":
    main()
