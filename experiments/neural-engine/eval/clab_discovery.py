"""机制一实验:CLAB-lite 自学习闭环发现力验证(eval/clab_discovery.py)。

问题:闭环(指路 → 自动云端暴力候选 → §8.4 验证漏斗 → 入库 → 重训)能否
重新发现埋进世界生成、但下游不可见的 10 条保留池规则?

判卷(本模块是唯一的 ground truth 消费者):
  对每条规则在 dev 案例上取 fire 指示变量(条件合取满足=1,来自
  world.truth.rule_fired);候选特征(沙箱求值后)与每条规则 fire 做点双列
  相关;判定:
    重新发现 = 特征已过验证门槛 且 与某保留规则 fire |corr| > tau 且方向一致;
    已知发现 = 最佳命中落在经验池规则;
    假阳性   = 与全部 30 条规则 fire |corr| <= tau(或方向不一致)。
  方向一致:sign(corr(feat, fire)) * sign(rule.weight) == sign(corr(feat, y))。

阈值标定(保守):
  命中侧 —— 对每条经验池规则构造「理想特征」(fire 指示 + 10% 比特翻转),
  看其与自身 fire 的相关分布;零侧 —— 构造「伪特征」(多条规则 fire 的随机
  带号混合 + 噪声,以及纯噪声),取其与全部 30 条规则最大 |corr| 的分布。
  tau = max(零侧 P99, 0.05)。标定数值全部写进 metrics json。

用法(cwd = experiments/neural-engine):
    uv run python -m eval.clab_discovery                 # 100 ep × 1000,15 轮
    uv run python -m eval.clab_discovery --skip-eval     # 跳过 eval 窗滚动 AUC
"""

from __future__ import annotations

import argparse
import json
import math
import shutil
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.metrics import roc_auc_score

from selflearn import DEFAULT_LGBM_PARAMS, LoopConfig, SelfLearnLoop, StrategyMemory
from selflearn.clab import (
    CLAB_FIELDS,
    CLAB_FIELD_STATEMENTS,
    ClabAutoCloud,
    build_clab_split,
    clab_base_features,
)
from selflearn.features import compile_l2_expression
from selflearn.gbdt import train_gbdt
from slots import SlotConfig, SlotService
from synth import SyntheticWorld, default_config
from synth.rules import build_rule_pool
from synth.world import WorldData
from verify.metrics import information_value, lift

# ---------------------------------------------------------------------------
# 判卷器
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class RuleFire:
    """一条规则在 dev 案例上的 fire 指示变量(判卷原料,ground truth 侧)。"""

    rule_id: str
    pool: str  # "experience" | "heldout"
    weight: float  # 正 = fire 推高 bad logit
    fire: np.ndarray  # bool [n_dev]


def pointbiserial(values: np.ndarray, fire: np.ndarray) -> float:
    """连续特征 × 二值 fire 的 Pearson 相关(= 点双列相关);NaN-safe。"""
    v = np.asarray(values, dtype=np.float64)
    f = np.asarray(fire, dtype=bool).astype(np.float64)
    mask = np.isfinite(v)
    v, f = v[mask], f[mask]
    if v.size == 0 or v.std() == 0.0 or f.std() == 0.0:
        return float("nan")
    return float(np.corrcoef(v, f)[0, 1])


def rule_fires_from_world(
    world: WorldData, case_idx: np.ndarray
) -> tuple[RuleFire, ...]:
    """从 ground truth 取 30 条规则的 dev 窗 fire 指示(判卷唯一取数口)。"""
    rules = build_rule_pool()
    truth = world.truth
    if tuple(r.rule_id for r in rules) != truth.rule_ids:
        raise ValueError("rule pool order mismatch with ground truth rule_ids")
    fired = truth.rule_fired[case_idx]
    return tuple(
        RuleFire(r.rule_id, r.pool, r.weight, fired[:, j])
        for j, r in enumerate(rules)
    )


@dataclass(frozen=True)
class GradeResult:
    feature: str
    category: str  # "rediscovery" | "known" | "false_positive"
    best_rule_id: str | None
    best_corr: float  # 命中规则的带号相关(未命中为 0)
    direction_ok: bool
    note: str

    def as_dict(self) -> dict[str, Any]:
        return {
            "feature": self.feature, "category": self.category,
            "best_rule_id": self.best_rule_id, "best_corr": self.best_corr,
            "direction_ok": self.direction_ok, "note": self.note,
        }


class RuleGrader:
    """规则命中判卷:|corr| 降序找第一条过 tau 且方向一致的规则。"""

    def __init__(
        self, fires: tuple[RuleFire, ...], labels: np.ndarray, tau: float
    ) -> None:
        self._fires = fires
        self._y = np.asarray(labels, dtype=np.int8)
        self.tau = float(tau)

    def grade(self, name: str, values: np.ndarray) -> GradeResult:
        v = np.asarray(values, dtype=np.float64)
        c_y = pointbiserial(v, self._y)
        sign_y = math.copysign(1.0, c_y) if math.isfinite(c_y) and c_y != 0 else 0.0

        scored = []
        for f in self._fires:
            c = pointbiserial(v, f.fire)
            if math.isfinite(c):
                scored.append((abs(c), c, f))
        scored.sort(key=lambda t: -t[0])

        mismatched: str | None = None
        for abs_c, c, f in scored:
            if abs_c <= self.tau:
                break  # 降序:之后全都不过线
            expected = math.copysign(1.0, c) * math.copysign(1.0, f.weight)
            if sign_y == expected:
                category = "rediscovery" if f.pool == "heldout" else "known"
                return GradeResult(name, category, f.rule_id, round(c, 6), True,
                                   f"matched {f.rule_id} corr={c:.4f}")
            if mismatched is None:
                mismatched = f.rule_id

        if mismatched is not None:
            return GradeResult(name, "false_positive", None, 0.0, False,
                               f"direction mismatch vs {mismatched}")
        return GradeResult(name, "false_positive", None, 0.0, True,
                           "below threshold")


# ---------------------------------------------------------------------------
# 阈值标定
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ThresholdCalibration:
    tau: float
    hit_min: float
    hit_median: float
    null_p99: float
    null_max: float
    n_hit: int
    n_null: int
    hit_gate_pass: int  # 理想特征中过 §8.4 门槛(IV>0.1 或 lift>1.3)的条数

    def as_dict(self) -> dict[str, Any]:
        return {
            "tau": self.tau, "hit_min": self.hit_min,
            "hit_median": self.hit_median, "null_p99": self.null_p99,
            "null_max": self.null_max, "n_hit": self.n_hit,
            "n_null": self.n_null, "hit_gate_pass": self.hit_gate_pass,
        }


def calibrate_threshold(
    fires: tuple[RuleFire, ...],
    labels: np.ndarray,
    *,
    seed: int = 20260727,
    flip_p: float = 0.10,
    n_null: int = 100,
    null_noise: float = 1.5,
    mix_range: tuple[int, int] = (8, 15),
) -> ThresholdCalibration:
    """用经验池规则标定命中阈值 tau(过程与数值入报告)。

    命中侧:每条经验规则的 fire + flip_p 比特翻转 = 「已知命中经验规则的
    理想特征」,取其与自身 fire 的相关分布。零侧:多条规则 fire 的随机
    带号混合 + 噪声(模拟「过门槛但不特指某条规则」的伪特征)与纯噪声,
    各取与全部规则的最大 |corr|。tau = max(零侧 P99, 0.05)。
    """
    rng = np.random.default_rng(seed)
    y = np.asarray(labels, dtype=np.int8)
    exp = [f for f in fires if f.pool == "experience"]

    hit_corrs: list[float] = []
    gate_pass = 0
    for f in exp:
        proxy = (f.fire ^ (rng.random(len(f.fire)) < flip_p)).astype(np.float64)
        c = pointbiserial(proxy, f.fire)
        if math.isfinite(c):
            hit_corrs.append(c)
        iv = information_value(proxy, y)
        lf = lift(proxy, y)
        if (math.isfinite(iv) and iv > 0.1) or (math.isfinite(lf) and lf > 1.3):
            gate_pass += 1

    n = len(y)
    null_maxes: list[float] = []
    for _ in range(n_null):
        if rng.random() < 0.5 or len(fires) < 4:
            feat = rng.normal(size=n)  # 纯噪声伪特征
        else:  # 多规则带号混合伪特征:骑全局风险信号但不特指某条规则
            lo = min(mix_range[0], len(fires))
            hi = min(mix_range[1], len(fires))
            k = int(rng.integers(lo, hi + 1))
            idx = rng.choice(len(fires), size=k, replace=False)
            signs = rng.choice(np.array([-1.0, 1.0]), size=k)
            mix = np.zeros(n)
            for s, i in zip(signs, idx):
                mix = mix + s * fires[i].fire.astype(np.float64)
            feat = mix + rng.normal(0.0, null_noise * (mix.std() + 1e-9), n)
        corrs = [abs(pointbiserial(feat, f.fire)) for f in fires]
        corrs = [c for c in corrs if math.isfinite(c)]
        null_maxes.append(max(corrs) if corrs else 0.0)

    null_p99 = float(np.percentile(null_maxes, 99)) if null_maxes else 0.0
    tau = max(null_p99, 0.05)
    return ThresholdCalibration(
        tau=round(tau, 6),
        hit_min=round(min(hit_corrs), 6) if hit_corrs else float("nan"),
        hit_median=round(float(np.median(hit_corrs)), 6) if hit_corrs else float("nan"),
        null_p99=round(null_p99, 6),
        null_max=round(max(null_maxes), 6) if null_maxes else 0.0,
        n_hit=len(hit_corrs), n_null=len(null_maxes), hit_gate_pass=gate_pass,
    )


# ---------------------------------------------------------------------------
# 实验主流程
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class DiscoveryConfig:
    episodes: int = 100
    per_episode: int = 1000
    seed: int = 20260726
    dev_episodes: int = 60  # 前 60 episode 迭代,后 40 评估
    rounds: int = 15
    top_m: int = 20  # 每轮自动云端喂给验证漏斗的候选数
    top_k: int = 20  # GBDT 指路:解释不了的坏账数
    lag: int = 3  # eval 窗滚动评估的标签成熟滞后期
    eval_max_train_rows: int = 50_000
    skip_eval: bool = False
    n_null: int = 100  # 阈值标定零侧伪特征数
    dev_holdout_episodes: int = 3
    lgbm_params: dict[str, Any] | None = None  # None = DEFAULT_LGBM_PARAMS
    out_dir: Path = Path("eval/artifacts-clab")


@dataclass
class DiscoveryResult:
    metrics: dict[str, Any]
    loop: SelfLearnLoop
    cloud: ClabAutoCloud
    calibration: ThresholdCalibration


def _eval_window_auc(
    world: WorldData,
    loop: SelfLearnLoop,
    *,
    dev_episodes: int,
    episodes: int,
    lag: int,
    max_train_rows: int,
    seed: int,
    lgbm_params: dict[str, Any],
) -> dict[str, Any]:
    """eval 窗滚动 AUC:基线特征库 vs 基线+入库 L2(同参同种子同窗)。"""
    full_df = pd.DataFrame(world.casebook.observables, columns=list(CLAB_FIELDS))
    X_base, _ = clab_base_features(full_df)
    if len(loop.registry):
        X_final = np.hstack([X_base, loop.registry.compute(full_df).to_numpy()])
    else:
        X_final = X_base
    y = world.ledger.outcome
    ep = world.casebook.episode
    visible = world.ledger.visible_episode

    records: list[dict[str, Any]] = []
    for t in range(dev_episodes, episodes):
        tr = np.nonzero(visible <= t - lag)[0]
        if len(tr) > max_train_rows:
            tr = tr[-max_train_rows:]
        te = np.nonzero(ep == t)[0]
        if len(tr) < 1000 or len(np.unique(y[tr])) < 2 or len(np.unique(y[te])) < 2:
            continue
        p_base = train_gbdt(X_base[tr], y[tr], params=lgbm_params, seed=seed
                            ).predict_proba(X_base[te])[:, 1]
        p_final = train_gbdt(X_final[tr], y[tr], params=lgbm_params, seed=seed
                             ).predict_proba(X_final[te])[:, 1]
        records.append({
            "episode": t,
            "auc_base": float(roc_auc_score(y[te], p_base)),
            "auc_final": float(roc_auc_score(y[te], p_final)),
        })
    diffs = [r["auc_final"] - r["auc_base"] for r in records]
    return {
        "records": records,
        "auc_base_mean": float(np.mean([r["auc_base"] for r in records])) if records else None,
        "auc_final_mean": float(np.mean([r["auc_final"] for r in records])) if records else None,
        "diff_mean": float(np.mean(diffs)) if diffs else None,
        "n_episodes": len(records),
    }


def _plot_discovery(
    rounds_out: list[dict[str, Any]],
    n_rounds: int,
    out_png: Path,
) -> None:
    """发现曲线:累计重新发现(保留)/已知发现(经验)/假阳性 vs 轮次。"""
    xs = list(range(1, n_rounds + 1))
    red_cum, known_cum, fp_cum = [], [], []
    red_seen: set[str] = set()
    known_seen: set[str] = set()
    fp = 0
    for r in rounds_out:
        for a in r["accepted"]:
            if a["category"] == "rediscovery" and a["best_rule_id"]:
                red_seen.add(a["best_rule_id"])
            elif a["category"] == "known" and a["best_rule_id"]:
                known_seen.add(a["best_rule_id"])
            elif a["category"] == "false_positive":
                fp += 1
        red_cum.append(len(red_seen))
        known_cum.append(len(known_seen))
        fp_cum.append(fp)
    fig, ax = plt.subplots(figsize=(9, 5.5))
    ax.plot(xs, red_cum, color="C3", lw=2.0, marker="o", ms=4,
            label=f"held-out rediscovered ({len(red_seen)})")
    ax.plot(xs, known_cum, color="C0", lw=1.6, marker="s", ms=4,
            label=f"experience-pool matched ({len(known_seen)})")
    ax.plot(xs, fp_cum, color="C7", lw=1.4, ls="--", marker="^", ms=4,
            label=f"false positives ({fp})")
    ax.set_xlabel("round")
    ax.set_ylabel("cumulative count")
    ax.set_title("CLAB-lite discovery curve (mechanism 1: discovery power)")
    ax.set_xticks(xs)
    ax.legend()
    fig.tight_layout()
    fig.savefig(out_png, dpi=120)
    plt.close(fig)


def run_discovery(cfg: DiscoveryConfig) -> DiscoveryResult:
    """跑完整实验:世界生成 → dev 闭环迭代 → 判卷 → 产物落盘。"""
    t0 = time.time()
    lgbm_params = dict(cfg.lgbm_params or DEFAULT_LGBM_PARAMS)

    world = SyntheticWorld(default_config(seed=cfg.seed))
    data = world.run(cfg.episodes, cfg.per_episode)
    split = build_clab_split(data, dev_episodes=cfg.dev_episodes)

    work = cfg.out_dir / "work"
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True, exist_ok=True)
    cfg.out_dir.mkdir(parents=True, exist_ok=True)

    memory = StrategyMemory(SlotService(work / "slots.db", SlotConfig()))
    memory.init_field_slots(CLAB_FIELD_STATEMENTS)

    feature_df = split.dev_df[list(CLAB_FIELDS)]
    labels = split.dev_df["outcome"].to_numpy().astype(np.int8)
    cloud = ClabAutoCloud(feature_df, labels, seed=cfg.seed)

    loop_cfg = LoopConfig(
        dev_start="000", dev_end=f"{cfg.dev_episodes - 1:03d}",
        eval_start=f"{cfg.dev_episodes:03d}", eval_end=f"{cfg.episodes - 1:03d}",
        top_k=cfg.top_k, max_features_per_round=cfg.top_m,
        dev_holdout_episodes=cfg.dev_holdout_episodes,
        lgbm_params=lgbm_params, seed=cfg.seed,
    )
    loop = SelfLearnLoop(split.dev_df, config=loop_cfg,
                         base_features=clab_base_features,
                         cloud=cloud, memory=memory)
    records = loop.run(cfg.rounds)
    memory.service.persist()

    # ---- 判卷(唯一的 ground truth 消费点)----
    fires = rule_fires_from_world(data, split.dev_case_idx)
    cal = calibrate_threshold(fires, labels, seed=cfg.seed, n_null=cfg.n_null)
    grader = RuleGrader(fires, labels, cal.tau)

    rounds_out: list[dict[str, Any]] = []
    discovery: dict[str, dict[str, Any]] = {}  # rule_id -> 首次命中记录
    graded = 0
    fp_count = 0
    seen_features: set[str] = set()
    for rec in records:
        accepted_out: list[dict[str, Any]] = []
        for p in rec.proposals:
            if p.name not in rec.accepted or p.name in seen_features:
                continue
            seen_features.add(p.name)
            values = compile_l2_expression(p.expression)(loop.verify_df).to_numpy()
            g = grader.grade(p.name, values)
            graded += 1
            entry = {
                "name": p.name, "expression": p.expression,
                "category": g.category, "best_rule_id": g.best_rule_id,
                "corr": g.best_corr, "note": g.note,
                "iv": p.metrics.get("iv"), "round": rec.round_no,
            }
            accepted_out.append(entry)
            if g.category in ("rediscovery", "known") and g.best_rule_id:
                discovery.setdefault(g.best_rule_id, {
                    "rule_id": g.best_rule_id, "pool": g.category,
                    "found_round": rec.round_no, "feature": p.name,
                    "corr": g.best_corr, "iv": p.metrics.get("iv"),
                })
            elif g.category == "false_positive":
                fp_count += 1
        rounds_out.append({
            "round": rec.round_no, "n_unexplained": rec.n_unexplained,
            "auc_dev_before": rec.auc_before, "auc_dev_after": rec.auc_after,
            "accepted": accepted_out,
        })

    heldout_discovery = [
        discovery.get(f.rule_id, {"rule_id": f.rule_id, "pool": "rediscovery",
                                  "found_round": None, "feature": None,
                                  "corr": None, "iv": None})
        for f in fires if f.pool == "heldout"
    ]
    known_discovery = [
        discovery[f.rule_id] for f in fires
        if f.pool == "experience" and f.rule_id in discovery
    ]

    eval_auc = None
    if not cfg.skip_eval:
        eval_auc = _eval_window_auc(
            data, loop, dev_episodes=cfg.dev_episodes, episodes=cfg.episodes,
            lag=cfg.lag, max_train_rows=cfg.eval_max_train_rows,
            seed=cfg.seed, lgbm_params=lgbm_params,
        )

    with open(cfg.out_dir / "iteration_log.jsonl", "w", encoding="utf-8") as fh:
        for r in records:
            fh.write(json.dumps(r.as_dict(), ensure_ascii=False) + "\n")
    _plot_discovery(rounds_out, cfg.rounds, cfg.out_dir / "clab_discovery_curve.png")

    n_rediscovered = sum(1 for h in heldout_discovery if h["found_round"] is not None)
    metrics: dict[str, Any] = {
        "config": {
            "episodes": cfg.episodes, "per_episode": cfg.per_episode,
            "seed": cfg.seed, "dev_episodes": cfg.dev_episodes,
            "rounds": cfg.rounds, "top_m": cfg.top_m, "top_k": cfg.top_k,
            "lag": cfg.lag, "n_candidates": len(cloud.candidates),
            "cloud": "clab-auto (brute-force enumerator)",
        },
        "calibration": cal.as_dict(),
        "rounds": rounds_out,
        "heldout_discovery": heldout_discovery,
        "known_discovery": known_discovery,
        "heldout_found": n_rediscovered,
        "known_found": len(known_discovery),
        "false_positive_count": fp_count,
        "false_positive_rate": round(fp_count / max(graded, 1), 6),
        "accepted_total": graded,
        "eval_auc": eval_auc,
        "runtime_seconds": round(time.time() - t0, 1),
    }
    (cfg.out_dir / "clab_metrics.json").write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2)
    )
    return DiscoveryResult(metrics=metrics, loop=loop, cloud=cloud, calibration=cal)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--episodes", type=int, default=100)
    ap.add_argument("--per-episode", type=int, default=1000)
    ap.add_argument("--seed", type=int, default=20260726)
    ap.add_argument("--dev-episodes", type=int, default=60)
    ap.add_argument("--rounds", type=int, default=15)
    ap.add_argument("--top-m", type=int, default=20)
    ap.add_argument("--top-k", type=int, default=20)
    ap.add_argument("--lag", type=int, default=3)
    ap.add_argument("--n-null", type=int, default=100)
    ap.add_argument("--skip-eval", action="store_true")
    ap.add_argument("--out", type=Path, default=Path("eval/artifacts-clab"))
    args = ap.parse_args(argv)

    cfg = DiscoveryConfig(
        episodes=args.episodes, per_episode=args.per_episode, seed=args.seed,
        dev_episodes=args.dev_episodes, rounds=args.rounds, top_m=args.top_m,
        top_k=args.top_k, lag=args.lag, n_null=args.n_null,
        skip_eval=args.skip_eval, out_dir=args.out,
    )
    t0 = time.time()
    res = run_discovery(cfg)
    m = res.metrics

    print(f"runtime={m['runtime_seconds']:.0f}s "
          f"candidates={m['config']['n_candidates']} "
          f"accepted={m['accepted_total']}")
    print(f"calibration: tau={m['calibration']['tau']:.4f} "
          f"hit_median={m['calibration']['hit_median']:.4f} "
          f"null_p99={m['calibration']['null_p99']:.4f} "
          f"gate_pass={m['calibration']['hit_gate_pass']}/{m['calibration']['n_hit']}")
    print(f"held-out rediscovered: {m['heldout_found']}/10")
    for h in m["heldout_discovery"]:
        if h["found_round"] is not None:
            print(f"  {h['rule_id']}  round={h['found_round']:>2}  "
                  f"feature={h['feature']}  corr={h['corr']:.4f}")
        else:
            print(f"  {h['rule_id']}  not found")
    print(f"experience-pool matched: {m['known_found']}/20")
    print(f"false positives: {m['false_positive_count']} "
          f"(rate={m['false_positive_rate']:.4f})")
    if m["eval_auc"] and m["eval_auc"]["records"]:
        ea = m["eval_auc"]
        print(f"eval AUC: base={ea['auc_base_mean']:.4f} "
              f"final={ea['auc_final_mean']:.4f} "
              f"diff={ea['diff_mean']:+.4f} (n={ea['n_episodes']})")
    print(f"artifacts in {args.out} "
          f"(total wall {time.time() - t0:.0f}s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
