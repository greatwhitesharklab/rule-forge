"""LendingClub 两臂对照实验:可写记忆作为 GBDT 特征(L3)。

臂 A(baseline):L0/L1 特征(白名单数值字段 + 三个比率衍生 + 低基数类别
one-hot);臂 B(+memory):A 的特征 + 三个 L3 记忆特征(memory_bad_rate /
memory_hit_count / memory_max_w),在 registry.compute 之外的 enrich 步骤
注入(scoring 包不动)。两臂 GBDT 滚动训练数据窗完全一致(同成熟案例、同
窗口、同超参、同种子),唯一变量 = 输入特征是否含记忆特征。

时间红线(§8.3 铁律二):episode t 决策时,记忆库只含写入滞后 LAG 个
episode 的成熟案例;两臂训练标签同样只来自 episode <= t-LAG。臂 B 训练
用的记忆特征是该案例**决策时刻** enrich 的冻结值(线上线下一致,§8.3
铁律一),不是事后用未来记忆重算。

利润:total_pymnt 仅从原始 CSV join 回算真实损益(approve&good:
pymnt-amnt;approve&bad: -(amnt-pymnt);reject: 0);两臂 approve 笔数逐
episode 对齐(臂 B 批准与臂 A 相同的笔数),公平比较。

PASS 判据:逐 episode 配对 test AUC 增量(B-A)均值 > 0 且 bootstrap
95% CI 不含 0;利润增量同样报告(次要指标,不要求显著)。

用法(cwd = experiments/neural-engine):
    uv run python -m eval.lending_acceptance [--since 2008-01] [--lag 3]
        [--window 12] [--encoder auto|fake|real] [--out eval/artifacts-lending]
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import lightgbm as lgb
import numpy as np
import pandas as pd
from sklearn.metrics import roc_auc_score

from embed import Embedder
from eval.curves import bootstrap_ci
from eval.lending_canon import canonical_series
from eval.lending_memory import LendingMemory
from eval.lending_profit import approve_mask, load_profit_fields, profit_per_case
from scoring.scorer import _ks_stat
from slots import SlotConfig, SlotService

_REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_PARQUET = _REPO_ROOT / "data" / "lendingclub" / "lendingclub_episodes.parquet"

MEM_FEATURES = ("memory_bad_rate", "memory_hit_count", "memory_max_w")

# 臂 A 特征:L0 白名单数值字段 + L1 比率衍生 + 低基数类别 one-hot。
NUMERIC_COLS = [
    "loan_amnt", "int_rate", "installment", "annual_inc", "dti",
    "delinq_2yrs", "inq_last_6mths", "open_acc", "pub_rec",
    "revol_bal", "revol_util", "total_acc",
    "credit_history_months", "term_months", "emp_length_years",
]
CATEGORICAL_COLS = [
    "grade", "sub_grade", "term", "home_ownership",
    "verification_status", "purpose", "application_type", "addr_state",
]

LGBM_PARAMS: dict[str, object] = {
    "n_estimators": 120,
    "learning_rate": 0.08,
    "num_leaves": 15,
    "min_child_samples": 40,
    "subsample": 0.9,
    "subsample_freq": 1,
    "colsample_bytree": 0.9,
    "random_state": 20260726,
    "deterministic": True,
    "force_col_wise": True,
    "n_jobs": -1,
    "verbose": -1,
}

# 真 encoder 实测 ~3 文本/s(CPU, Qwen3-Embedding-0.6B);auto 模式下唯一
# 文本数超过该预算即回退 hash_encode。
REAL_ENCODER_RATE = 3.0
REAL_ENCODER_BUDGET_SEC = 240.0


@dataclass(frozen=True)
class ExperimentConfig:
    eval_start: int = 24  # 前段暖机:充记忆 + 首个 GBDT 训练窗
    lag: int = 3  # 写入滞后(贷后结局延迟),episode 数
    window: int = 12  # GBDT 滚动训练窗(episode 数,两臂一致)
    min_train: int = 2000  # 冷启动阈值
    max_train_rows: int = 120_000  # 训练行数上限(取最近)
    approve_rate: float = 0.7  # 逐 episode approve 率(两臂对齐)
    seed: int = 20260726
    trace: bool = False  # 测试用:记录训练案例集/冻结特征


@dataclass
class ExperimentResult:
    records: list[dict]
    enriched_features: dict[str, tuple] = field(default_factory=dict)
    importance_B: pd.Series | None = None
    memory_stats: dict = field(default_factory=dict)
    runtime_seconds: float = 0.0


def build_base_features(df: pd.DataFrame) -> tuple[np.ndarray, list[str]]:
    """臂 A 的 L0/L1 特征矩阵(float32)。类别 one-hot 词表取自全量数据的
    schema(类别集合是模式知识,不含标签信息);NaN 保留给 LightGBM 原生处理。
    """
    parts: list[np.ndarray] = []
    names: list[str] = []
    num = df[NUMERIC_COLS].apply(pd.to_numeric, errors="coerce").astype(np.float32)
    parts.append(num.to_numpy())
    names.extend(NUMERIC_COLS)
    inc = num["annual_inc"].to_numpy()
    l1 = np.column_stack([
        num["loan_amnt"].to_numpy() / (inc + 1.0),
        num["installment"].to_numpy() * 12.0 / (inc + 1.0),
        num["delinq_2yrs"].to_numpy()
        / (num["credit_history_months"].to_numpy() / 12.0 + 1.0),
    ]).astype(np.float32)
    parts.append(l1)
    names.extend(["loan_to_income", "installment_to_income", "delinq_per_year"])
    cat = df[CATEGORICAL_COLS].astype("category")
    dummies = pd.get_dummies(cat, dtype=np.float32, dummy_na=False)
    parts.append(dummies.to_numpy())
    names.extend(dummies.columns.tolist())
    return np.concatenate(parts, axis=1), names


def _fit_predict(X_tr, y_tr, X_te, seed: int):
    params = dict(LGBM_PARAMS, random_state=seed)
    model = lgb.LGBMClassifier(**params)
    model.fit(X_tr, y_tr)
    return model.predict_proba(X_te)[:, 1], model


def run_experiment(
    cfg: ExperimentConfig,
    df: pd.DataFrame,
    total_pymnt: pd.Series | np.ndarray | None = None,
    work_dir: Path | str = "eval/artifacts-lending/work",
    encode_fn=None,
    day_hook=None,
) -> ExperimentResult:
    """滚动 episode 主循环:日间检索打分 -> 夜间写槽 + 声誉回流。"""
    t0 = time.time()
    work_dir = Path(work_dir)
    work_dir.mkdir(parents=True, exist_ok=True)
    df = df.sort_values("episode", kind="stable").reset_index(drop=True)
    episodes = sorted(df["episode"].unique())
    ep_codes = df["episode"].map({e: i for i, e in enumerate(episodes)}).to_numpy()
    y = df["outcome"].to_numpy().astype(np.int8)
    canon = canonical_series(df).to_numpy()
    emp = df["emp_title_norm"].fillna("").to_numpy()
    case_ids = df.index.map(str).to_numpy()
    X_base, base_names = build_base_features(df)
    feature_names_B = base_names + list(MEM_FEATURES)
    pymnt = None if total_pymnt is None else np.asarray(total_pymnt, dtype=np.float64)

    service = SlotService(work_dir / "slots.db", SlotConfig())
    mem = LendingMemory(service, encode_fn=encode_fn)

    frozen = np.full((len(df), len(MEM_FEATURES)), np.nan)
    records: list[dict] = []
    enriched_trace: dict[str, tuple] = {}
    importances: list[np.ndarray] = []
    cum_good = 0.0
    cum_n = 0

    for t, ep in enumerate(episodes):
        idx_t = np.nonzero(ep_codes == t)[0]
        if day_hook is not None:
            day_hook(t, ep, mem)
        # ---- 日间:检索(只读)-> 臂 B 记忆特征(as-of t,冻结供日后训练) ----
        feats = mem.enrich(canon[idx_t].tolist(), case_ids[idx_t].tolist())
        frozen[idx_t] = feats
        if cfg.trace:
            for i, cid in zip(idx_t, case_ids[idx_t]):
                enriched_trace[cid] = tuple(float(v) for v in frozen[i])

        # ---- 两臂训练 + 打分(标签只允许 episode <= t-lag) ----
        hi = t - cfg.lag
        lo = max(0, hi - cfg.window + 1)
        rec: dict = {"episode": ep, "n": int(len(idx_t)), "cold_start": True}
        if t >= cfg.eval_start and hi >= 0:
            idx_tr = np.nonzero((ep_codes >= lo) & (ep_codes <= hi))[0]
            if len(idx_tr) > cfg.max_train_rows:
                idx_tr = idx_tr[-cfg.max_train_rows:]  # df 按 episode 有序
            y_tr = y[idx_tr]
            if len(idx_tr) >= cfg.min_train and len(np.unique(y_tr)) == 2:
                Xa_tr = X_base[idx_tr]
                Xb_tr = np.hstack([Xa_tr, frozen[idx_tr]])
                proba_a, _m_a = _fit_predict(Xa_tr, y_tr, X_base[idx_t], cfg.seed)
                proba_b, m_b = _fit_predict(
                    Xb_tr, y_tr,
                    np.hstack([X_base[idx_t], frozen[idx_t]]),
                    cfg.seed,
                )
                importances.append(m_b.feature_importances_.astype(np.float64))
                rec.update(cold_start=False, n_train=int(len(idx_tr)))
                y_t = y[idx_t]
                if len(np.unique(y_t)) == 2:
                    rec["auc_a"] = float(roc_auc_score(y_t, proba_a))
                    rec["auc_b"] = float(roc_auc_score(y_t, proba_b))
                    rec["ks_a"] = float(_ks_stat(y_t, proba_a))
                    rec["ks_b"] = float(_ks_stat(y_t, proba_b))
                if pymnt is not None:
                    n_appr = int(np.floor(cfg.approve_rate * len(idx_t)))
                    ma = approve_mask(proba_a, n_appr)
                    mb = approve_mask(proba_b, n_appr)
                    rec["profit_a"] = float(profit_per_case(
                        ma, y_t, df["loan_amnt"].to_numpy()[idx_t],
                        pymnt[idx_t]).sum())
                    rec["profit_b"] = float(profit_per_case(
                        mb, y_t, df["loan_amnt"].to_numpy()[idx_t],
                        pymnt[idx_t]).sum())
                    rec["n_approve"] = n_appr
                if cfg.trace:
                    rec["train_case_ids_A"] = case_ids[idx_tr].tolist()
                    rec["train_case_ids_B"] = case_ids[idx_tr].tolist()
                    rec["train_episodes"] = sorted(
                        {episodes[c] for c in ep_codes[idx_tr]}
                    )
                    rec["train_memory_features"] = [
                        (case_ids[i], tuple(float(v) for v in frozen[i]))
                        for i in idx_tr[:50]  # 抽样 50 条钉冻结值
                    ]
        records.append(rec)

        # ---- 夜间:episode t-lag 成熟案例写槽 + 声誉回流 ----
        u = t - cfg.lag
        if u >= 0:
            idx_u = np.nonzero(ep_codes == u)[0]
            cum_good += float((y[idx_u] == 0).sum())
            cum_n += len(idx_u)
            mem.set_prior(1.0 - cum_good / cum_n)  # 成熟案例滚动 bad rate
            mem.nightly_write(
                canon[idx_u].tolist(),
                ["bad" if v else "good" for v in y[idx_u]],
                emp[idx_u].tolist(),
                case_ids[idx_u].tolist(),
                episodes[u],
            )
            mem.nightly_credit(
                case_ids[idx_u].tolist(),
                ["bad" if v else "good" for v in y[idx_u]],
            )

    imp_B = None
    if importances:
        imp_B = pd.Series(
            np.mean(importances, axis=0), index=feature_names_B
        ).sort_values(ascending=False)
    runtime = time.time() - t0
    mem_stats = {
        "n_slots": mem.n_slots,
        "embed_key_cache": len(mem._key_cache),
        "embed_value_cache": len(mem._val_cache),
        "prior_final": mem.prior_bad_rate,
    }
    service.close()
    return ExperimentResult(records, enriched_trace, imp_B, mem_stats, runtime)


def _paired(records: list[dict], key_a: str, key_b: str) -> np.ndarray:
    diffs = [
        r[key_b] - r[key_a] for r in records
        if key_a in r and key_b in r
    ]
    return np.asarray(diffs, dtype=np.float64)


def _plot(records: list[dict], out_png: Path) -> None:
    eps = [r["episode"] for r in records]
    auc_a = [r.get("auc_a", np.nan) for r in records]
    auc_b = [r.get("auc_b", np.nan) for r in records]
    fig, axes = plt.subplots(3, 1, figsize=(13, 10), sharex=True)
    x = np.arange(len(eps))
    axes[0].plot(x, auc_a, color="C1", lw=1.2, label="arm A (baseline)")
    axes[0].plot(x, auc_b, color="C0", lw=1.2, label="arm B (+memory)")
    axes[0].set_ylabel("AUC")
    axes[0].legend()
    axes[0].set_title("Per-episode test AUC (rolling GBDT)")
    diff = np.asarray(auc_b) - np.asarray(auc_a)
    axes[1].bar(x, diff, color=np.where(diff >= 0, "C0", "C3"))
    axes[1].axhline(0.0, color="gray", lw=0.8)
    axes[1].set_ylabel("AUC diff (B-A)")
    pa = np.array([r.get("profit_a", np.nan) for r in records], dtype=float)
    pb = np.array([r.get("profit_b", np.nan) for r in records], dtype=float)
    axes[2].plot(x, np.nancumsum(pb - pa), color="C2")
    axes[2].axhline(0.0, color="gray", lw=0.8)
    axes[2].set_ylabel("cum. profit diff ($)")
    step = max(1, len(eps) // 12)
    axes[2].set_xticks(x[::step])
    axes[2].set_xticklabels(eps[::step], rotation=45)
    fig.tight_layout()
    fig.savefig(out_png, dpi=120)
    plt.close(fig)


def _encode_fn_for(mode: str, n_unique: int):
    """auto:唯一文本编码预计耗时在预算内用真 Qwen3,否则 hash_encode。"""
    if mode == "fake":
        return None, "fake (hash_encode)"
    est = n_unique / REAL_ENCODER_RATE
    if mode == "real" or (mode == "auto" and est <= REAL_ENCODER_BUDGET_SEC):
        emb = Embedder()
        return emb._encode, f"real (Qwen3-Embedding-0.6B, ~{est:.0f}s est.)"
    return None, f"fake (hash_encode; real would need ~{est:.0f}s > budget)"


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="LendingClub L3-memory two-arm experiment")
    ap.add_argument("--parquet", default=str(DEFAULT_PARQUET))
    ap.add_argument("--since", default="2008-01")
    ap.add_argument("--per-episode", type=int, default=0,
                    help="每 episode 额外降采样上限(0 = 用 parquet 原样)")
    ap.add_argument("--eval-start", type=int, default=24)
    ap.add_argument("--lag", type=int, default=3)
    ap.add_argument("--window", type=int, default=12)
    ap.add_argument("--max-train-rows", type=int, default=120000)
    ap.add_argument("--min-train", type=int, default=2000)
    ap.add_argument("--approve-rate", type=float, default=0.7)
    ap.add_argument("--encoder", choices=["auto", "fake", "real"], default="auto")
    ap.add_argument("--seed", type=int, default=20260726)
    ap.add_argument("--skip-profit", action="store_true")
    ap.add_argument("--out", type=Path, default=Path("eval/artifacts-lending"))
    args = ap.parse_args(argv)

    df = pd.read_parquet(args.parquet)
    if args.since:
        df = df.loc[df["episode"] >= args.since].copy()
    if args.per_episode:
        counts = df.groupby("episode", observed=True).size()
        big = counts[counts > args.per_episode].index
        if len(big):
            parts = [df[~df["episode"].isin(big)]]
            parts.append(
                df[df["episode"].isin(big)]
                .groupby("episode", observed=True)
                .sample(n=args.per_episode, random_state=args.seed)
            )
            df = pd.concat(parts)
    df = df.sort_values("episode", kind="stable").reset_index(drop=True)
    print(f"rows={len(df):,} episodes={df['episode'].nunique()} "
          f"bad_rate={df['outcome'].mean():.4f}")

    t0 = time.time()
    canon_preview = canonical_series(df)
    n_unique = int(canon_preview.nunique())
    encode_fn, encoder_desc = _encode_fn_for(args.encoder, n_unique)
    print(f"canonical unique texts={n_unique:,}; encoder={encoder_desc}")

    pymnt = None
    if not args.skip_profit:
        pymnt = load_profit_fields(
            df, since=None if args.since.lower() == "none" else args.since,
        )
        print(f"profit fields joined ({time.time() - t0:.0f}s)")

    cfg = ExperimentConfig(
        eval_start=args.eval_start, lag=args.lag, window=args.window,
        min_train=args.min_train, max_train_rows=args.max_train_rows,
        approve_rate=args.approve_rate, seed=args.seed,
    )
    work = args.out / "work"
    if work.exists():
        shutil.rmtree(work)
    res = run_experiment(cfg, df, total_pymnt=pymnt, work_dir=work,
                         encode_fn=encode_fn)

    auc_diffs = _paired(res.records, "auc_a", "auc_b")
    mean_d, lo_d, hi_d = bootstrap_ci(auc_diffs)
    profit_diffs = _paired(res.records, "profit_a", "profit_b")
    mean_p, lo_p, hi_p = bootstrap_ci(profit_diffs)
    ks_diffs = _paired(res.records, "ks_a", "ks_b")
    mean_k, lo_k, hi_k = bootstrap_ci(ks_diffs)
    auc_a = [r["auc_a"] for r in res.records if "auc_a" in r]
    auc_b = [r["auc_b"] for r in res.records if "auc_b" in r]
    passed = bool(len(auc_diffs) >= 10 and mean_d > 0.0 and lo_d > 0.0)

    metrics = {
        "config": {**vars(cfg), "since": args.since,
                   "per_episode": args.per_episode, "encoder": encoder_desc,
                   "n_rows": len(df), "n_unique_canonical": n_unique},
        "verdict": "PASS" if passed else "FAIL",
        "auc": {
            "arm_A_mean": float(np.mean(auc_a)) if auc_a else None,
            "arm_B_mean": float(np.mean(auc_b)) if auc_b else None,
            "diff_mean": mean_d, "diff_ci95": [lo_d, hi_d],
            "n_episodes_paired": len(auc_diffs),
        },
        "ks": {"diff_mean": mean_k, "diff_ci95": [lo_k, hi_k]},
        "profit": {
            "arm_A_total": float(np.nansum(
                [r.get("profit_a", np.nan) for r in res.records])),
            "arm_B_total": float(np.nansum(
                [r.get("profit_b", np.nan) for r in res.records])),
            "diff_mean_per_episode": mean_p, "diff_ci95": [lo_p, hi_p],
        },
        "memory_stats": res.memory_stats,
        "importance_B_top20": (
            res.importance_B.head(20).round(1).to_dict()
            if res.importance_B is not None else {}
        ),
        "memory_feature_importance_rank": (
            {f: int(res.importance_B.rank(ascending=False)[f])
             for f in MEM_FEATURES}
            if res.importance_B is not None else {}
        ),
        "records": [
            {k: v for k, v in r.items()
             if not k.startswith("train_")} for r in res.records
        ],
        "runtime_seconds": round(res.runtime_seconds, 1),
    }
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "lending_metrics.json").write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2)
    )
    _plot(res.records, args.out / "lending_auc_curves.png")

    print(f"episodes paired={len(auc_diffs)} runtime={res.runtime_seconds:.0f}s")
    print(f"AUC  A={metrics['auc']['arm_A_mean']:.4f} "
          f"B={metrics['auc']['arm_B_mean']:.4f} "
          f"diff={mean_d:+.4f} CI95=[{lo_d:+.4f},{hi_d:+.4f}]")
    print(f"KS   diff={mean_k:+.4f} CI95=[{lo_k:+.4f},{hi_k:+.4f}]")
    print(f"profit A={metrics['profit']['arm_A_total']:+,.0f} "
          f"B={metrics['profit']['arm_B_total']:+,.0f} "
          f"diff/ep={mean_p:+,.0f} CI95=[{lo_p:+,.0f},{hi_p:+,.0f}]")
    print(f"VERDICT: {'PASS' if passed else 'FAIL'} (artifacts in {args.out})")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
