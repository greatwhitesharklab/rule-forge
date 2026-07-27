"""机制二实验:可写记忆的增量价值——双臂对照(eval/clab_memory_ab.py)。

问题:有策略记忆的闭环(臂 A) vs 无记忆闭环(臂 B),探索效率是否更高?
两臂同世界同种子、同一枚举云端候选表(预排序一次,两臂共用)、同一判卷
器与 tau;**唯一变量 = 策略记忆**:

  臂 A(有记忆)
    1. 死路档案:验证失败方向按 direction_key 归一化归档,后续轮次跳过
       该方向的全部变体(重复提案率 ≈ 0);
    2. regime 经验:跟踪特征×regime 的 IV;优先重提「历史 regime IV 高但
       当前 regime 尚未验证」方向的下一个未提变体;
    3. 声誉演化:已入库特征每轮在最新成熟切片上测 IV,连续 K 轮低于门槛
       → 退役出 GBDT 特征库,方向记死路(regime 不稳)。
  臂 B(无记忆):盲枚举,不记死路、不重提、不退役;GBDT 特征库同样累积。

流式轮次:round r 的 front = dev_episodes * r / R(只见到 front 之前且已
成熟的案例),regime 切换随 front 推进真实发生。判卷在完整 dev 帧上统一
进行(两臂同一 RuleGrader/tau,口径与机制一一致)。

判据(5 种子配对,未发现的规则发现轮次记 R+1):
  PASS = 发现速度配对差(B-A)均值 > 0 且 bootstrap 95% CI 不含 0
         且 累计假阳性配对差(A-B)均值 < 0 且 CI 不含 0。否则 FAIL,如实报。

用法(cwd = experiments/neural-engine):
    uv run python -m eval.clab_memory_ab
"""

from __future__ import annotations

import argparse
import json
import math
import shutil
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

from cloud.contracts import Provenance, TaskPackage, TaskResult
from eval.clab_discovery import (
    RuleFire,
    RuleGrader,
    calibrate_threshold,
    rule_fires_from_world,
)
from eval.curves import bootstrap_ci
from scoring.features import FeatureRegistry
from selflearn import DEFAULT_LGBM_PARAMS, LoopConfig, SelfLearnLoop, StrategyMemory
from selflearn.clab import (
    CLAB_FIELDS,
    CLAB_FIELD_STATEMENTS,
    ClabAutoCloud,
    build_clab_split,
    clab_base_features,
)
from selflearn.features import compile_l2_expression, register_l2_feature
from selflearn.memory import CAUSE_REGIME
from slots import SlotConfig, SlotService
from synth import SyntheticWorld, default_config
from synth.world import WorldData
from verify.metrics import information_value

NOT_FOUND_OFFSET = 1  # 未发现规则的发现轮次 = R + 1


# ---------------------------------------------------------------------------
# 策略记忆(臂 A 的唯一记忆状态)
# ---------------------------------------------------------------------------


class MemoryPolicy:
    """死路档案 + regime 经验 + 声誉退役;臂 B 不使用(为 None)。"""

    def __init__(self, *, retire_k: int = 3, iv_bar: float = 0.1,
                 repropose_p: int = 5) -> None:
        self.retire_k = retire_k
        self.iv_bar = iv_bar
        self.repropose_p = repropose_p
        self.dead: dict[str, str] = {}  # direction -> 死因
        self.dir_regime_iv: dict[str, dict[int, float]] = {}
        self.dir_seen: set[tuple[str, int]] = set()  # (direction, regime)
        self.low_iv_streak: dict[str, int] = {}

    def add_dead(self, direction: str, cause: str) -> None:
        self.dead.setdefault(direction, cause)

    def mark_proposed(self, direction: str, regime: int) -> None:
        self.dir_seen.add((direction, regime))

    def record_regime_iv(self, direction: str, regime: int, iv: float) -> None:
        if not math.isfinite(iv):
            return
        per = self.dir_regime_iv.setdefault(direction, {})
        per[regime] = max(per.get(regime, 0.0), float(iv))

    def repropose_directions(self, current_regime: int) -> list[str]:
        """历史 regime IV 高、当前 regime 未验证、非死路的方向,IV 降序。"""
        out: list[tuple[float, str]] = []
        for d, per in self.dir_regime_iv.items():
            if d in self.dead or (d, current_regime) in self.dir_seen:
                continue
            best = max(per.values())
            if best >= self.iv_bar:
                out.append((best, d))
        out.sort(key=lambda t: (-t[0], t[1]))
        return [d for _, d in out[: self.repropose_p]]

    def track_feature(self, name: str, iv: float) -> bool:
        """声誉跟踪:连续 retire_k 轮 IV 低于 iv_bar → 返回 True(应退役)。"""
        low = not math.isfinite(iv) or iv < self.iv_bar
        streak = self.low_iv_streak.get(name, 0) + 1 if low else 0
        self.low_iv_streak[name] = streak
        return streak >= self.retire_k


class ArmCloud:
    """枚举云端臂实现:两臂共用预排序候选表;policy=None 即臂 B 盲枚举。"""

    provider_name = "clab-auto"
    model_name = "enumerator-v1"

    def __init__(self, ranked: list[dict[str, Any]],
                 policy: MemoryPolicy | None = None) -> None:
        self._ranked = ranked
        self._policy = policy
        self._proposed: set[str] = set()
        self.current_regime = 0  # 由臂驱动器每轮设置
        self.seen_contexts: list[dict[str, Any]] = []

    def _select(self, m: int) -> list[dict[str, Any]]:
        batch: list[dict[str, Any]] = []
        pol = self._policy
        if pol is not None:  # regime 经验:优先重提强方向的下一个未提变体
            for d in pol.repropose_directions(self.current_regime):
                if len(batch) >= m:
                    break
                cand = next(
                    (c for c in self._ranked
                     if c["direction"] == d and c["name"] not in self._proposed),
                    None,
                )
                if cand is not None:
                    batch.append(cand)
                    self._proposed.add(cand["name"])
                    pol.mark_proposed(d, self.current_regime)
        for c in self._ranked:
            if len(batch) >= m:
                break
            if c["name"] in self._proposed:
                continue
            if pol is not None and c["direction"] in pol.dead:
                continue  # 死路方向的全部变体跳过
            batch.append(c)
            self._proposed.add(c["name"])
            if pol is not None:
                pol.mark_proposed(c["direction"], self.current_regime)
        return batch

    def execute(self, task: TaskPackage) -> TaskResult:
        self.seen_contexts.append(dict(task.context))
        m = int(task.constraints.get("max_features", 20))
        features = [
            {"name": c["name"], "expression": c["expression"],
             "rationale": c["rationale"]}
            for c in self._select(m)
        ]
        return TaskResult(
            task_id=task.task_id, task_type=task.task_type,
            content={"features": features},
            provenance=Provenance(
                provider=self.provider_name, model=self.model_name,
                model_version="v1",
                timestamp="ab-experiment", prompt_hash="", cost_tokens=0,
            ),
        )


# ---------------------------------------------------------------------------
# 配置与臂驱动
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ABConfig:
    seeds: tuple[int, ...] = (20260726, 20260727, 20260728, 20260729, 20260730)
    episodes: int = 100
    per_episode: int = 1000
    dev_episodes: int = 60
    rounds: int = 15
    top_m: int = 20
    top_k: int = 20
    dev_holdout_episodes: int = 2
    retire_k: int = 3  # 连续 K 轮低 IV → 退役
    iv_bar: float = 0.1  # 声誉/regime 经验的 IV 门槛(与 §8.4 iv_min 一致)
    repropose_p: int = 5  # 每轮 regime 优先重提的方向数上限
    recovery_tol: float = 0.005  # AUC 恢复容差
    n_null: int = 100
    lgbm_params: dict[str, Any] | None = None
    out_dir: Path = Path("eval/artifacts-clab-ab")


@dataclass
class ArmResult:
    arm: str
    memory: bool
    rounds: list[dict[str, Any]]
    discovery_rounds: dict[str, int]  # rule_id -> 首发现轮次(R+1 = 未发现)
    graded: dict[str, dict[str, Any]]
    final_library: list[str]
    retired: list[str]
    total_proposals: int
    repeat_proposals: int
    fp_cumulative: int
    fp_final: int

    @property
    def repeat_rate(self) -> float:
        return self.repeat_proposals / max(self.total_proposals, 1)

    def as_dict(self) -> dict[str, Any]:
        return {
            "arm": self.arm, "memory": self.memory, "rounds": self.rounds,
            "discovery_rounds": self.discovery_rounds,
            "final_library": self.final_library, "retired": self.retired,
            "total_proposals": self.total_proposals,
            "repeat_proposals": self.repeat_proposals,
            "repeat_rate": round(self.repeat_rate, 6),
            "fp_cumulative": self.fp_cumulative, "fp_final": self.fp_final,
        }


def run_arm(
    world: WorldData,
    ranked: list[dict[str, Any]],
    full_split: Any,
    fires: tuple[RuleFire, ...],
    grader: RuleGrader,
    *,
    cfg: ABConfig,
    use_memory: bool,
    seed: int,
    work_dir: Path,
) -> ArmResult:
    """单臂流式迭代:round r 只见 frontier 之前已成熟的案例。"""
    name2cand = {c["name"]: c for c in ranked}
    work_dir.mkdir(parents=True, exist_ok=True)
    policy = (
        MemoryPolicy(retire_k=cfg.retire_k, iv_bar=cfg.iv_bar,
                     repropose_p=cfg.repropose_p)
        if use_memory else None
    )
    cloud = ArmCloud(ranked, policy)
    memory = StrategyMemory(SlotService(work_dir / "slots.db", SlotConfig()))
    memory.init_field_slots(CLAB_FIELD_STATEMENTS)
    params = dict(cfg.lgbm_params or DEFAULT_LGBM_PARAMS)

    accepted_history: dict[str, dict[str, Any]] = {}  # 曾入库(含已退役)
    alive: dict[str, dict[str, Any]] = {}  # 当前在库
    failed_dirs: set[str] = set()  # 测量重复率用(两臂都记,仅臂 A 用于跳过)
    repeats = 0
    total = 0
    retired_all: list[str] = []
    rounds_out: list[dict[str, Any]] = []

    for r in range(1, cfg.rounds + 1):
        frontier = cfg.dev_episodes * r // cfg.rounds
        if frontier < cfg.dev_holdout_episodes + 2:
            raise ValueError(
                f"round {r} frontier={frontier} 过小:需 >= dev_holdout+2 "
                f"(dev_episodes/rounds 太小)"
            )
        split_r = build_clab_split(world, dev_episodes=frontier)
        current_regime = int(world.casebook.regime_id[split_r.dev_case_idx[-1]])
        cloud.current_regime = current_regime

        registry = FeatureRegistry()  # 每轮重建:库 = 存活特征(退役即消失)
        for name, h in alive.items():
            register_l2_feature(registry, name=name, expression=h["expression"],
                                rationale=h["rationale"], author=h["author"])
        loop_cfg = LoopConfig(
            dev_start="000", dev_end=f"{frontier - 1:03d}",
            eval_start=f"{frontier:03d}", eval_end=f"{cfg.episodes - 1:03d}",
            top_k=cfg.top_k, max_features_per_round=cfg.top_m,
            dev_holdout_episodes=cfg.dev_holdout_episodes,
            lgbm_params=params, seed=seed,
        )
        loop = SelfLearnLoop(split_r.dev_df, config=loop_cfg,
                             base_features=clab_base_features,
                             cloud=cloud, memory=memory)
        rec = loop.run_round(r)

        for p in rec.proposals:
            total += 1
            if name2cand[p.name]["direction"] in failed_dirs:
                repeats += 1  # 早轮已死方向又被提(臂 A 结构上 ≈ 0)
        round_failed: list[str] = []
        for p in rec.proposals:
            d = name2cand[p.name]["direction"]
            if p.verdict == "pass":
                h = {"round": r, "expression": p.expression,
                     "rationale": p.rationale, "direction": d,
                     "iv": p.metrics.get("iv"),
                     "author": f"clab-auto:enumerator-v1#selflearn-r{r:02d}"}
                accepted_history[p.name] = h
                alive[p.name] = h
            else:
                failed_dirs.add(d)
                round_failed.append(p.name)
                if policy is not None:
                    policy.add_dead(d, p.reasons[0] if p.reasons else "fail")

        retired: list[str] = []
        if policy is not None:
            frame_df = split_r.dev_df
            labels_r = frame_df["outcome"].to_numpy().astype(np.int8)
            feat_df = frame_df[list(CLAB_FIELDS)]
            regimes_r = world.casebook.regime_id[split_r.dev_case_idx]
            prev_frontier = cfg.dev_episodes * (r - 1) // cfg.rounds
            newest = frame_df["episode"].to_numpy() >= f"{prev_frontier:03d}"
            for name in list(alive):
                h = alive[name]
                values = compile_l2_expression(h["expression"])(feat_df).to_numpy()
                for g in np.unique(regimes_r):
                    m = regimes_r == g
                    policy.record_regime_iv(
                        h["direction"], int(g),
                        information_value(values[m], labels_r[m]),
                    )
                iv_new = information_value(values[newest], labels_r[newest])
                if policy.track_feature(name, iv_new):
                    retired.append(name)
                    policy.add_dead(h["direction"], CAUSE_REGIME)
                    del alive[name]
        retired_all.extend(retired)
        rounds_out.append({
            "round": r, "frontier": frontier, "regime": current_regime,
            "proposed": [p.name for p in rec.proposals],
            "accepted": list(rec.accepted), "failed": round_failed,
            "retired": retired, "library_size": len(alive),
            "auc_before": rec.auc_before, "auc_after": rec.auc_after,
        })

    # ---- 判卷:完整 dev 帧,两臂同一判卷器/tau ----
    full_feat_df = full_split.dev_df[list(CLAB_FIELDS)]
    graded: dict[str, dict[str, Any]] = {}
    for name, h in accepted_history.items():
        values = compile_l2_expression(h["expression"])(full_feat_df).to_numpy()
        g = grader.grade(name, values)
        graded[name] = {**g.as_dict(), "round": h["round"],
                        "direction": h["direction"], "iv": h["iv"]}
    discovery_rounds: dict[str, int] = {}
    for f in fires:
        best: int | None = None
        for gd in graded.values():
            if gd["best_rule_id"] == f.rule_id and gd["category"] in (
                "rediscovery", "known"
            ):
                best = gd["round"] if best is None else min(best, gd["round"])
        discovery_rounds[f.rule_id] = (
            best if best is not None else cfg.rounds + NOT_FOUND_OFFSET
        )
    fp_cum = sum(1 for gd in graded.values()
                 if gd["category"] == "false_positive")
    fp_final = sum(1 for name in alive
                   if graded[name]["category"] == "false_positive")
    return ArmResult(
        arm="A" if use_memory else "B", memory=use_memory, rounds=rounds_out,
        discovery_rounds=discovery_rounds, graded=graded,
        final_library=sorted(alive), retired=retired_all,
        total_proposals=total, repeat_proposals=repeats,
        fp_cumulative=fp_cum, fp_final=fp_final,
    )


# ---------------------------------------------------------------------------
# 配对统计与再适应
# ---------------------------------------------------------------------------


def paired_speed_stats(
    per_seed: list[dict[str, dict[str, int]]],
    *,
    heldout_ids: list[str],
) -> dict[str, Any]:
    """发现速度配对:diff = round_B - round_A(正 = 臂 A 更快),仅保留池。"""
    diffs = np.asarray(
        [float(s["B"][r] - s["A"][r]) for s in per_seed for r in heldout_ids]
    )
    mean, lo, hi = bootstrap_ci(diffs)
    return {"n": int(diffs.size), "mean": mean, "ci95": [lo, hi],
            "diffs": diffs.tolist()}


def paired_fp_stats(pairs: list[tuple[int, int]]) -> dict[str, Any]:
    """假阳性配对:diff = fp_A - fp_B(负 = 臂 A 更低),按种子配对。"""
    diffs = np.asarray([float(a - b) for a, b in pairs])
    mean, lo, hi = bootstrap_ci(diffs)
    return {"n": int(diffs.size), "mean": mean, "ci95": [lo, hi],
            "diffs": diffs.tolist()}


def readaptation(
    rounds_records: list[dict[str, Any]],
    discovery_rounds: dict[str, int],
    switch_episodes: list[int],
    *,
    dev_episodes: int,
    rounds: int,
    tol: float = 0.005,
) -> dict[str, Any]:
    """regime 切换后再适应:切换后 3 轮内新发现数 + 样本外 AUC 恢复轮数。

    AUC 取每轮 dev 留出集(frontier 尾段)的 auc_after;恢复 = 切换轮之后
    首个 AUC >= 切换前轮 AUC - tol 的轮次间隔,未恢复记剩余轮数。
    """
    per_switch: list[dict[str, Any]] = []
    for e in switch_episodes:
        if not (0 < e < dev_episodes):
            continue
        r_s = e * rounds // dev_episodes + 1  # 首个 frame 覆盖 episode e 的轮次
        if r_s < 2 or r_s > rounds:
            continue
        pre = rounds_records[r_s - 2].get("auc_after")
        if pre is None or not math.isfinite(pre):
            continue
        rec_rounds = rounds - r_s  # 默认:观察窗内未恢复
        for r in range(r_s + 1, rounds + 1):
            auc = rounds_records[r - 1].get("auc_after")
            if auc is not None and math.isfinite(auc) and auc >= pre - tol:
                rec_rounds = r - r_s
                break
        new_disc = sum(
            1 for dr in discovery_rounds.values()
            if r_s < dr <= min(r_s + 3, rounds)
        )
        per_switch.append({
            "switch_episode": e, "switch_round": r_s,
            "recovery_rounds": rec_rounds, "new_discoveries_3r": new_disc,
        })
    rec = [s["recovery_rounds"] for s in per_switch]
    nd = [s["new_discoveries_3r"] for s in per_switch]
    return {
        "per_switch": per_switch,
        "mean_recovery_rounds": float(np.mean(rec)) if rec else float("nan"),
        "mean_new_discoveries": float(np.mean(nd)) if nd else float("nan"),
    }


# ---------------------------------------------------------------------------
# 实验主流程
# ---------------------------------------------------------------------------


def _heldout_ids(fires: tuple[RuleFire, ...]) -> list[str]:
    return [f.rule_id for f in fires if f.pool == "heldout"]


def run_pair(seed: int, cfg: ABConfig, work_root: Path) -> dict[str, Any]:
    """一个世界(种子)上的双臂对照:共享候选表/判卷器/tau。"""
    world = SyntheticWorld(default_config(seed=seed)).run(
        cfg.episodes, cfg.per_episode
    )
    full_split = build_clab_split(world, dev_episodes=cfg.dev_episodes)
    labels = full_split.dev_df["outcome"].to_numpy().astype(np.int8)
    auto = ClabAutoCloud(full_split.dev_df[list(CLAB_FIELDS)], labels, seed=seed)
    ranked = auto.candidates  # 两臂共用同一份预排序候选表
    fires = rule_fires_from_world(world, full_split.dev_case_idx)
    cal = calibrate_threshold(fires, labels, seed=seed, n_null=cfg.n_null)
    grader = RuleGrader(fires, labels, cal.tau)
    switches = [e.episode for e in world.regimes]

    arm_b = run_arm(world, ranked, full_split, fires, grader, cfg=cfg,
                    use_memory=False, seed=seed,
                    work_dir=work_root / f"seed{seed}-B")
    arm_a = run_arm(world, ranked, full_split, fires, grader, cfg=cfg,
                    use_memory=True, seed=seed,
                    work_dir=work_root / f"seed{seed}-A")
    return {
        "seed": seed, "tau": cal.tau, "calibration": cal.as_dict(),
        "n_candidates": len(ranked), "switches": switches,
        "heldout_ids": _heldout_ids(fires),
        "arm_A": arm_a, "arm_B": arm_b,
    }


def _cum_curve(discovery_rounds: dict[str, int], rounds: int,
               prefix: str) -> list[int]:
    """累计发现曲线:curve[r] = 发现轮次 <= r 的规则数(prefix 过滤)。"""
    return [
        sum(1 for rid, dr in discovery_rounds.items()
            if rid.startswith(prefix) and dr <= r)
        for r in range(1, rounds + 1)
    ]


def _fp_cum_curve(graded: dict[str, dict[str, Any]], rounds: int) -> list[int]:
    return [
        sum(1 for gd in graded.values()
            if gd["category"] == "false_positive" and gd["round"] <= r)
        for r in range(1, rounds + 1)
    ]


def _plot(seed_summaries: list[dict[str, Any]], rounds: int, out_png: Path) -> None:
    xs = list(range(1, rounds + 1))
    red_a = np.mean([s["cum_A"] for s in seed_summaries], axis=0)
    red_b = np.mean([s["cum_B"] for s in seed_summaries], axis=0)
    fp_a = np.mean([s["fp_A"] for s in seed_summaries], axis=0)
    fp_b = np.mean([s["fp_B"] for s in seed_summaries], axis=0)
    fig, axes = plt.subplots(1, 2, figsize=(13, 5))
    axes[0].plot(xs, red_a, color="C0", lw=2, marker="o", ms=4,
                 label="arm A (memory)")
    axes[0].plot(xs, red_b, color="C3", lw=2, marker="s", ms=4,
                 label="arm B (blind)")
    axes[0].set_title("held-out rules rediscovered (mean over seeds)")
    axes[0].set_xlabel("round")
    axes[0].legend()
    axes[1].plot(xs, fp_a, color="C0", lw=2, marker="o", ms=4,
                 label="arm A (memory)")
    axes[1].plot(xs, fp_b, color="C3", lw=2, marker="s", ms=4,
                 label="arm B (blind)")
    axes[1].set_title("cumulative false positives (mean over seeds)")
    axes[1].set_xlabel("round")
    axes[1].legend()
    fig.suptitle("Mechanism 2: writable-memory A/B on CLAB-lite")
    fig.tight_layout()
    fig.savefig(out_png, dpi=120)
    plt.close(fig)


def run_experiment(cfg: ABConfig) -> dict[str, Any]:
    t0 = time.time()
    work_root = cfg.out_dir / "work"
    if work_root.exists():
        shutil.rmtree(work_root)
    work_root.mkdir(parents=True, exist_ok=True)
    cfg.out_dir.mkdir(parents=True, exist_ok=True)

    pairs = [run_pair(seed, cfg, work_root) for seed in cfg.seeds]
    heldout_ids = pairs[0]["heldout_ids"]

    speed = paired_speed_stats(
        [{"A": p["arm_A"].discovery_rounds, "B": p["arm_B"].discovery_rounds}
         for p in pairs],
        heldout_ids=heldout_ids,
    )
    fp = paired_fp_stats(
        [(p["arm_A"].fp_cumulative, p["arm_B"].fp_cumulative) for p in pairs]
    )
    fp_final = paired_fp_stats(
        [(p["arm_A"].fp_final, p["arm_B"].fp_final) for p in pairs]
    )

    seed_summaries: list[dict[str, Any]] = []
    readapt_a: list[dict[str, Any]] = []
    readapt_b: list[dict[str, Any]] = []
    seeds_out: list[dict[str, Any]] = []
    for p in pairs:
        a, b = p["arm_A"], p["arm_B"]
        ra = readaptation(a.rounds, a.discovery_rounds, p["switches"],
                          dev_episodes=cfg.dev_episodes, rounds=cfg.rounds,
                          tol=cfg.recovery_tol)
        rb = readaptation(b.rounds, b.discovery_rounds, p["switches"],
                          dev_episodes=cfg.dev_episodes, rounds=cfg.rounds,
                          tol=cfg.recovery_tol)
        readapt_a.append(ra)
        readapt_b.append(rb)
        seed_summaries.append({
            "cum_A": _cum_curve(a.discovery_rounds, cfg.rounds, "HLD-"),
            "cum_B": _cum_curve(b.discovery_rounds, cfg.rounds, "HLD-"),
            "fp_A": _fp_cum_curve(a.graded, cfg.rounds),
            "fp_B": _fp_cum_curve(b.graded, cfg.rounds),
        })
        seeds_out.append({
            "seed": p["seed"], "tau": p["tau"],
            "n_candidates": p["n_candidates"], "switches": p["switches"],
            "arm_A": a.as_dict(), "arm_B": b.as_dict(),
            "readapt_A": ra, "readapt_B": rb,
        })

    def _nanmean(xs: list[float]) -> float:
        arr = np.asarray(xs, dtype=np.float64)
        return float(np.nanmean(arr)) if arr.size else float("nan")

    agg = {
        "heldout_found_A": float(np.mean([
            sum(1 for rid, dr in a.discovery_rounds.items()
                if rid.startswith("HLD-") and dr <= cfg.rounds)
            for a in (p["arm_A"] for p in pairs)
        ])),
        "heldout_found_B": float(np.mean([
            sum(1 for rid, dr in b.discovery_rounds.items()
                if rid.startswith("HLD-") and dr <= cfg.rounds)
            for b in (p["arm_B"] for p in pairs)
        ])),
        "repeat_rate_A": float(np.mean([p["arm_A"].repeat_rate for p in pairs])),
        "repeat_rate_B": float(np.mean([p["arm_B"].repeat_rate for p in pairs])),
        "fp_cum_A": float(np.mean([p["arm_A"].fp_cumulative for p in pairs])),
        "fp_cum_B": float(np.mean([p["arm_B"].fp_cumulative for p in pairs])),
        "fp_final_A": float(np.mean([p["arm_A"].fp_final for p in pairs])),
        "fp_final_B": float(np.mean([p["arm_B"].fp_final for p in pairs])),
        "retired_A": float(np.mean([len(p["arm_A"].retired) for p in pairs])),
        "recovery_rounds_A": _nanmean(
            [r["mean_recovery_rounds"] for r in readapt_a]),
        "recovery_rounds_B": _nanmean(
            [r["mean_recovery_rounds"] for r in readapt_b]),
        "new_disc_3r_A": _nanmean(
            [r["mean_new_discoveries"] for r in readapt_a]),
        "new_disc_3r_B": _nanmean(
            [r["mean_new_discoveries"] for r in readapt_b]),
    }

    speed_pass = speed["mean"] > 0 and speed["ci95"][0] > 0
    fp_pass = fp["mean"] < 0 and fp["ci95"][1] < 0
    verdict = "PASS" if (speed_pass and fp_pass) else "FAIL"

    metrics: dict[str, Any] = {
        "config": {
            "seeds": list(cfg.seeds), "episodes": cfg.episodes,
            "per_episode": cfg.per_episode, "dev_episodes": cfg.dev_episodes,
            "rounds": cfg.rounds, "top_m": cfg.top_m,
            "retire_k": cfg.retire_k, "iv_bar": cfg.iv_bar,
            "repropose_p": cfg.repropose_p,
        },
        "verdict": verdict,
        "criteria": {
            "speed_faster": {"pass": bool(speed_pass),
                             "mean_B_minus_A": speed["mean"],
                             "ci95": speed["ci95"]},
            "fp_lower": {"pass": bool(fp_pass),
                         "mean_A_minus_B": fp["mean"], "ci95": fp["ci95"]},
        },
        "speed_paired": speed,
        "fp_paired": fp,
        "fp_final_paired": fp_final,
        "aggregate": agg,
        "seeds": seeds_out,
        "runtime_seconds": round(time.time() - t0, 1),
    }
    cfg.out_dir.mkdir(parents=True, exist_ok=True)
    (cfg.out_dir / "clab_ab_metrics.json").write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2)
    )
    _plot(seed_summaries, cfg.rounds, cfg.out_dir / "clab_ab_curves.png")
    return metrics


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--seeds", type=int, nargs="+",
                    default=[20260726, 20260727, 20260728, 20260729, 20260730])
    ap.add_argument("--episodes", type=int, default=100)
    ap.add_argument("--per-episode", type=int, default=1000)
    ap.add_argument("--dev-episodes", type=int, default=60)
    ap.add_argument("--rounds", type=int, default=15)
    ap.add_argument("--top-m", type=int, default=20)
    ap.add_argument("--retire-k", type=int, default=3)
    ap.add_argument("--n-null", type=int, default=100)
    ap.add_argument("--out", type=Path, default=Path("eval/artifacts-clab-ab"))
    args = ap.parse_args(argv)

    cfg = ABConfig(
        seeds=tuple(args.seeds), episodes=args.episodes,
        per_episode=args.per_episode, dev_episodes=args.dev_episodes,
        rounds=args.rounds, top_m=args.top_m, retire_k=args.retire_k,
        n_null=args.n_null, out_dir=args.out,
    )
    m = run_experiment(cfg)
    agg = m["aggregate"]
    print(f"runtime={m['runtime_seconds']:.0f}s seeds={len(m['config']['seeds'])}")
    print(f"held-out found: A={agg['heldout_found_A']:.1f} "
          f"B={agg['heldout_found_B']:.1f} (mean of 10)")
    sp = m["speed_paired"]
    print(f"discovery speed paired (B-A rounds): mean={sp['mean']:+.3f} "
          f"CI95=[{sp['ci95'][0]:+.3f},{sp['ci95'][1]:+.3f}] n={sp['n']}")
    fp = m["fp_paired"]
    print(f"false positives cumulative: A={agg['fp_cum_A']:.1f} "
          f"B={agg['fp_cum_B']:.1f}; paired (A-B): mean={fp['mean']:+.3f} "
          f"CI95=[{fp['ci95'][0]:+.3f},{fp['ci95'][1]:+.3f}]")
    ff = m["fp_final_paired"]
    print(f"false positives final library: A={agg['fp_final_A']:.1f} "
          f"B={agg['fp_final_B']:.1f}; paired mean={ff['mean']:+.3f} "
          f"CI95=[{ff['ci95'][0]:+.3f},{ff['ci95'][1]:+.3f}] "
          f"(A retired {agg['retired_A']:.1f} features)")
    print(f"repeat proposal rate: A={agg['repeat_rate_A']:.4f} "
          f"B={agg['repeat_rate_B']:.4f}")
    print(f"readaptation: recovery rounds A={agg['recovery_rounds_A']:.2f} "
          f"B={agg['recovery_rounds_B']:.2f}; new discoveries in 3r "
          f"A={agg['new_disc_3r_A']:.2f} B={agg['new_disc_3r_B']:.2f}")
    print(f"VERDICT: {m['verdict']} "
          f"(speed_faster={m['criteria']['speed_faster']['pass']}, "
          f"fp_lower={m['criteria']['fp_lower']['pass']}) "
          f"artifacts in {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
