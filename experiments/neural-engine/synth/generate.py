"""Demo entry: generate a world and print summary statistics.

Usage (from experiments/neural-engine/):
    uv run python -m synth.generate --episodes 100 --per-episode 1000
"""

from __future__ import annotations

import argparse

import numpy as np

from synth import SyntheticWorld, default_config


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description="CLAB-lite synthetic credit world")
    ap.add_argument("--episodes", type=int, default=100)
    ap.add_argument("--per-episode", type=int, default=1000)
    ap.add_argument("--seed", type=int, default=20260726)
    args = ap.parse_args(argv)

    world = SyntheticWorld(default_config(seed=args.seed))
    data = world.run(args.episodes, args.per_episode)
    cb, ld, gt = data.casebook, data.ledger, data.truth

    n = len(cb.case_ids)
    bad = ld.outcome.mean()
    print(f"cases: {n} ({args.episodes} episodes x {args.per_episode})")
    print(f"seed: {args.seed}")
    print(f"overall bad_rate: {bad:.4f}")
    print(f"regimes: {cb.regime_id.max() + 1} "
          f"({len(data.regimes)} switches, expected ~{args.episodes * 0.1:.0f})")
    delays, counts = np.unique(ld.delay, return_counts=True)
    print("outcome delay distribution: "
          + ", ".join(f"{d}ep: {c / n:.3f}" for d, c in zip(delays, counts)))
    matured = ld.visible_mask(args.episodes - 1)
    print(f"outcomes matured by final episode: {matured.mean():.3f}")

    print("\nper-regime bad_rate:")
    for r in np.unique(cb.regime_id):
        m = cb.regime_id == r
        print(f"  R{r:02d}: episodes {cb.episode[m].min():>3}-{cb.episode[m].max():>3}, "
              f"cases {m.sum():>6}, bad_rate {ld.outcome[m].mean():.4f}")

    exp = world.experience_rules
    idx = [gt.rule_ids.index(r.rule_id) for r in exp]
    fire_rates = gt.rule_fired[:, idx].mean(axis=0)
    order = np.argsort(-fire_rates)[:5]
    print("\ntop-5 experience rules by fire rate:")
    for i in order:
        print(f"  {exp[i].rule_id} (w={exp[i].weight:+.2f}, "
              f"fire={fire_rates[i]:.3f}): {exp[i].text}")

    if data.regimes:
        ev = data.regimes[0]
        print(f"\nfirst switch at episode {ev.episode} -> {ev.regime_tag}, "
              f"{len(ev.mutations)} mutations, e.g.:")
        for mu in ev.mutations[:3]:
            print(f"  {mu.rule_id} {mu.mode}: {mu.old_weight:+.3f} -> {mu.new_weight:+.3f}")


if __name__ == "__main__":
    main()
