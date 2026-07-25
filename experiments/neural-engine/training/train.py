"""Phase 1 offline training pipeline (ARCHITECTURE D6).

Load credit-g -> preprocess -> stratified 60/20/20 split -> build memory
table from the train split only -> freeze memory -> train backbone + read
gates (BCE, Adam, early stop on valid AUC) -> evaluate (AUC / KS / Brier,
isotonic-calibrated Brier, memory hit/miss rates) -> save artifacts ->
print human-readable MemoryTrace decision reasons (D7).

Run from experiments/neural-engine:  uv run python -m training.train
"""

from __future__ import annotations

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

from memory.hasher import HashHead, MultiHeadHasher
from memory.table import MemoryTable
from model.net import FeatureSpec, MemoryTrace, NeuralCreditNet

SEED = 42
ARTIFACT_DIR = Path(__file__).resolve().parent.parent / "artifacts"
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# K=4 heads (D1): single-feature heads + second-order crosses.
HEADS = [
    HashHead("checking", ("checking_status",), bits=10),
    HashHead("history", ("credit_history",), bits=10),
    HashHead("loan", ("credit_amount", "duration"), bits=12),
    HashHead("profile", ("personal_status", "purpose"), bits=12),
]
PROTO_DIM = 32

NUMERIC_FEATURES = [
    "duration", "credit_amount", "installment_commitment",
    "residence_since", "age", "existing_credits", "num_dependents",
]


# ---------------------------------------------------------------------- data

def load_credit_data() -> tuple[pd.DataFrame, np.ndarray, str]:
    """German Credit (bad=1 positive). Falls back to data_id=31, then to
    synthetic data (the fallback is reported in metrics.json)."""
    try:
        ds = __import__("sklearn.datasets", fromlist=["fetch_openml"]).fetch_openml(
            "credit-g", version=1, as_frame=True, parser="auto"
        )
        source = "openml:credit-g(v1)"
    except Exception as e1:  # noqa: BLE001 - network/parser failures both fall through
        try:
            ds = __import__("sklearn.datasets", fromlist=["fetch_openml"]).fetch_openml(
                data_id=31, as_frame=True, parser="auto"
            )
            source = "openml:data_id=31"
        except Exception as e2:  # noqa: BLE001
            print(f"openml failed ({e1!r}; {e2!r}); using synthetic fallback")
            from sklearn.datasets import make_classification

            x, y = make_classification(
                n_samples=1000, n_features=20, n_informative=8,
                weights=[0.7, 0.3], random_state=SEED,
            )
            df = pd.DataFrame(x, columns=[f"f{i}" for i in range(20)])
            return df, y.astype(int), "synthetic:make_classification"
    df = ds.frame.copy()
    y = (df.pop("class").astype(str) == "bad").astype(int).to_numpy()
    return df, y, source


class Preprocessor:
    """Ordinal-encode categoricals (0 = unseen), standardize numerics."""

    def __init__(self, num_cols: list[str]) -> None:
        self.num_cols = num_cols
        self.cat_cols: list[str] = []
        self.vocab: dict[str, dict[str, int]] = {}
        self.scaler = StandardScaler()

    def fit(self, df: pd.DataFrame) -> "Preprocessor":
        self.cat_cols = [c for c in df.columns if c not in self.num_cols]
        for c in self.cat_cols:
            cats = sorted(df[c].astype(str).unique())
            self.vocab[c] = {v: i + 1 for i, v in enumerate(cats)}
        self.scaler.fit(df[self.num_cols].to_numpy(dtype=float))
        return self

    def transform(self, df: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        cat = np.zeros((len(df), len(self.cat_cols)), dtype=np.int64)
        for j, c in enumerate(self.cat_cols):
            m = self.vocab[c]
            cat[:, j] = [m.get(str(v), 0) for v in df[c]]
        num = self.scaler.transform(df[self.num_cols].to_numpy(dtype=float))
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


def train_model(net, data, epochs=200, patience=15, lr=1e-3, batch=64):
    cat_tr, num_tr, sid_tr, y_tr = batch_tensors(*data["train"])
    cat_va, num_va, sid_va, y_va = batch_tensors(*data["valid"])
    opt = torch.optim.Adam(net.parameters(), lr=lr)
    n_bad, n_good = int(y_tr.sum().item()), int((y_tr == 0).sum().item())
    loss_fn = nn.BCEWithLogitsLoss(
        pos_weight=torch.tensor(n_good / max(n_bad, 1), device=DEVICE)
    )
    best_auc, best_state, wait = -1.0, None, 0
    for epoch in range(epochs):
        net.train()
        perm = torch.randperm(len(y_tr), device=DEVICE)
        for i in range(0, len(y_tr), batch):
            idx = perm[i : i + batch]
            logit, _ = net(cat_tr[idx], num_tr[idx], sid_tr[idx])
            loss = loss_fn(logit, y_tr[idx])
            opt.zero_grad()
            loss.backward()
            opt.step()
        net.eval()
        with torch.no_grad():
            logit_va, _ = net(cat_va, num_va, sid_va)
            auc = roc_auc_score(y_va.cpu(), torch.sigmoid(logit_va).cpu())
        if auc > best_auc:
            best_auc, best_state, wait = auc, net.state_dict().copy(), 0
        else:
            wait += 1
            if wait >= patience:
                break
    net.load_state_dict(best_state)  # type: ignore[arg-type]
    return best_auc, epoch + 1


# ------------------------------------------------------------------ metrics

def ks_statistic(scores: np.ndarray, y: np.ndarray) -> float:
    order = np.argsort(scores)
    y_sorted = y[order]
    cum_bad = np.cumsum(y_sorted) / max(y_sorted.sum(), 1)
    cum_good = np.cumsum(1 - y_sorted) / max((1 - y_sorted).sum(), 1)
    return float(np.max(np.abs(cum_bad - cum_good)))


def format_trace(
    trace: MemoryTrace, i: int, table: MemoryTable, prob_cal: float
) -> str:
    """D7 decision-reason template, one sample."""
    lines = [
        f"sample #{i}: P(bad) raw={trace.prob[i]:.3f} calibrated={prob_cal:.3f} "
        f"memory_miss={bool(trace.memory_miss[i])}"
    ]
    for k, head in enumerate(table.head_names):
        sid = int(trace.slot_ids[i, k])
        gate = float(trace.gates[i, k])
        if not bool(trace.hits[i, k]):
            lines.append(f"  [{head}] slot {sid}: MISS (gate {gate:.2f})")
            continue
        slot = table.get_slot(k, sid)
        desc = slot.pattern_desc if slot else "?"
        lines.append(
            f"  [{head}] slot {sid}: pattern『{desc}』"
            f"(n={int(trace.ns[i, k])}, bad_rate={slot.bad_rate:.2f}, "
            f"confidence={float(trace.confidences[i, k]):.2f}), gate={gate:.2f}"
        )
    return "\n".join(lines)


# ---------------------------------------------------------------------- main

def main() -> None:
    torch.manual_seed(SEED)
    np.random.seed(SEED)
    t0 = time.time()

    df, y, source = load_credit_data()
    print(f"data: {source}, n={len(df)}, bad_rate={y.mean():.3f}, device={DEVICE}")

    idx = np.arange(len(df))
    idx_tr, idx_tmp = train_test_split(
        idx, test_size=0.4, stratify=y, random_state=SEED
    )
    idx_va, idx_te = train_test_split(
        idx_tmp, test_size=0.5, stratify=y[idx_tmp], random_state=SEED
    )
    splits = {"train": idx_tr, "valid": idx_va, "test": idx_te}

    num_cols = [c for c in NUMERIC_FEATURES if c in df.columns]
    prep = Preprocessor(num_cols).fit(df.iloc[idx_tr])
    hasher = MultiHeadHasher(HEADS).fit(df.iloc[idx_tr], num_cols)
    spec = prep.spec()

    encoded, addressed = {}, {}
    for name, ids in splits.items():
        cat, num = prep.transform(df.iloc[ids])
        sids, patterns = hasher.address_batch(df.iloc[ids])
        encoded[name] = (cat, num)
        addressed[name] = (sids, patterns)

    # Build memory from the train split only, then freeze (D6).
    emb_tr = proto_embeddings(encoded["train"][0], spec, encoded["train"][1])
    table = MemoryTable([h.name for h in HEADS], [h.num_slots for h in HEADS], PROTO_DIM)
    table.build(addressed["train"][0], emb_tr, y[idx_tr], addressed["train"][1])
    print(f"memory built: {table.occupancy()}")

    net = NeuralCreditNet(spec, table).to(DEVICE)
    data = {
        name: (*encoded[name], addressed[name][0], y[ids])
        for name, ids in splits.items()
    }
    best_va_auc, n_epochs = train_model(net, data)
    print(f"trained {n_epochs} epochs, best valid AUC={best_va_auc:.4f}")

    # Evaluate raw + isotonic-calibrated (calibrator fit on valid only, D5).
    net.eval()
    with torch.no_grad():
        logit_va, _ = net(*batch_tensors(*data["valid"])[:3])
        logit_te, trace_te = net(*batch_tensors(*data["test"])[:3])
    p_va = torch.sigmoid(logit_va).cpu().numpy()
    p_te = torch.sigmoid(logit_te).cpu().numpy()
    y_va, y_te = y[idx_va], y[idx_te]
    iso = IsotonicRegression(out_of_bounds="clip").fit(p_va, y_va)
    p_te_cal = iso.predict(p_te)

    hits_np = trace_te.hits.numpy()
    metrics = {
        "data_source": source,
        "n_train": len(idx_tr), "n_valid": len(idx_va), "n_test": len(idx_te),
        "valid_auc": round(best_va_auc, 4),
        "test_auc": round(float(roc_auc_score(y_te, p_te)), 4),
        "test_auc_calibrated": round(float(roc_auc_score(y_te, p_te_cal)), 4),
        "test_ks": round(ks_statistic(p_te, y_te), 4),
        "test_brier_raw": round(float(brier_score_loss(y_te, p_te)), 4),
        "test_brier_calibrated": round(float(brier_score_loss(y_te, p_te_cal)), 4),
        "memory_hit_rate": round(float(hits_np.any(axis=1).mean()), 4),
        "memory_miss_rate": round(float(trace_te.memory_miss.numpy().mean()), 4),
        "head_hit_rates": {
            h: round(float(hits_np[:, k].mean()), 4)
            for k, h in enumerate(table.head_names)
        },
        "memory_occupancy": table.occupancy(),
        "epochs": n_epochs,
        "device": str(DEVICE),
        "elapsed_sec": round(time.time() - t0, 1),
    }

    ARTIFACT_DIR.mkdir(exist_ok=True)
    torch.save(
        {"state_dict": net.state_dict(), "spec": spec.__dict__,
         "heads": [h.__dict__ for h in HEADS], "proto_dim": PROTO_DIM},
        ARTIFACT_DIR / "model.pt",
    )
    table.save(ARTIFACT_DIR / "memory_table.pkl")
    (ARTIFACT_DIR / "metrics.json").write_text(json.dumps(metrics, indent=2))
    print(json.dumps(metrics, indent=2))

    print("\n--- MemoryTrace samples (D7) ---")
    for i in (0, 1, 2):
        print(format_trace(trace_te, i, table, float(p_te_cal[i])))


if __name__ == "__main__":
    main()
