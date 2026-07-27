"""Demo entry: generate a CLAB-full world and print summary statistics.

Prints ONLY aggregate statistics (condition-type distribution, categorical
cardinality/skew, sequence length profile, bad-rate curve). It never prints
concrete rule conditions, thresholds, value sets, or weights.

Usage (from experiments/neural-engine/):
    uv run python -m synthfull.generate --episodes 100 --per-episode 1000
"""

from __future__ import annotations

import argparse
import time
from collections import Counter

import numpy as np

from synthfull import FullWorld, default_config
from synthfull.rulegen import COND_CAT, COND_NUM, COND_SEQ

_SPARK = "▁▂▃▄▅▆▇█"


def _sparkline(x: np.ndarray) -> str:
    lo, hi = float(x.min()), float(x.max())
    if hi - lo < 1e-12:
        return _SPARK[0] * len(x)
    idx = ((x - lo) / (hi - lo) * (len(_SPARK) - 1)).astype(int)
    return "".join(_SPARK[i] for i in idx)


def _top_share(values: np.ndarray, pool_size: int, fraction: float) -> float:
    counts = np.bincount(values, minlength=pool_size)
    k = max(1, int(round(pool_size * fraction)))
    return float(np.sort(counts)[::-1][:k].sum() / len(values))


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description="CLAB-full synthetic credit world")
    ap.add_argument("--episodes", type=int, default=100)
    ap.add_argument("--per-episode", type=int, default=1000)
    ap.add_argument("--seed", type=int, default=20260726)
    args = ap.parse_args(argv)

    t0 = time.perf_counter()
    world = FullWorld(default_config(seed=args.seed))
    data = world.run(args.episodes, args.per_episode)
    elapsed = time.perf_counter() - t0
    cb, ld, gt = data.casebook, data.ledger, data.truth

    n = len(cb.case_ids)
    print(f"cases: {n} ({args.episodes} episodes x {args.per_episode}) "
          f"in {elapsed:.2f}s ({n / elapsed:,.0f} cases/s)")
    print(f"seed: {args.seed}")
    print(f"overall bad_rate: {ld.outcome.mean():.4f}")
    print(f"regimes: {cb.regime_id.max() + 1} "
          f"({len(data.regimes)} switches, expected ~{args.episodes * 0.1:.0f})")

    # Rule pool shape: condition-type mix and fire-rate profile only.
    kind_counts = Counter(c.kind for r in world.rules for c in r.conditions)
    n_conds = sum(kind_counts.values())
    print(f"\nrule pool: {len(world.rules)} rules "
          f"({len(world.experience_rules)} experience + "
          f"{len(world.rules) - len(world.experience_rules)} held-out), "
          f"{n_conds} conditions")
    for kind, label in ((COND_NUM, "numeric"), (COND_CAT, "categorical"),
                        (COND_SEQ, "sequence")):
        print(f"  {label:<11} conditions: {kind_counts.get(kind, 0):>3} "
              f"({kind_counts.get(kind, 0) / n_conds:.2%})")
    fire = gt.rule_fired.mean(axis=0)
    q = np.quantile(fire, [0.0, 0.25, 0.5, 0.75, 1.0])
    print("  fire-rate quantiles [min/p25/p50/p75/max]: "
          + " ".join(f"{v:.3f}" for v in q))

    # Categorical modality: cardinality and skew.
    print("\ncategoricals (empirical):")
    for spec in data.config.categoricals:
        values = cb.categorical(spec.name)
        uniq = len(np.unique(values))
        share = _top_share(values, spec.pool_size, 0.01)
        print(f"  {spec.name:<13} pool {spec.pool_size:>6}, observed {uniq:>6}, "
              f"top-1% share {share:.3f}")

    # Sequence modality: length profile and latent-mode speed sanity
    # (mode identity is mechanism metadata, not rule content).
    print("\nsequences:")
    print(f"  length mean {cb.seq_len.mean():.1f}, "
          f"p10/p50/p90: {np.quantile(cb.seq_len, [0.1, 0.5, 0.9]).astype(int)}")
    totals = cb.seq_durations.sum(axis=1)
    for mid, mname in enumerate(gt.mode_names):
        m = gt.seq_mode == mid
        print(f"  mode {mname:<9} share {m.mean():.3f}, "
              f"mean len {cb.seq_len[m].mean():5.1f}, "
              f"mean total duration {totals[m].mean():6.2f}s")

    # Bad-rate time curve (per episode) + per-regime table.
    rates = np.array([ld.outcome[cb.episode == ep].mean()
                      for ep in range(args.episodes)])
    print(f"\nbad_rate per episode (min {rates.min():.3f}, max {rates.max():.3f}):")
    print(f"  {_sparkline(rates)}")
    for r in np.unique(cb.regime_id):
        m = cb.regime_id == r
        print(f"  R{r:02d}: episodes {cb.episode[m].min():>3}-{cb.episode[m].max():>3}, "
              f"cases {m.sum():>6}, bad_rate {ld.outcome[m].mean():.4f}")

    matured = ld.visible_mask(args.episodes - 1)
    print(f"\noutcomes matured by final episode: {matured.mean():.3f}")


if __name__ == "__main__":
    main()
