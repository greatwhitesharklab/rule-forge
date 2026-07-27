"""CLAB-full 三方对比实验:枚举 vs 本地小模型 vs 我在环(eval/clabfull_comparison.py)。

问题:同一个 CLAB-full 考场(同世界同种子、同 dev 帧、同判卷器与 tau、
同轮数),三种「云端」各自出题,谁的发现力强?

  - enum:   暴力枚举臂(ClabFullAutoCloud,top-M/轮);
  - llm:    本地小模型臂(LocalLLMCloud,Qwen3-0.6B,methodology.md 注入,
            ≤3 提案/轮);
  - bridge: 我在环臂(AgentBridge,接口留位,默认不跑,主 agent 接入)。

判卷(本模块是唯一的 ground truth 消费者):
  规则 fire 由判卷侧按 `synthfull.rules/v1` payload 条件自行计算
  (num 阈值 / cat 池内索引值集 / seq 统计量),方向判定用 regime 漂移
  回放出的 dev 窗末当期权重;tau 标定复用机制一(命中侧理想特征 vs
  零侧伪特征 P99)。

判据(每臂):保留池发现数 / 发现轮次 / 假阳性数与假阳性率 / 总提案数
(提案效率 = 保留池发现数 / 总提案数)。产出对比表 json + 发现曲线图 +
终端总结。

用法(cwd = experiments/neural-engine):
    uv run python -m eval.clabfull_comparison                  # 枚举+小模型,3 种子
    uv run python -m eval.clabfull_comparison --arms enum      # 只跑枚举臂
    uv run python -m eval.clabfull_comparison --seeds 20260726 # 单种子
"""

from __future__ import annotations

import argparse
import json
import shutil
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

from eval.clab_discovery import (
    RuleFire,
    RuleGrader,
    calibrate_threshold,
)
from selflearn import DEFAULT_LGBM_PARAMS, LoopConfig, SelfLearnLoop, StrategyMemory
from selflearn.clabfull import (
    ALL_FEATURE_COLUMNS,
    CLABFULL_FIELD_STATEMENTS,
    ClabFullAutoCloud,
    LocalLLMCloud,
    build_agent_bridge_cloud,
    build_clabfull_split,
    clabfull_base_features,
)
from selflearn.features import compile_l2_expression
from slots import SlotConfig, SlotService
from synthfull import FullWorld, default_config
from synthfull.rulegen import Condition, FeatureView, rules_payload
from synthfull.world import WorldData

NOT_FOUND_OFFSET = 1  # 未发现规则的发现轮次 = R + 1


# ---------------------------------------------------------------------------
# 判卷适配:synthfull.rules/v1 → dev 窗 fire(判卷侧自行计算)
# ---------------------------------------------------------------------------


def _parse_payload_rules(payload: dict[str, Any]) -> list[tuple[str, str, float, tuple[Condition, ...]]]:
    """解析 synthfull.rules/v1;格式/条件类型不符直接 ValueError。"""
    if payload.get("format") != "synthfull.rules/v1":
        raise ValueError(
            f"unsupported ground-truth format: {payload.get('format')!r}"
        )
    out = []
    for r in payload["rules"]:
        conds: list[Condition] = []
        for c in r["conditions"]:
            kind = c["kind"]
            if kind == "cat":
                conds.append(Condition(
                    "cat", c["field"], "in",
                    values=tuple(int(v) for v in (c["values"] or ())),
                ))
            elif kind in ("num", "seq"):
                op = c["op"]
                if op not in (">", "<"):
                    raise ValueError(f"unsupported op {op!r} in {r['rule_id']}")
                conds.append(Condition(
                    kind, c["field"], op, threshold=float(c["threshold"]),
                ))
            else:
                raise ValueError(f"unsupported condition kind {kind!r}")
        out.append((r["rule_id"], r["pool"], float(r["weight"]), tuple(conds)))
    return out


def rule_fires_from_payload(
    payload: dict[str, Any],
    fv: FeatureView,
    weights: dict[str, float] | None = None,
) -> tuple[RuleFire, ...]:
    """按 payload 条件在 FeatureView 上计算 fire(判卷原料)。

    `weights` 覆盖方向判定用权重(当期权重);None 时用 payload 基权重。
    """
    fires: list[RuleFire] = []
    for rule_id, pool, base_weight, conds in _parse_payload_rules(payload):
        m = np.ones(fv.observables.shape[0], dtype=bool)
        for cond in conds:
            m &= cond.mask(fv)
        w = base_weight if weights is None else weights.get(rule_id, base_weight)
        fires.append(RuleFire(rule_id, pool, float(w), m))
    return tuple(fires)


def dev_end_weights(
    world: FullWorld, data: WorldData, dev_episodes: int
) -> dict[str, float]:
    """dev 窗末当期权重:基权重 + episode <= dev 窗末的 regime mutation 回放。"""
    w = {r.rule_id: r.weight for r in world.rules}
    cutoff = dev_episodes - 1
    for ev in data.regimes:
        if ev.episode <= cutoff:
            for mut in ev.mutations:
                w[mut.rule_id] = mut.new_weight
    return w


def rule_fires_from_fullworld(
    world: FullWorld,
    data: WorldData,
    case_idx: np.ndarray,
    dev_episodes: int,
) -> tuple[RuleFire, ...]:
    """判卷唯一取数口:synthfull.rules/v1 payload + dev 案例子集 → RuleFire。"""
    payload = rules_payload(world.rules)
    idx = np.asarray(case_idx)
    fv_all = data.feature_view()
    fv = FeatureView(
        observables=fv_all.observables[idx],
        observable_index=fv_all.observable_index,
        categories={k: v[idx] for k, v in fv_all.categories.items()},
        stats=fv_all.stats[idx],
        stat_index=fv_all.stat_index,
    )
    weights = dev_end_weights(world, data, dev_episodes)
    return rule_fires_from_payload(payload, fv, weights=weights)


# ---------------------------------------------------------------------------
# 臂驱动
# ---------------------------------------------------------------------------


@dataclass
class ArmResult:
    """单臂在一颗种子上的完整结果。"""

    arm: str
    tau: float
    rounds: list[dict[str, Any]]
    discovery_rounds: dict[str, int]  # rule_id -> 首发现轮次(R+1 = 未发现)
    total_proposals: int
    false_positive_count: int
    heldout_found: int
    cloud_stats: dict[str, Any] = field(default_factory=dict)

    @property
    def graded(self) -> int:
        return sum(len(r["accepted"]) for r in self.rounds)

    def as_dict(self) -> dict[str, Any]:
        return {
            "arm": self.arm, "tau": self.tau, "rounds": self.rounds,
            "discovery_rounds": self.discovery_rounds,
            "total_proposals": self.total_proposals,
            "false_positive_count": self.false_positive_count,
            "false_positive_rate": round(
                self.false_positive_count / max(self.graded, 1), 6),
            "heldout_found": self.heldout_found,
            "accepted_total": self.graded,
            "cloud_stats": self.cloud_stats,
        }


def run_arm(
    split: Any,
    fires: tuple[RuleFire, ...],
    grader: RuleGrader,
    cloud: Any,
    *,
    arm: str,
    budget: int,
    cfg: "ComparisonConfig",
    seed: int,
    work_dir: Path,
) -> ArmResult:
    """单臂闭环:SelfLearnLoop 跑 cfg.rounds 轮,判卷入档。"""
    work_dir.mkdir(parents=True, exist_ok=True)
    memory = StrategyMemory(SlotService(work_dir / "slots.db", SlotConfig()))
    memory.init_field_slots(CLABFULL_FIELD_STATEMENTS)
    params = dict(cfg.lgbm_params or DEFAULT_LGBM_PARAMS)

    loop_cfg = LoopConfig(
        dev_start="000", dev_end=f"{cfg.dev_episodes - 1:03d}",
        eval_start=f"{cfg.dev_episodes:03d}", eval_end=f"{cfg.episodes - 1:03d}",
        top_k=cfg.top_k, max_features_per_round=budget,
        dev_holdout_episodes=cfg.dev_holdout_episodes,
        lgbm_params=params, seed=seed,
    )
    loop = SelfLearnLoop(split.dev_df, config=loop_cfg,
                         base_features=clabfull_base_features,
                         cloud=cloud, memory=memory)
    records = loop.run(cfg.rounds)
    memory.service.persist()

    rounds_out: list[dict[str, Any]] = []
    discovery_rounds: dict[str, int] = {}
    fp_count = 0
    total_proposals = 0
    seen: set[str] = set()
    for rec in records:
        total_proposals += len(rec.proposals)
        accepted_out: list[dict[str, Any]] = []
        for p in rec.proposals:
            if p.name not in rec.accepted or p.name in seen:
                continue
            seen.add(p.name)
            values = compile_l2_expression(p.expression)(loop.verify_df).to_numpy()
            g = grader.grade(p.name, values)
            entry = {"name": p.name, "expression": p.expression,
                     "category": g.category, "best_rule_id": g.best_rule_id,
                     "corr": g.best_corr, "note": g.note,
                     "iv": p.metrics.get("iv")}
            accepted_out.append(entry)
            if g.category in ("rediscovery", "known") and g.best_rule_id:
                prev = discovery_rounds.get(g.best_rule_id)
                if prev is None or rec.round_no < prev:
                    discovery_rounds[g.best_rule_id] = rec.round_no
            elif g.category == "false_positive":
                fp_count += 1
        rounds_out.append({
            "round": rec.round_no, "n_unexplained": rec.n_unexplained,
            "auc_before": rec.auc_before, "auc_after": rec.auc_after,
            "n_proposals": len(rec.proposals), "accepted": accepted_out,
        })

    for f in fires:
        discovery_rounds.setdefault(f.rule_id, cfg.rounds + NOT_FOUND_OFFSET)
    heldout_found = sum(
        1 for f in fires
        if f.pool == "heldout" and discovery_rounds[f.rule_id] <= cfg.rounds
    )
    cloud_stats = dict(getattr(cloud, "stats", {}) or {})
    return ArmResult(
        arm=arm, tau=grader.tau, rounds=rounds_out,
        discovery_rounds=discovery_rounds, total_proposals=total_proposals,
        false_positive_count=fp_count, heldout_found=heldout_found,
        cloud_stats=cloud_stats,
    )


# ---------------------------------------------------------------------------
# 实验主流程
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ComparisonConfig:
    seeds: tuple[int, ...] = (20260726, 20260727, 20260728)
    episodes: int = 60
    per_episode: int = 800
    dev_episodes: int = 45
    rounds: int = 10
    top_m: int = 20  # 枚举臂每轮候选预算
    llm_max_proposals: int = 3  # 小模型臂每轮提案上限(手册纪律)
    llm_max_new_tokens: int = 256  # 采样易啰嗦,截断保预算(3 提案 JSON 足够)
    llm_temperature: float = 0.5  # 贪心解码会同 context 重复出题,采样保多样
    llm_top_p: float = 0.9
    top_k: int = 20
    dev_holdout_episodes: int = 3
    n_null: int = 100
    n_experience: int = 20  # 世界规则池(经验池);测试用小池
    n_heldout: int = 10  # 世界规则池(保留池)
    lgbm_params: dict[str, Any] | None = None
    arms: tuple[str, ...] = ("enum", "llm")  # bridge 默认不跑(我在环留接口)
    out_dir: Path = Path("eval/artifacts-clabfull")


def _build_cloud(arm: str, cfg: ComparisonConfig, feature_df: Any,
                 labels: np.ndarray, seed: int, work_root: Path,
                 llm_cloud: Any | None) -> Any:
    if arm == "enum":
        return ClabFullAutoCloud(feature_df, labels)  # 枚举与排序均确定
    if arm == "llm":
        if llm_cloud is not None:
            return llm_cloud
        from llm import LocalLLM

        return LocalLLMCloud.from_llm(
            LocalLLM(), max_new_tokens=cfg.llm_max_new_tokens,
            temperature=cfg.llm_temperature, top_p=cfg.llm_top_p,
            seed=seed, max_proposals=cfg.llm_max_proposals,
        )
    if arm == "bridge":
        return build_agent_bridge_cloud(work_root / "bridge")
    raise ValueError(f"unknown arm {arm!r}")


def run_seed(
    cfg: ComparisonConfig,
    seed: int,
    *,
    arms: tuple[str, ...] | None = None,
    llm_cloud: Any | None = None,
) -> dict[str, Any]:
    """一颗种子:世界生成 → 共享 dev 帧/判卷器/tau → 逐臂闭环。"""
    work_root = cfg.out_dir / "work"
    world = FullWorld(default_config(
        seed=seed, n_experience=cfg.n_experience, n_heldout=cfg.n_heldout
    ))
    data = world.run(cfg.episodes, cfg.per_episode)
    split = build_clabfull_split(data, dev_episodes=cfg.dev_episodes)
    labels = split.dev_df["outcome"].to_numpy().astype(np.int8)
    feature_df = split.dev_df[list(ALL_FEATURE_COLUMNS)]

    fires = rule_fires_from_fullworld(world, data, split.dev_case_idx,
                                      cfg.dev_episodes)
    cal = calibrate_threshold(fires, labels, seed=seed, n_null=cfg.n_null)
    grader = RuleGrader(fires, labels, cal.tau)

    arms_out: dict[str, ArmResult] = {}
    for arm in (arms or cfg.arms):
        cloud = _build_cloud(arm, cfg, feature_df, labels, seed,
                             work_root, llm_cloud)
        budget = cfg.top_m if arm != "llm" else cfg.llm_max_proposals
        arms_out[arm] = run_arm(
            split, fires, grader, cloud, arm=arm, budget=budget, cfg=cfg,
            seed=seed, work_dir=work_root / f"seed{seed}-{arm}",
        )
    return {
        "seed": seed, "tau": cal.tau, "calibration": cal.as_dict(),
        "switches": [e.episode for e in data.regimes],
        "heldout_ids": [f.rule_id for f in fires if f.pool == "heldout"],
        "arms": arms_out,
    }


def _cum_curve(discovery_rounds: dict[str, int], rounds: int,
               heldout_ids: list[str]) -> list[int]:
    return [
        sum(1 for rid in heldout_ids if discovery_rounds[rid] <= r)
        for r in range(1, rounds + 1)
    ]


def _fp_cum_curve(rounds_out: list[dict[str, Any]], rounds: int) -> list[int]:
    fp_at = [
        sum(1 for a in r["accepted"] if a["category"] == "false_positive")
        for r in rounds_out
    ]
    return [sum(fp_at[:r]) for r in range(1, rounds + 1)]


def _plot(seed_runs: list[dict[str, Any]], arm_names: list[str],
          rounds: int, heldout_ids: list[str], out_png: Path) -> None:
    xs = list(range(1, rounds + 1))
    colors = {"enum": "C0", "llm": "C3", "bridge": "C2"}
    fig, axes = plt.subplots(1, 2, figsize=(13, 5))
    for arm in arm_names:
        runs = [s for s in seed_runs if arm in s["arms"]]
        if not runs:
            continue
        disc = np.mean([
            _cum_curve(s["arms"][arm].discovery_rounds, rounds, heldout_ids)
            for s in runs
        ], axis=0)
        fp = np.mean([
            _fp_cum_curve(s["arms"][arm].rounds, rounds) for s in runs
        ], axis=0)
        label = {"enum": "enumerator (brute force)",
                 "llm": "local LLM (Qwen3-0.6B)",
                 "bridge": "agent bridge (human-in-loop)"}.get(arm, arm)
        axes[0].plot(xs, disc, color=colors.get(arm), lw=2, marker="o", ms=4,
                     label=label)
        axes[1].plot(xs, fp, color=colors.get(arm), lw=2, marker="s", ms=4,
                     label=label)
    axes[0].set_title("held-out rules rediscovered (mean over seeds)")
    axes[0].set_xlabel("round")
    axes[0].set_xticks(xs)
    axes[0].legend()
    axes[1].set_title("cumulative false positives (mean over seeds)")
    axes[1].set_xlabel("round")
    axes[1].set_xticks(xs)
    axes[1].legend()
    fig.suptitle("CLAB-full three-way discovery comparison")
    fig.tight_layout()
    fig.savefig(out_png, dpi=120)
    plt.close(fig)


def run_experiment(cfg: ComparisonConfig) -> dict[str, Any]:
    t0 = time.time()
    work_root = cfg.out_dir / "work"
    if work_root.exists():
        shutil.rmtree(work_root)
    work_root.mkdir(parents=True, exist_ok=True)
    cfg.out_dir.mkdir(parents=True, exist_ok=True)

    seed_runs = [run_seed(cfg, seed) for seed in cfg.seeds]
    heldout_ids = seed_runs[0]["heldout_ids"]
    arm_names = [a for a in cfg.arms if any(a in s["arms"] for s in seed_runs)]

    table: list[dict[str, Any]] = []
    for arm in arm_names:
        runs = [s["arms"][arm] for s in seed_runs if arm in s["arms"]]
        found = [a.heldout_found for a in runs]
        proposals = [a.total_proposals for a in runs]
        fps = [a.false_positive_count for a in runs]
        accepted = [a.graded for a in runs]
        mean_rounds = []
        for rid in heldout_ids:
            rs = [a.discovery_rounds[rid] for a in runs]
            mean_rounds.append(round(float(np.mean(rs)), 2))
        row = {
            "arm": arm,
            "n_seeds": len(runs),
            "heldout_found_mean": round(float(np.mean(found)), 2),
            "heldout_found_per_seed": found,
            "heldout_total": len(heldout_ids),
            "mean_discovery_round_per_rule": mean_rounds,
            "false_positive_mean": round(float(np.mean(fps)), 2),
            "false_positive_rate_mean": round(
                float(np.mean([f / max(g, 1) for f, g in zip(fps, accepted)])), 4),
            "total_proposals_mean": round(float(np.mean(proposals)), 1),
            "accepted_mean": round(float(np.mean(accepted)), 1),
            "discovery_per_proposal": round(
                float(np.mean(found)) / max(float(np.mean(proposals)), 1e-9), 4),
        }
        if arm == "llm" and runs[0].cloud_stats:
            lat = [x for a in runs for x in a.cloud_stats.get("latency_s", [])]
            row["llm"] = {
                "calls": sum(a.cloud_stats.get("calls", 0) for a in runs),
                "retries": sum(a.cloud_stats.get("retries", 0) for a in runs),
                "parse_failures": sum(
                    a.cloud_stats.get("parse_failures", 0) for a in runs),
                "empty_results": sum(
                    a.cloud_stats.get("empty_results", 0) for a in runs),
                "latency_mean_s": round(float(np.mean(lat)), 2) if lat else None,
                "prompt_chars_mean": round(float(np.mean([
                    x for a in runs
                    for x in a.cloud_stats.get("prompt_chars", [])
                ])), 0),
            }
        table.append(row)

    metrics: dict[str, Any] = {
        "config": {
            "seeds": list(cfg.seeds), "episodes": cfg.episodes,
            "per_episode": cfg.per_episode, "dev_episodes": cfg.dev_episodes,
            "rounds": cfg.rounds, "top_m": cfg.top_m,
            "llm_max_proposals": cfg.llm_max_proposals,
            "llm_max_new_tokens": cfg.llm_max_new_tokens,
            "llm_temperature": cfg.llm_temperature,
            "llm_top_p": cfg.llm_top_p,
            "arms": list(arm_names),
        },
        "table": table,
        "seeds": [{
            "seed": s["seed"], "tau": s["tau"], "calibration": s["calibration"],
            "switches": s["switches"],
            "arms": {a: r.as_dict() for a, r in s["arms"].items()},
        } for s in seed_runs],
        "runtime_seconds": round(time.time() - t0, 1),
    }
    (cfg.out_dir / "clabfull_comparison.json").write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2)
    )
    _plot(seed_runs, arm_names, cfg.rounds, heldout_ids,
          cfg.out_dir / "clabfull_curves.png")
    return metrics


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--seeds", type=int, nargs="+",
                    default=list(ComparisonConfig().seeds))
    ap.add_argument("--episodes", type=int, default=60)
    ap.add_argument("--per-episode", type=int, default=800)
    ap.add_argument("--dev-episodes", type=int, default=45)
    ap.add_argument("--rounds", type=int, default=10)
    ap.add_argument("--top-m", type=int, default=20)
    ap.add_argument("--llm-max-new-tokens", type=int, default=256)
    ap.add_argument("--arms", nargs="+", default=list(ComparisonConfig().arms),
                    choices=["enum", "llm", "bridge"])
    ap.add_argument("--n-null", type=int, default=100)
    ap.add_argument("--out", type=Path, default=Path("eval/artifacts-clabfull"))
    args = ap.parse_args(argv)

    cfg = ComparisonConfig(
        seeds=tuple(args.seeds), episodes=args.episodes,
        per_episode=args.per_episode, dev_episodes=args.dev_episodes,
        rounds=args.rounds, top_m=args.top_m,
        llm_max_new_tokens=args.llm_max_new_tokens,
        arms=tuple(args.arms), n_null=args.n_null, out_dir=args.out,
    )
    m = run_experiment(cfg)

    print(f"runtime={m['runtime_seconds']:.0f}s seeds={m['config']['seeds']} "
          f"arms={m['config']['arms']}")
    print(f"{'arm':<8} {'found':>12} {'fp':>6} {'fp_rate':>8} "
          f"{'proposals':>10} {'disc/prop':>10}")
    for row in m["table"]:
        print(f"{row['arm']:<8} "
              f"{row['heldout_found_mean']:>5.2f}/{row['heldout_total']:<6} "
              f"{row['false_positive_mean']:>6.2f} "
              f"{row['false_positive_rate_mean']:>8.4f} "
              f"{row['total_proposals_mean']:>10.1f} "
              f"{row['discovery_per_proposal']:>10.4f}")
        if "llm" in row:
            info = row["llm"]
            print(f"  llm: calls={info['calls']} retries={info['retries']} "
                  f"parse_failures={info['parse_failures']} "
                  f"empty={info['empty_results']} "
                  f"latency={info['latency_mean_s']}s "
                  f"prompt_chars={info['prompt_chars_mean']}")
    print(f"artifacts in {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
