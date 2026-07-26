"""P1 acceptance runner (design doc §5): 100-episode two-arm contrast.

Usage (cwd = experiments/neural-engine):

    uv run python -m eval.p1_acceptance [--episodes 100] [--per-episode 100]
        [--seed 20260726] [--memory-weight 0.2] [--out eval/artifacts]
        [--real-encoder]

Outputs: a four-panel curve figure (N / ZS / L / reputation convergence), a
metrics JSON, and a terminal PASS/FAIL verdict — PASS iff the system arm's
portfolio zero-shot ZS (mean AUC over the first episodes after each regime
switch) beats the frozen RAG arm with a bootstrap 95% CI above zero.
Exit code: 0 on PASS, 1 on FAIL.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

from eval.curves import (
    bootstrap_ci,
    decision_profit,
    dividend_curve,
    zero_shot_auc,
)
from eval.harness import ExperimentConfig, ExperimentResult, run_experiment


def _per_episode(res: ExperimentResult, arm: str):
    """episode -> (final proba, decisions, true labels) arrays."""
    proba, labels, decisions, profit = {}, {}, {}, {}
    out = res.world.ledger.outcome
    for rec in res.arms[arm].episodes:
        proba[rec.episode] = rec.final_proba
        decisions[rec.episode] = rec.decisions
        labels[rec.episode] = out[rec.case_ids].astype(np.float64)
        profit[rec.episode] = float(
            decision_profit(rec.decisions, labels[rec.episode]).sum()
        )
    return proba, labels, decisions, profit


def _diagnostics(res: ExperimentResult, arm: str) -> dict:
    """Decision and profit decomposition for post-hoc analysis."""
    out = res.world.ledger.outcome
    dec_all = np.concatenate([r.decisions for r in res.arms[arm].episodes])
    lab_all = np.concatenate(
        [out[r.case_ids].astype(np.float64) for r in res.arms[arm].episodes]
    )
    bad, good = lab_all == 1.0, lab_all == 0.0
    hits = sum(r.memory_hits for r in res.arms[arm].episodes)
    return {
        "decisions": {d: int((dec_all == d).sum()) for d in
                      ("approve", "review", "reject")},
        "profit_components": {
            "correct_approve": int(((dec_all == "approve") & good).sum()),
            "wrong_approve": int(((dec_all == "approve") & bad).sum()),
            "correct_reject": int(((dec_all == "reject") & bad).sum()),
            "wrong_reject": int(((dec_all == "reject") & good).sum()),
        },
        "memory_hit_rate": hits / len(dec_all),
    }


def _verdict(zs_sys: np.ndarray, zs_rag: np.ndarray):
    n = min(len(zs_sys), len(zs_rag))
    diffs = zs_sys[:n] - zs_rag[:n]
    mean, lo, hi = bootstrap_ci(diffs)
    passed = bool(n >= 3 and mean > 0.0 and lo > 0.0)
    return passed, {"n_switches": n, "mean_diff": mean, "ci95": [lo, hi],
                    "diffs": diffs.tolist()}


def _plot(res: ExperimentResult, n_curve, zs_sys, zs_rag, verdict, path: Path):
    fig, axes = plt.subplots(2, 2, figsize=(13, 9))
    eps = np.arange(res.config.episodes)

    ax = axes[0, 0]
    ax.plot(eps, n_curve, color="C0")
    ax.axhline(0.0, color="gray", lw=0.8)
    ax.set_title("N(n) dividend: cumulative profit (system - RAG)")
    ax.set_xlabel("episode")

    ax = axes[0, 1]
    x = np.arange(len(zs_sys))
    ax.plot(x, zs_sys, "o-", color="C0", label="system (writable)")
    ax.plot(x, zs_rag, "s--", color="C1", label="RAG (frozen)")
    ax.set_title(
        f"ZS(t) zero-shot AUC per regime switch\n"
        f"mean diff {verdict['mean_diff']:+.4f}, CI95 "
        f"[{verdict['ci95'][0]:+.4f}, {verdict['ci95'][1]:+.4f}]"
    )
    ax.set_xlabel("switch #")
    ax.legend()

    ax = axes[1, 0]
    ax.plot(eps, res.arms["system"].retention, color="C0", label="system (writable)")
    ax.plot(eps, res.arms["rag"].retention, color="C1", ls="--", label="RAG (frozen)")
    ax.axhline(1.0, color="gray", lw=0.8)
    ax.set_title("L(t) retention on fixed regime-0 replay set")
    ax.set_xlabel("night")
    ax.legend()

    ax = axes[1, 1]
    al = res.arms["system"].alignment
    mae = [a.mae for a in al]
    direction = [a.direction for a in al]
    ax.plot(eps[: len(mae)], mae, color="C2", label="MAE |rep_bad - truth|")
    ax.set_ylabel("MAE")
    ax2 = ax.twinx()
    ax2.plot(eps[: len(direction)], direction, color="C3", ls="--",
             label="direction agreement")
    ax2.set_ylabel("agreement rate")
    ax2.set_ylim(0, 1.05)
    ax.set_title("Reputation convergence (system arm)")
    ax.set_xlabel("night")
    lines = ax.get_lines() + ax2.get_lines()
    ax.legend(lines, [l.get_label() for l in lines], loc="best")

    fig.tight_layout()
    fig.savefig(path, dpi=120)
    plt.close(fig)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="P1 acceptance experiment")
    parser.add_argument("--episodes", type=int, default=100)
    parser.add_argument("--per-episode", type=int, default=100)
    parser.add_argument("--seed", type=int, default=20260726)
    parser.add_argument("--memory-weight", type=float, default=0.2)
    parser.add_argument("--out", type=Path, default=Path("eval/artifacts"))
    parser.add_argument("--real-encoder", action="store_true",
                        help="use the real Qwen3 embedder on a 30-episode sanity run")
    args = parser.parse_args(argv)

    cfg = ExperimentConfig(
        episodes=args.episodes,
        per_episode=args.per_episode,
        seed=args.seed,
        memory_weight=args.memory_weight,
    )
    encode_fn = None
    if args.real_encoder:  # optional sanity path; shrink the run
        cfg = ExperimentConfig(
            episodes=min(args.episodes, 30), per_episode=args.per_episode,
            seed=args.seed, memory_weight=args.memory_weight,
        )
        from embed import Embedder

        # Lazy Qwen3 load on first encode; the harness still wraps it in the
        # dedup cache, so repeated canonical texts cost one model call.
        encode_fn = Embedder()._encode

    args.out.mkdir(parents=True, exist_ok=True)
    work = args.out / "work"
    if work.exists():  # slot DBs persist across runs; always start fresh
        import shutil

        shutil.rmtree(work)
    res = run_experiment(cfg, work, encode_fn=encode_fn)

    p_sys, l_sys, _d_sys, profit_sys = _per_episode(res, "system")
    p_rag, _l_rag, _d_rag2, profit_rag = _per_episode(res, "rag")
    n_curve = dividend_curve(
        np.array([profit_sys[e] for e in sorted(profit_sys)]),
        np.array([profit_rag[e] for e in sorted(profit_rag)]),
    )
    # ZS windows must fit inside the run.
    switches = [s for s in res.switch_episodes if s + cfg.zs_window <= cfg.episodes]
    zs_sys = zero_shot_auc(p_sys, l_sys, switches, cfg.zs_window)
    zs_rag = zero_shot_auc(p_rag, l_sys, switches, cfg.zs_window)
    passed, verdict = _verdict(zs_sys, zs_rag)

    write_ops = {"allocate": 0, "reinforce": 0, "compete": 0}
    for rep in res.arms["system"].nightly_reports:
        for k, v in rep.write_ops.items():
            write_ops[k] += v
    metrics = {
        "config": vars(cfg),
        "verdict": "PASS" if passed else "FAIL",
        "zs": {
            "system": zs_sys.tolist(), "rag": zs_rag.tolist(),
            "switches": switches, **verdict,
        },
        "dividend_final": float(n_curve[-1]) if len(n_curve) else None,
        "dividend_curve": np.round(n_curve, 4).tolist(),
        "retention": {
            "system": res.arms["system"].retention,
            "rag": res.arms["rag"].retention,
            "replay_auc_system": res.arms["system"].retention_auc,
            "replay_auc_rag": res.arms["rag"].retention_auc,
        },
        "reputation_alignment": [
            {"mae": a.mae, "direction": a.direction, "n": a.n}
            for a in res.arms["system"].alignment
        ],
        "nightly_totals": {
            "credited": sum(len(r.credited_case_ids)
                            for r in res.arms["system"].nightly_reports),
            "reputation_updates": sum(r.reputation_updates
                                      for r in res.arms["system"].nightly_reports),
            "credit_competes": sum(r.credit_competes
                                   for r in res.arms["system"].nightly_reports),
            "write_ops": write_ops,
            "feature_proposed": sum(r.feature_proposed
                                    for r in res.arms["system"].nightly_reports),
            "feature_passed": sum(r.feature_passed
                                  for r in res.arms["system"].nightly_reports),
        },
        "embed_cache": {"hits": res.embed_cache.hits, "misses": res.embed_cache.misses},
        "diagnostics": {
            "system": _diagnostics(res, "system"),
            "rag": _diagnostics(res, "rag"),
        },
        "runtime_seconds": round(res.runtime_seconds, 2),
    }
    (args.out / "p1_metrics.json").write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2)
    )
    _plot(res, n_curve, zs_sys, zs_rag, verdict, args.out / "p1_curves.png")

    print(f"episodes={cfg.episodes} per_episode={cfg.per_episode} "
          f"switches={len(switches)} runtime={res.runtime_seconds:.1f}s")
    print(f"ZS system={zs_sys.mean():.4f} rag={zs_rag.mean():.4f} "
          f"diff={verdict['mean_diff']:+.4f} "
          f"CI95=[{verdict['ci95'][0]:+.4f},{verdict['ci95'][1]:+.4f}]")
    print(f"N final dividend={metrics['dividend_final']:+.2f} | "
          f"L end system={res.arms['system'].retention[-1]:.3f} "
          f"rag={res.arms['rag'].retention[-1]:.3f}")
    print(f"VERDICT: {'PASS' if passed else 'FAIL'} "
          f"(artifacts in {args.out})")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
