"""自学习特征迭代闭环验收(新定位:记忆用在训练/迭代阶段,推理由 GBDT 执行)。

流程:
1. dev 窗(默认 2013-01~2015-12)跑 N 轮迭代(GBDT 指路 → 策略记忆查询 →
   G1 出题 → 云端 agent/replay → 验证 → 入库/死路 → GBDT 重训),全程不碰
   eval 窗;每轮一行 jsonl 迭代日志。
2. 最终评估:eval 窗(2016-01~2018-12)滚动 GBDT,**最终特征库 vs 24 字段
   基线**(同窗/同参/同种子/同训练行),逐 episode AUC + 利润。
   训练窗只允许 episode <= t-lag(dev + 更早 eval episode,标签均为成熟结局)。
3. 判据:AUC 增量均值 > 0 且 bootstrap 95% CI 不含 0 = PASS;
   利润增量报告(次要指标);一轮都没入库新特征 = BASELINE(基线对照,不判)。

用法(cwd = experiments/neural-engine):
    uv run python -m eval.selflearn_acceptance --cloud replay --rounds 3
    uv run python -m eval.selflearn_acceptance --cloud agent --rounds 1 \\
        --bridge-timeout 1800
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
import time
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.metrics import roc_auc_score

from cloud.agent_bridge import AgentBridgeProvider
from eval.curves import bootstrap_ci
from eval.lending_acceptance import LGBM_PARAMS, build_base_features
from eval.lending_profit import approve_mask, load_profit_fields, profit_per_case
from lending.prepare import FEATURE_COLS
from selflearn import (
    LoopConfig,
    ReplayProvider,
    SelfLearnLoop,
    StrategyMemory,
    load_replay,
)
from selflearn.gbdt import train_gbdt
from slots import SlotConfig, SlotService
from verify import backtest_frame_from_data

_REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_PARQUET = _REPO_ROOT / "data" / "lendingclub" / "lendingclub_episodes.parquet"
DEFAULT_REPLAY = Path(__file__).resolve().parent.parent / "selflearn" / "replay_features.json"


# --------------------------------------------------------------------- 最终评估


def rolling_eval(
    df: pd.DataFrame,
    X_base: np.ndarray,
    X_final: np.ndarray,
    *,
    eval_start: str,
    eval_end: str,
    lag: int,
    window: int,
    min_train: int,
    max_train_rows: int,
    approve_rate: float,
    seed: int,
    total_pymnt: np.ndarray | None,
) -> list[dict]:
    """eval 窗滚动对比:臂 baseline(24 字段基线)vs 臂 final(基线 + 入库 L2)。

    两臂同训练行/同参/同种子,唯一变量 = 特征库。逐 episode AUC + 利润。
    """
    episodes = sorted(df["episode"].unique())
    ep_codes = df["episode"].map({e: i for i, e in enumerate(episodes)}).to_numpy()
    y = df["outcome"].to_numpy().astype(np.int8)
    amnt = df["loan_amnt"].to_numpy(dtype=np.float64)
    records: list[dict] = []

    for t, ep in enumerate(episodes):
        if not (eval_start <= ep <= eval_end):
            continue
        hi = t - lag
        lo = max(0, hi - window + 1)
        if hi < 0:
            continue
        idx_tr = np.nonzero((ep_codes >= lo) & (ep_codes <= hi))[0]
        if len(idx_tr) > max_train_rows:
            idx_tr = idx_tr[-max_train_rows:]  # df 按 episode 有序
        idx_te = np.nonzero(ep_codes == t)[0]
        y_tr = y[idx_tr]
        rec: dict = {"episode": ep, "n": int(len(idx_te)),
                     "n_train": int(len(idx_tr))}
        if len(idx_tr) < min_train or len(np.unique(y_tr)) < 2:
            rec["cold_start"] = True
            records.append(rec)
            continue
        y_te = y[idx_te]
        if len(np.unique(y_te)) < 2:
            rec["cold_start"] = True
            records.append(rec)
            continue
        proba_base = train_gbdt(X_base[idx_tr], y_tr, params=LGBM_PARAMS,
                                seed=seed).predict_proba(X_base[idx_te])[:, 1]
        proba_final = train_gbdt(X_final[idx_tr], y_tr, params=LGBM_PARAMS,
                                 seed=seed).predict_proba(X_final[idx_te])[:, 1]
        rec["auc_base"] = float(roc_auc_score(y_te, proba_base))
        rec["auc_final"] = float(roc_auc_score(y_te, proba_final))
        if total_pymnt is not None:
            n_appr = int(np.floor(approve_rate * len(idx_te)))
            rec["profit_base"] = float(profit_per_case(
                approve_mask(proba_base, n_appr), y_te, amnt[idx_te],
                total_pymnt[idx_te]).sum())
            rec["profit_final"] = float(profit_per_case(
                approve_mask(proba_final, n_appr), y_te, amnt[idx_te],
                total_pymnt[idx_te]).sum())
        records.append(rec)
    return records


def _paired(records: list[dict], key_a: str, key_b: str) -> np.ndarray:
    return np.asarray(
        [r[key_b] - r[key_a] for r in records if key_a in r and key_b in r],
        dtype=np.float64,
    )


def _plot(records: list[dict], out_png: Path) -> None:
    eps = [r["episode"] for r in records]
    auc_a = np.array([r.get("auc_base", np.nan) for r in records])
    auc_b = np.array([r.get("auc_final", np.nan) for r in records])
    fig, axes = plt.subplots(3, 1, figsize=(13, 10), sharex=True)
    x = np.arange(len(eps))
    axes[0].plot(x, auc_a, color="C1", lw=1.2, label="baseline (24-field)")
    axes[0].plot(x, auc_b, color="C0", lw=1.2, label="final (+self-learned L2)")
    axes[0].set_ylabel("AUC")
    axes[0].legend()
    axes[0].set_title("Eval-window per-episode test AUC (rolling GBDT)")
    axes[1].bar(x, auc_b - auc_a, color=np.where((auc_b - auc_a) >= 0, "C0", "C3"))
    axes[1].axhline(0.0, color="gray", lw=0.8)
    axes[1].set_ylabel("AUC diff (final-base)")
    pa = np.array([r.get("profit_base", np.nan) for r in records], dtype=float)
    pb = np.array([r.get("profit_final", np.nan) for r in records], dtype=float)
    axes[2].plot(x, np.nancumsum(pb - pa), color="C2")
    axes[2].axhline(0.0, color="gray", lw=0.8)
    axes[2].set_ylabel("cum. profit diff ($)")
    step = max(1, len(eps) // 12)
    axes[2].set_xticks(x[::step])
    axes[2].set_xticklabels(eps[::step], rotation=45)
    fig.tight_layout()
    fig.savefig(out_png, dpi=120)
    plt.close(fig)


# ------------------------------------------------------------------------- main


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--parquet", default=str(DEFAULT_PARQUET))
    ap.add_argument("--cloud", choices=["replay", "agent"], default="replay")
    ap.add_argument("--replay-file", default=str(DEFAULT_REPLAY))
    ap.add_argument("--rounds", type=int, default=3)
    ap.add_argument("--dev-start", default="2013-01")
    ap.add_argument("--dev-end", default="2015-12")
    ap.add_argument("--eval-start", default="2016-01")
    ap.add_argument("--eval-end", default="2018-12")
    ap.add_argument("--top-k", type=int, default=20)
    ap.add_argument("--max-features-per-round", type=int, default=3)
    ap.add_argument("--max-train-rows", type=int, default=200_000,
                    help="dev 窗迭代 GBDT 训练行数上限")
    ap.add_argument("--lag", type=int, default=3)
    ap.add_argument("--window", type=int, default=12)
    ap.add_argument("--min-train", type=int, default=2000)
    ap.add_argument("--eval-max-train-rows", type=int, default=120_000,
                    help="eval 窗滚动评估训练行数上限")
    ap.add_argument("--approve-rate", type=float, default=0.7)
    ap.add_argument("--encoder", choices=["fake", "real"], default="fake",
                    help="策略槽检索 encoder;real = Qwen3-Embedding-0.6B")
    ap.add_argument("--seed", type=int, default=20260727)
    ap.add_argument("--skip-profit", action="store_true")
    ap.add_argument("--bridge-dir", default=None,
                    help="agent 模式的 bridge 目录(默认 cloud/bridge)")
    ap.add_argument("--bridge-timeout", type=float, default=600.0)
    ap.add_argument("--out", type=Path, default=Path("eval/artifacts-selflearn"))
    args = ap.parse_args(argv)

    t0 = time.time()
    df = pd.read_parquet(args.parquet)
    df = df.sort_values("episode", kind="stable").reset_index(drop=True)
    print(f"rows={len(df):,} episodes={df['episode'].nunique()} "
          f"bad_rate={df['outcome'].mean():.4f}")

    # ---------- 1. dev 窗迭代 ----------
    dev_df = df.loc[
        (df["episode"] >= args.dev_start) & (df["episode"] <= args.dev_end)
    ].copy()
    print(f"dev window [{args.dev_start},{args.dev_end}]: {len(dev_df):,} rows, "
          f"{dev_df['episode'].nunique()} episodes")

    work = args.out / "work"
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True, exist_ok=True)

    encode_fn = None
    if args.encoder == "real":
        from embed import Embedder

        encode_fn = Embedder()._encode
        print("encoder=real (Qwen3-Embedding-0.6B)")

    memory = StrategyMemory(SlotService(work / "slots.db", SlotConfig()),
                            encode_fn=encode_fn)
    n_fields = memory.init_field_slots(FEATURE_COLS)
    print(f"strategy memory: {n_fields} field slots initialized (active)")

    if args.cloud == "replay":
        cloud = ReplayProvider(load_replay(args.replay_file),
                               source=Path(args.replay_file).name)
        print(f"cloud=replay file={args.replay_file}")
    else:
        cloud = AgentBridgeProvider(
            bridge_dir=args.bridge_dir, timeout_s=args.bridge_timeout,
        )
        print(f"cloud=agent bridge_dir={cloud._outbox.parent} "
              f"timeout={args.bridge_timeout:.0f}s")

    cfg = LoopConfig(
        dev_start=args.dev_start, dev_end=args.dev_end,
        eval_start=args.eval_start, eval_end=args.eval_end,
        top_k=args.top_k, max_features_per_round=args.max_features_per_round,
        max_train_rows=args.max_train_rows, seed=args.seed,
    )
    loop = SelfLearnLoop(dev_df, config=cfg, base_features=build_base_features,
                         cloud=cloud, memory=memory)
    records = loop.run(args.rounds)
    memory.service.persist()

    args.out.mkdir(parents=True, exist_ok=True)
    with open(args.out / "iteration_log.jsonl", "w", encoding="utf-8") as fh:
        for r in records:
            fh.write(json.dumps(r.as_dict(), ensure_ascii=False) + "\n")
    accepted = [name for r in records for name in r.accepted]
    print(f"iteration done ({time.time() - t0:.0f}s): "
          f"{len(accepted)} features accepted {accepted}; "
          f"dev AUC path: "
          + " -> ".join(f"{r.auc_after:.4f}" for r in records if r.auc_after))

    # ---------- 2. eval 窗最终评估 ----------
    feat_df, _ = backtest_frame_from_data(df)  # 剥离 outcome/episode
    X_base, _base_names = build_base_features(feat_df)
    if len(loop.registry):
        X_final = np.hstack([X_base, loop.registry.compute(feat_df).to_numpy()])
    else:
        X_final = X_base

    pymnt = None
    if not args.skip_profit:
        pymnt = load_profit_fields(df).to_numpy(dtype=np.float64)
        print(f"profit fields joined ({time.time() - t0:.0f}s)")

    eval_records = rolling_eval(
        df, X_base, X_final,
        eval_start=args.eval_start, eval_end=args.eval_end,
        lag=args.lag, window=args.window, min_train=args.min_train,
        max_train_rows=args.eval_max_train_rows,
        approve_rate=args.approve_rate, seed=args.seed, total_pymnt=pymnt,
    )

    # ---------- 3. 判据 ----------
    auc_diffs = _paired(eval_records, "auc_base", "auc_final")
    mean_d, lo_d, hi_d = bootstrap_ci(auc_diffs)
    profit_diffs = _paired(eval_records, "profit_base", "profit_final")
    mean_p, lo_p, hi_p = bootstrap_ci(profit_diffs)
    auc_base = [r["auc_base"] for r in eval_records if "auc_base" in r]
    auc_final = [r["auc_final"] for r in eval_records if "auc_final" in r]

    if not accepted:
        verdict = "BASELINE"  # 无新特征入库:基线对照,两臂定义性相等,不判
    else:
        ok = len(auc_diffs) >= 10 and mean_d > 0.0 and lo_d > 0.0
        verdict = "PASS" if ok else "FAIL"

    metrics = {
        "config": {
            **{k: getattr(cfg, k) for k in (
                "dev_start", "dev_end", "eval_start", "eval_end", "top_k",
                "max_features_per_round", "corr_max", "max_train_rows",
                "dev_holdout_episodes", "seed")},
            "cloud": args.cloud, "rounds": args.rounds, "lag": args.lag,
            "window": args.window, "eval_max_train_rows": args.eval_max_train_rows,
            "encoder": args.encoder, "n_rows": len(df),
        },
        "verdict": verdict,
        "accepted_features": [
            {"name": p.name, "expression": p.expression, "rationale": p.rationale}
            for r in records for p in r.proposals if p.name in r.accepted
        ],
        "dev_auc_path": [
            {"round": r.round_no, "before": r.auc_before, "after": r.auc_after}
            for r in records
        ],
        "auc": {
            "baseline_mean": float(np.mean(auc_base)) if auc_base else None,
            "final_mean": float(np.mean(auc_final)) if auc_final else None,
            "diff_mean": mean_d, "diff_ci95": [lo_d, hi_d],
            "n_episodes_paired": len(auc_diffs),
        },
        "profit": {
            "baseline_total": float(np.nansum(
                [r.get("profit_base", np.nan) for r in eval_records])),
            "final_total": float(np.nansum(
                [r.get("profit_final", np.nan) for r in eval_records])),
            "diff_mean_per_episode": mean_p, "diff_ci95": [lo_p, hi_p],
        },
        "eval_records": eval_records,
        "runtime_seconds": round(time.time() - t0, 1),
    }
    (args.out / "selflearn_metrics.json").write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2)
    )
    _plot(eval_records, args.out / "selflearn_auc_curves.png")

    print(f"eval episodes paired={len(auc_diffs)} "
          f"runtime={metrics['runtime_seconds']:.0f}s")
    print(f"AUC  baseline={metrics['auc']['baseline_mean']:.4f} "
          f"final={metrics['auc']['final_mean']:.4f} "
          f"diff={mean_d:+.4f} CI95=[{lo_d:+.4f},{hi_d:+.4f}]")
    if pymnt is not None:
        print(f"profit baseline={metrics['profit']['baseline_total']:+,.0f} "
              f"final={metrics['profit']['final_total']:+,.0f} "
              f"diff/ep={mean_p:+,.0f} CI95=[{lo_p:+,.0f},{hi_p:+,.0f}]")
    print(f"VERDICT: {verdict} (artifacts in {args.out})")
    return 0 if verdict in ("PASS", "BASELINE") else 1


if __name__ == "__main__":
    sys.exit(main())
