"""RPC Paper 1: multi-seed rerun of Experiment 1 with confidence intervals.

Addresses Limitation 3 (single-seed). Reruns groups A/B/C/D over multiple
seeds and reports mean + 95% CI (t-distribution) for strong_rate, b_quality
and b_strong, plus the per-seed D/A advantage ratio and its CI. D's advantage
is declared robust when the CI lower bound of the D/A strong_rate ratio
exceeds 1.0.

Cloud dependency: the original Exp1 used the real DeepSeek cloud. When
DEEPSEEK_API_KEY is unavailable this script uses the deterministic fallback
enumeration inside OrchestratorCloud (mock cloud). Absolute metric values are
then NOT comparable to the paper; only relative group trends are meaningful.
TTT updates for groups C/D are kept active under the mock path (they depend
on the orchestrator prompt/completion pair, not on the cloud provider).

Run:
  cd experiments/neural-engine
  uv run python -m eval.rpc_multiseed --rounds 5
"""

from __future__ import annotations

import argparse
import json
import os
import time
from dataclasses import asdict
from pathlib import Path
from typing import Sequence

import numpy as np

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

from eval.rpc_paper1 import ExperimentResult, _run_group

BASE_MODEL = "Qwen/Qwen3-0.6B"
DOMAIN_MODEL = "eval/artifacts-orchestrator/grpo/final"

MODEL_BY_GROUP = {
    "A": BASE_MODEL,
    "B": DOMAIN_MODEL,
    "C": BASE_MODEL,
    "D": DOMAIN_MODEL,
}

METRICS = ("strong_rate", "b_quality", "b_strong")

MOCK_DISCLAIMER = (
    "Multi-seed results are based on the deterministic mock cloud "
    "(OrchestratorCloud fallback enumeration, no DEEPSEEK_API_KEY). "
    "Absolute values are NOT comparable to the paper; relative trends "
    "between groups remain valid."
)


def ci95(values: Sequence[float]) -> dict:
    """Mean and 95% confidence interval (t-distribution, n-1 dof).

    Returns {"mean", "ci_lo", "ci_hi", "n", "values"}; empty input yields
    None stats (all runs of a group may be cloud-invalidated).
    """
    vals = [float(v) for v in values]
    n = len(vals)
    if n == 0:
        return {"mean": None, "ci_lo": None, "ci_hi": None, "n": 0,
                "values": []}
    mean = float(np.mean(vals))
    if n < 2:
        return {"mean": mean, "ci_lo": mean, "ci_hi": mean, "n": n,
                "values": vals}
    from scipy import stats

    sem = float(np.std(vals, ddof=1) / np.sqrt(n))
    t_crit = float(stats.t.ppf(0.975, df=n - 1))
    return {
        "mean": mean,
        "ci_lo": mean - t_crit * sem,
        "ci_hi": mean + t_crit * sem,
        "n": n,
        "values": vals,
    }


def summarize_runs(results: list[ExperimentResult]) -> dict:
    """Aggregate per-group metric stats and the D-vs-A advantage ratios.

    Invalid runs (real-cloud failure, valid=False) are EXCLUDED from all
    statistics and reported separately — never mixed into CIs.
    """
    valid = [r for r in results if r.valid]
    invalid = [r for r in results if not r.valid]
    groups = sorted({r.group for r in valid})
    summary: dict[str, dict] = {}
    for g in groups:
        rs = [r for r in valid if r.group == g]
        summary[g] = {m: ci95([getattr(r, m) for r in rs]) for m in METRICS}

    ratios: dict[str, dict] = {}
    a_by_seed = {r.seed: r for r in valid if r.group == "A"}
    d_by_seed = {r.seed: r for r in valid if r.group == "D"}
    common_seeds = sorted(set(a_by_seed) & set(d_by_seed))
    for m in METRICS:
        per_seed = []
        for s in common_seeds:
            denom = getattr(a_by_seed[s], m)
            per_seed.append(getattr(d_by_seed[s], m) / denom if denom else 0.0)
        stat = ci95(per_seed)
        stat["per_seed_ratios"] = per_seed
        stat["seeds"] = common_seeds
        ratios[m] = stat

    sr = ratios["strong_rate"]
    robust = bool(sr["ci_lo"] is not None and sr["ci_lo"] > 1.0)
    return {
        "summary": summary,
        "d_vs_a_ratio": ratios,
        "d_advantage_robust": robust,
        "n_valid": len(valid),
        "n_invalid": len(invalid),
        "invalid_runs": [
            {"group": r.group, "seed": r.seed, "fail_reason": r.fail_reason}
            for r in invalid
        ],
    }


def plot_multiseed(agg: dict, path: Path) -> None:
    """Error-bar chart: strong_rate and b_quality per group (mean + 95% CI)."""
    summary = agg["summary"]
    groups = list(summary.keys())
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.5))
    for ax, metric, title in (
        (axes[0], "strong_rate", "strong_rate (mean + 95% CI)"),
        (axes[1], "b_quality", "b_quality (mean + 95% CI)"),
    ):
        means = [summary[g][metric]["mean"] for g in groups]
        errs = [
            [summary[g][metric]["mean"] - summary[g][metric]["ci_lo"]
             for g in groups],
            [summary[g][metric]["ci_hi"] - summary[g][metric]["mean"]
             for g in groups],
        ]
        ax.bar(groups, means, yerr=errs, capsize=5,
               color=["tab:gray", "tab:blue", "tab:green", "tab:red"][:len(groups)])
        # Overlay individual seed values.
        for i, g in enumerate(groups):
            vals = summary[g][metric]["values"]
            ax.scatter([i] * len(vals), vals, color="black", zorder=3, s=18)
        ax.set_title(title)
        ax.set_xlabel("group")
    fig.tight_layout()
    path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(path, dpi=150)
    plt.close(fig)


def _write_outputs(results: list[ExperimentResult], args: argparse.Namespace,
                   cloud_mode: str, use_real: bool, elapsed: float,
                   partial: bool) -> dict:
    """Aggregate current results and write multiseed.json + multiseed.png.

    Called after every single run (partial=True) so progress survives
    timeouts, and once at the end (partial=False).
    """
    agg = summarize_runs(results)
    report = {
        "cloud_mode": cloud_mode,
        "disclaimer": "" if use_real else MOCK_DISCLAIMER,
        "rounds": args.rounds,
        "seeds": args.seeds,
        "groups": list(args.groups),
        "partial": partial,
        "elapsed_seconds": round(elapsed, 1),
        "per_run": [asdict(r) for r in results],
        **agg,
    }
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "multiseed.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False))
    plot_multiseed(agg, args.out / "multiseed.png")
    return report


def _merge(part_dirs: list[Path], out: Path) -> None:
    """Merge per-run results from several partial output directories."""
    results: list[ExperimentResult] = []
    rounds = 5
    cloud_mode = "real_deepseek"
    for d in part_dirs:
        data = json.loads((d / "multiseed.json").read_text())
        rounds = data.get("rounds", rounds)
        cloud_mode = data.get("cloud_mode", cloud_mode)
        for rd in data["per_run"]:
            results.append(ExperimentResult(**rd))
    args = argparse.Namespace(rounds=rounds,
                              seeds=sorted({r.seed for r in results}),
                              groups="".join(sorted({r.group for r in results})),
                              out=out)
    report = _write_outputs(results, args, cloud_mode,
                            use_real=cloud_mode == "real_deepseek",
                            elapsed=0.0, partial=False)
    _print_summary(report)
    print(f"merged {len(part_dirs)} partial dirs -> {out}")


def _print_summary(report: dict) -> None:
    """Terminal summary: per-group mean [95% CI] and the D/A verdict."""
    agg = report["summary"]
    print("\n=== Multi-seed summary (mean [95% CI]) ===")
    for g, stats in agg.items():
        sr = stats["strong_rate"]
        bq = stats["b_quality"]
        bs = stats["b_strong"]
        if sr["mean"] is None:
            print(f"  {g}: no valid runs")
            continue
        print(f"  {g}: strong_rate {sr['mean']:.4f} "
              f"[{sr['ci_lo']:.4f}, {sr['ci_hi']:.4f}]  "
              f"b_quality {bq['mean']:.4f} [{bq['ci_lo']:.4f}, {bq['ci_hi']:.4f}]  "
              f"b_strong {bs['mean']:.1f} [{bs['ci_lo']:.1f}, {bs['ci_hi']:.1f}]")
    ratio = report["d_vs_a_ratio"]["strong_rate"]
    if ratio["mean"] is None:
        print("\nD/A ratio: no common valid seeds")
    else:
        print(f"\nD/A strong_rate ratio: {ratio['mean']:.3f} "
              f"[{ratio['ci_lo']:.3f}, {ratio['ci_hi']:.3f}] "
              f"(per-seed: {[round(v, 3) for v in ratio['per_seed_ratios']]})")
    print(f"D advantage robust (CI lower bound > 1.0): "
          f"{report['d_advantage_robust']}")
    n_inv = report.get("n_invalid", 0)
    print(f"valid runs: {report.get('n_valid')}, "
          f"cloud-invalidated runs: {n_inv}")
    for inv in report.get("invalid_runs", []):
        print(f"  INVALID {inv['group']}/seed={inv['seed']}: "
              f"{inv['fail_reason'][:120]}")
    if report.get("disclaimer"):
        print(f"NOTE: {report['disclaimer']}")


def main() -> None:
    parser = argparse.ArgumentParser(description="RPC Exp1 multi-seed CI")
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--seeds", type=int, nargs="+",
                        default=[20260728, 20260729, 20260730])
    parser.add_argument("--groups", type=str, default="ABCD")
    parser.add_argument("--real-cloud", action="store_true")
    parser.add_argument("--merge", type=Path, nargs="*", default=None,
                        help="merge partial output dirs instead of running")
    parser.add_argument("--out", type=Path, default=Path("eval/artifacts-rpc"))
    args = parser.parse_args()

    if args.merge is not None:
        _merge(args.merge, args.out)
        return

    use_real = args.real_cloud and bool(os.environ.get("DEEPSEEK_API_KEY"))
    cloud_mode = "real_deepseek" if use_real else "mock_fallback_enumerate"
    if args.real_cloud and not use_real:
        print("WARNING: --real-cloud given but DEEPSEEK_API_KEY missing; "
              "falling back to mock cloud.")
    print(f">>> cloud_mode={cloud_mode}, seeds={args.seeds}, "
          f"groups={args.groups}, rounds={args.rounds}")

    results: list[ExperimentResult] = []
    t0 = time.time()
    for seed in args.seeds:
        for group in args.groups:
            r = _run_group(group, args.rounds, seed,
                           args.out / f"multiseed_work/s{seed}",
                           MODEL_BY_GROUP[group], use_real)
            results.append(r)
            print(f">>> done seed={seed} group={group}: "
                  f"strong_rate={r.strong_rate:.4f}")
            # Persist progress after every run (timeout safety).
            _write_outputs(results, args, cloud_mode, use_real,
                           time.time() - t0, partial=True)
    report = _write_outputs(results, args, cloud_mode, use_real,
                            time.time() - t0, partial=False)
    _print_summary(report)
    print(f"artifacts: {args.out / 'multiseed.json'}, "
          f"{args.out / 'multiseed.png'}")


if __name__ == "__main__":
    main()
