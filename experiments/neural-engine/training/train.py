"""Phase 1 offline training pipeline (ARCHITECTURE D6).

Load dataset (--dataset, see training/datasets.py) -> preprocess (train-set
median imputation + winsorized standardization) -> stratified 60/20/20 split
-> build memory table from the train split only -> freeze memory -> train
backbone + read gates (BCE with pos_weight, Adam, early stop on valid AUC)
-> evaluate (AUC / KS / Brier, isotonic-calibrated Brier, memory hit/miss
rates, gate distribution) -> repeat with the memory injection ablated (pure
MLP backbone, same split/seed) -> save artifacts -> print human-readable
MemoryTrace decision reasons (D7).

Run from experiments/neural-engine:
    uv run python -m training.train --dataset credit-g
    uv run python -m training.train --dataset give-me-some-credit
"""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from sklearn.isotonic import IsotonicRegression
from sklearn.metrics import brier_score_loss, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

from memory.hasher import MultiHeadHasher
from memory.table import MemoryTable
from model.net import FeatureSpec, MemoryTrace, NeuralCreditNet
from training import verify
from training.datasets import DATASET_NAMES, DatasetSpec, resolve
from training.verify import format_trace

SEED = 42
ARTIFACT_DIR = Path(__file__).resolve().parent.parent / "artifacts"
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
PROTO_DIM = 32


# ---------------------------------------------------------------------- data

class Preprocessor:
    """Median-impute + winsorize + standardize numerics; ordinal-encode
    categoricals (0 = unseen). All statistics are fit on the train split
    only, so valid/test cannot leak into the transforms."""

    def __init__(self, num_cols: list[str]) -> None:
        self.num_cols = num_cols
        self.cat_cols: list[str] = []
        self.vocab: dict[str, dict[str, int]] = {}
        self.medians: np.ndarray | None = None
        self.clip_lo: np.ndarray | None = None
        self.clip_hi: np.ndarray | None = None
        self.scaler = StandardScaler()

    def fillna(self, df: pd.DataFrame) -> pd.DataFrame:
        """Fill numeric NaNs with the train medians (identity before fit)."""
        if self.medians is None or not self.num_cols:
            return df
        fill = dict(zip(self.num_cols, self.medians.tolist()))
        return df.fillna(fill)

    def fit(self, df: pd.DataFrame) -> "Preprocessor":
        self.cat_cols = [c for c in df.columns if c not in self.num_cols]
        for c in self.cat_cols:
            cats = sorted(df[c].astype(str).unique())
            self.vocab[c] = {v: i + 1 for i, v in enumerate(cats)}
        num = df[self.num_cols].to_numpy(dtype=float)
        self.medians = np.nanmedian(num, axis=0)
        num = np.where(np.isnan(num), self.medians, num)
        # Winsorize at 0.5%/99.5%: GMSC has extreme outliers (utilization up
        # to 50708) that would otherwise crush the standardized features.
        self.clip_lo = np.quantile(num, 0.005, axis=0)
        self.clip_hi = np.quantile(num, 0.995, axis=0)
        self.scaler.fit(np.clip(num, self.clip_lo, self.clip_hi))
        return self

    def transform(self, df: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        cat = np.zeros((len(df), len(self.cat_cols)), dtype=np.int64)
        for j, c in enumerate(self.cat_cols):
            m = self.vocab[c]
            cat[:, j] = [m.get(str(v), 0) for v in df[c]]
        num = df[self.num_cols].to_numpy(dtype=float)
        num = np.where(np.isnan(num), self.medians, num)
        num = np.clip(num, self.clip_lo, self.clip_hi)
        num = self.scaler.transform(num)
        return cat, num.astype(np.float32)

    def spec(self, embed_dim: int = 8) -> FeatureSpec:
        return FeatureSpec(
            cat_features=self.cat_cols,
            cat_vocab_sizes=[len(self.vocab[c]) + 1 for c in self.cat_cols],
            num_features=self.num_cols,
            embed_dim=embed_dim,
        )


def proto_embeddings(
    cat: np.ndarray, spec: FeatureSpec, num: np.ndarray, seed: int = SEED
) -> np.ndarray:
    """Fixed seeded random projection of encoded features -> L2-normalized
    proto space. Phase-1 simplification of D6: protos are training-free and
    fully deterministic, avoiding the encoder/table chicken-and-egg; the
    read gate learns to map queries into this fixed space."""
    rng = np.random.default_rng(seed)
    onehot = np.zeros((len(cat), sum(spec.cat_vocab_sizes)), dtype=np.float32)
    offset = 0
    for j, v in enumerate(spec.cat_vocab_sizes):
        onehot[np.arange(len(cat)), offset + cat[:, j]] = 1.0
        offset += v
    x = np.concatenate([onehot, num], axis=1)
    proj = rng.standard_normal((x.shape[1], PROTO_DIM)).astype(np.float32)
    proj /= np.linalg.norm(proj, axis=0, keepdims=True)
    emb = x @ proj
    return emb / np.maximum(np.linalg.norm(emb, axis=1, keepdims=True), 1e-8)


# ------------------------------------------------------------------ training

def batch_tensors(cat, num, sids, y=None):
    t = [torch.as_tensor(cat), torch.as_tensor(num), torch.as_tensor(sids)]
    if y is not None:
        t.append(torch.as_tensor(y, dtype=torch.float32))
    return [x.to(DEVICE) for x in t]


def train_model(net, data, epochs=200, patience=15, lr=1e-3, batch=None):
    cat_tr, num_tr, sid_tr, y_tr = batch_tensors(*data["train"])
    cat_va, num_va, sid_va, y_va = batch_tensors(*data["valid"])
    if batch is None:
        batch = 64 if len(y_tr) <= 20_000 else 1024
    opt = torch.optim.Adam(net.parameters(), lr=lr)
    n_bad, n_good = int(y_tr.sum().item()), int((y_tr == 0).sum().item())
    loss_fn = nn.BCEWithLogitsLoss(
        pos_weight=torch.tensor(n_good / max(n_bad, 1), device=DEVICE)
    )
    best_auc, best_state, wait, epoch = -1.0, None, 0, -1
    history: dict[str, list[float]] = {"train_loss": [], "valid_loss": []}
    for epoch in range(epochs):
        net.train()
        perm = torch.randperm(len(y_tr), device=DEVICE)
        epoch_loss, seen = 0.0, 0
        for i in range(0, len(y_tr), batch):
            idx = perm[i : i + batch]
            logit, _ = net(cat_tr[idx], num_tr[idx], sid_tr[idx])
            loss = loss_fn(logit, y_tr[idx])
            opt.zero_grad()
            loss.backward()
            opt.step()
            epoch_loss += float(loss.item()) * len(idx)
            seen += len(idx)
        net.eval()
        with torch.no_grad():
            logit_va, _ = net(cat_va, num_va, sid_va)
            p_va = torch.sigmoid(logit_va)
            auc = roc_auc_score(y_va.cpu(), p_va.cpu())
            val_loss = float(loss_fn(logit_va, y_va).item())
        history["train_loss"].append(epoch_loss / max(seen, 1))
        history["valid_loss"].append(val_loss)
        if auc > best_auc:
            best_auc, wait = auc, 0
            # Clone tensors: state_dict() aliases live parameters.
            best_state = {k: v.clone() for k, v in net.state_dict().items()}
        else:
            wait += 1
            if wait >= patience:
                break
    net.load_state_dict(best_state)  # type: ignore[arg-type]
    return best_auc, epoch + 1, history


# ------------------------------------------------------------------ metrics

def ks_statistic(scores: np.ndarray, y: np.ndarray) -> float:
    order = np.argsort(scores)
    y_sorted = y[order]
    cum_bad = np.cumsum(y_sorted) / max(y_sorted.sum(), 1)
    cum_good = np.cumsum(1 - y_sorted) / max((1 - y_sorted).sum(), 1)
    return float(np.max(np.abs(cum_bad - cum_good)))


# ---------------------------------------------------------------------- main

def train_and_evaluate(
    net: NeuralCreditNet,
    data: dict[str, tuple],
    y: np.ndarray,
    splits: dict[str, np.ndarray],
) -> tuple[dict, dict[str, list[float]], MemoryTrace, np.ndarray]:
    """Train one arm and evaluate on valid/test. Returns (metrics, loss
    history, test trace, calibrated test probabilities)."""
    best_va_auc, n_epochs, history = train_model(net, data)

    net.eval()
    with torch.no_grad():
        logit_va, _ = net(*batch_tensors(*data["valid"])[:3])
        logit_te, trace_te = net(*batch_tensors(*data["test"])[:3])
    p_va = torch.sigmoid(logit_va).cpu().numpy()
    p_te = torch.sigmoid(logit_te).cpu().numpy()
    y_va, y_te = y[splits["valid"]], y[splits["test"]]
    # Isotonic calibrator fit on the valid split only (D5).
    iso = IsotonicRegression(out_of_bounds="clip").fit(p_va, y_va)
    p_te_cal = iso.predict(p_te)

    metrics = {
        "valid_auc": round(best_va_auc, 4),
        "test_auc": round(float(roc_auc_score(y_te, p_te)), 4),
        "test_auc_calibrated": round(float(roc_auc_score(y_te, p_te_cal)), 4),
        "test_ks": round(ks_statistic(p_te, y_te), 4),
        "test_brier_raw": round(float(brier_score_loss(y_te, p_te)), 4),
        "test_brier_calibrated": round(
            float(brier_score_loss(y_te, p_te_cal)), 4
        ),
        "epochs": n_epochs,
    }
    return metrics, history, trace_te, p_te_cal


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", choices=DATASET_NAMES, default="credit-g")
    args = parser.parse_args()

    torch.manual_seed(SEED)
    np.random.seed(SEED)
    t0 = time.time()

    dspec: DatasetSpec
    dspec, df, y, source = resolve(args.dataset, SEED)
    print(
        f"data: {source}, n={len(df)}, bad_rate={y.mean():.3f}, device={DEVICE}"
    )

    idx = np.arange(len(df))
    idx_tr, idx_tmp = train_test_split(
        idx, test_size=0.4, stratify=y, random_state=SEED
    )
    idx_va, idx_te = train_test_split(
        idx_tmp, test_size=0.5, stratify=y[idx_tmp], random_state=SEED
    )
    splits = {"train": idx_tr, "valid": idx_va, "test": idx_te}

    num_cols = [c for c in dspec.numeric_features if c in df.columns]
    prep = Preprocessor(num_cols).fit(df.iloc[idx_tr])
    df = prep.fillna(df)  # train medians; hasher must not see NaN
    hasher = MultiHeadHasher(list(dspec.heads), n_bins=dspec.n_bins).fit(
        df.iloc[idx_tr], num_cols
    )
    spec = prep.spec()

    encoded, addressed = {}, {}
    for name, ids in splits.items():
        cat, num = prep.transform(df.iloc[ids])
        sids, patterns = hasher.address_batch(df.iloc[ids])
        encoded[name] = (cat, num)
        addressed[name] = (sids, patterns)

    # Build memory from the train split only, then freeze (D6).
    emb_tr = proto_embeddings(encoded["train"][0], spec, encoded["train"][1])
    heads = list(dspec.heads)
    table = MemoryTable([h.name for h in heads], [h.num_slots for h in heads], PROTO_DIM)
    table.build(addressed["train"][0], emb_tr, y[idx_tr], addressed["train"][1])
    print(f"memory built: {table.occupancy()}")

    data = {
        name: (*encoded[name], addressed[name][0], y[ids])
        for name, ids in splits.items()
    }

    # Two arms, same data/split/seed: full model vs no-memory backbone.
    torch.manual_seed(SEED)
    net = NeuralCreditNet(spec, table).to(DEVICE)
    torch.manual_seed(SEED)
    net_abl = NeuralCreditNet(spec, table, ablate_memory=True).to(DEVICE)

    # V1: with W_out zero-initialized the memory branch is an exact no-op.
    cat_te, num_te, sid_te = batch_tensors(*data["test"])[:3]
    v1_diff = verify.zero_init_max_diff(net, net_abl, cat_te, num_te, sid_te)
    print(f"V1 zero-init max |dlogit| = {v1_diff:.2e}")

    m_mem, hist_mem, trace_te, p_te_cal = train_and_evaluate(
        net, data, y, splits
    )
    print(f"[with_memory] trained {m_mem['epochs']} epochs, "
          f"valid AUC={m_mem['valid_auc']:.4f}, test AUC={m_mem['test_auc']:.4f}")
    m_abl, hist_abl, _, _ = train_and_evaluate(net_abl, data, y, splits)
    print(f"[ablated]     trained {m_abl['epochs']} epochs, "
          f"valid AUC={m_abl['valid_auc']:.4f}, test AUC={m_abl['test_auc']:.4f}")

    hits_np = trace_te.hits.numpy()
    gates_np = trace_te.gates.numpy()
    metrics = {
        "dataset": dspec.name,
        "data_source": source,
        "loss": "BCEWithLogitsLoss(pos_weight=n_good/n_bad); AUC/KS are "
                "ranking metrics, Brier uses isotonic calibration on valid",
        "n_train": len(idx_tr), "n_valid": len(idx_va), "n_test": len(idx_te),
        **m_mem,
        "memory_hit_rate": round(float(hits_np.any(axis=1).mean()), 4),
        "memory_miss_rate": round(float(trace_te.memory_miss.numpy().mean()), 4),
        "head_hit_rates": {
            h: round(float(hits_np[:, k].mean()), 4)
            for k, h in enumerate(table.head_names)
        },
        "memory_occupancy": table.occupancy(),
        "gate_stats": {
            "mean": round(float(gates_np.mean()), 4),
            "p10": round(float(np.quantile(gates_np, 0.1)), 4),
            "p50": round(float(np.quantile(gates_np, 0.5)), 4),
            "p90": round(float(np.quantile(gates_np, 0.9)), 4),
        },
        "head_gate_means": {
            h: round(float(gates_np[:, k].mean()), 4)
            for k, h in enumerate(table.head_names)
        },
        "ablation": {
            "without_memory": m_abl,
            "memory_gain": {
                "test_auc": round(m_mem["test_auc"] - m_abl["test_auc"], 4),
                "test_ks": round(m_mem["test_ks"] - m_abl["test_ks"], 4),
                "test_brier_calibrated": round(
                    m_abl["test_brier_calibrated"]
                    - m_mem["test_brier_calibrated"], 4
                ),
            },
        },
        "device": str(DEVICE),
        "elapsed_sec": round(time.time() - t0, 1),
    }

    ARTIFACT_DIR.mkdir(exist_ok=True)
    torch.save(
        {"state_dict": net.state_dict(), "spec": spec.__dict__,
         "heads": [h.__dict__ for h in heads], "proto_dim": PROTO_DIM},
        ARTIFACT_DIR / "model.pt",
    )
    table.save(ARTIFACT_DIR / "memory_table.pkl")
    (ARTIFACT_DIR / "metrics.json").write_text(json.dumps(metrics, indent=2))
    print(json.dumps(metrics, indent=2))

    # ------------------------------------------------------ verifications
    # V2: gate context probe — same backbone context, scrambled addressing.
    sid_scr = verify.scrambled_slot_ids(hasher, df.iloc[idx_te], SEED)
    net.eval()
    with torch.no_grad():
        _, trace_scr = net(cat_te, num_te, torch.as_tensor(sid_scr).to(DEVICE))
    gates_scr = trace_scr.gates.numpy()
    verify.plot_gate_probe(
        gates_np, gates_scr, table.head_names,
        str(ARTIFACT_DIR / "v2_gate_context_probe.png"),
    )

    # V3: slot hit-count concentration (Zipf check) on the train split.
    zipf = verify.slot_concentration(addressed["train"][0], table)
    verify.plot_rank_frequency(
        addressed["train"][0], table,
        str(ARTIFACT_DIR / "v3_slot_rank_frequency.png"),
    )

    # V4: loss curves of both arms on one figure.
    verify.plot_loss_curves(
        hist_mem, hist_abl, str(ARTIFACT_DIR / "v4_ablation_loss_curves.png")
    )

    verification = {
        "v1_zero_init_max_abs_diff": v1_diff,
        "v2_gate_probe": {
            "real": verify.gate_stats(gates_np, table.head_names),
            "scrambled": verify.gate_stats(gates_scr, table.head_names),
        },
        "v3_slot_concentration": zipf,
        "v4_final_loss": {
            "with_memory": {k: round(v[-1], 5) for k, v in hist_mem.items()},
            "without_memory": {k: round(v[-1], 5) for k, v in hist_abl.items()},
        },
        "figures": [
            "v2_gate_context_probe.png",
            "v3_slot_rank_frequency.png",
            "v4_ablation_loss_curves.png",
        ],
    }
    (ARTIFACT_DIR / "verification.json").write_text(
        json.dumps(verification, indent=2)
    )
    print(json.dumps(verification, indent=2))

    print("\n--- MemoryTrace samples (D7) ---")
    for i in (0, 1, 2):
        print(format_trace(trace_te, i, table, float(p_te_cal[i])))


if __name__ == "__main__":
    main()
