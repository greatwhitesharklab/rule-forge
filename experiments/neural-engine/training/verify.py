"""Engram-style verification suite V1-V4 (all on the trained artifacts).

V1  zero-init: with W_out zero-initialized, a freshly built with-memory net
    must produce logits identical (< 1e-6) to the ablated net at the same
    seed — the memory branch starts as an exact no-op.
V2  gate context probe: gate alpha distribution on real test rows vs
    context-scrambled rows (each head's pattern re-addressed from a donor
    row, backbone context untouched). Expect real > scrambled.
V3  hash collision / Zipf: per-head slot hit-count concentration
    (top-1 / top-10 / top-10% shares) + rank-frequency log-log plot.
V4  ablation loss curves: train/valid loss per epoch for both arms.

Plots are written to artifacts/ (matplotlib, Agg backend); the textual
summary is returned as a dict for verification.json.
"""

from __future__ import annotations

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import torch

from memory.hasher import MultiHeadHasher
from memory.table import MemoryTable
from model.net import MemoryTrace, NeuralCreditNet


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


# ----------------------------------------------------------------------- V1

def zero_init_max_diff(
    net_mem: NeuralCreditNet,
    net_abl: NeuralCreditNet,
    cat: torch.Tensor,
    num: torch.Tensor,
    sids: torch.Tensor,
) -> float:
    """Max |logit_mem - logit_abl| for freshly initialized nets (eval mode
    to neutralize dropout). Must be ~0 if W_out zero-init is correct."""
    net_mem.eval()
    net_abl.eval()
    with torch.no_grad():
        logit_mem, _ = net_mem(cat, num, sids)
        logit_abl, _ = net_abl(cat, num, sids)
    return float((logit_mem - logit_abl).abs().max().item())


# ----------------------------------------------------------------------- V2

def scrambled_slot_ids(
    hasher: MultiHeadHasher, df: pd.DataFrame, seed: int
) -> np.ndarray:
    """V2 probe: re-address each head from a donor row (cyclic shift by a
    random offset in [1, n), so no row is its own donor). Each column keeps
    its exact marginal distribution (it is a permutation), but the pattern
    no longer matches the sample's backbone context."""
    rng = np.random.default_rng(seed)
    n = len(df)
    ids = np.zeros((n, len(hasher.heads)), dtype=np.int64)
    for k, h in enumerate(hasher.heads):
        shift = int(rng.integers(1, n))
        donor = (np.arange(n) + shift) % n
        ids[:, k] = hasher.address_head(h, df[list(h.features)].iloc[donor])
    return ids


def gate_stats(gates: np.ndarray, head_names: list[str]) -> dict[str, dict]:
    """Per-head gate alpha distribution summary."""
    return {
        h: {
            "mean": round(float(gates[:, k].mean()), 4),
            "p10": round(float(np.quantile(gates[:, k], 0.1)), 4),
            "p50": round(float(np.quantile(gates[:, k], 0.5)), 4),
            "p90": round(float(np.quantile(gates[:, k], 0.9)), 4),
        }
        for k, h in enumerate(head_names)
    }


def plot_gate_probe(
    gates_real: np.ndarray,
    gates_scr: np.ndarray,
    head_names: list[str],
    path: str,
) -> None:
    k = len(head_names)
    fig, axes = plt.subplots(1, k, figsize=(4 * k, 3.2), sharey=True)
    for ki, ax in enumerate(np.atleast_1d(axes)):
        ax.hist(gates_real[:, ki], bins=40, alpha=0.6, density=True,
                label="real", color="tab:blue")
        ax.hist(gates_scr[:, ki], bins=40, alpha=0.6, density=True,
                label="scrambled", color="tab:orange")
        ax.set_title(head_names[ki])
        ax.set_xlabel("gate alpha")
    axes[0].set_ylabel("density")
    axes[-1].legend()
    fig.suptitle("V2 gate behavior: real vs context-scrambled")
    fig.tight_layout()
    fig.savefig(path, dpi=120)
    plt.close(fig)


# ----------------------------------------------------------------------- V3

def slot_concentration(
    sids: np.ndarray, table: MemoryTable
) -> dict[str, dict]:
    """Per-head slot hit-count concentration on the train split."""
    out: dict[str, dict] = {}
    for k, h in enumerate(table.head_names):
        counts = np.bincount(sids[:, k], minlength=table.num_slots[k])
        nz = np.sort(counts[counts > 0])[::-1]
        total = float(nz.sum())
        top10pct = max(1, int(np.ceil(len(nz) * 0.1)))
        out[h] = {
            "total_slots": table.num_slots[k],
            "occupied_slots": int(len(nz)),
            "top1_share": round(float(nz[0] / total), 4),
            "top10_share": round(float(nz[:10].sum() / total), 4),
            "top10pct_share": round(float(nz[:top10pct].sum() / total), 4),
            "max_count": int(nz[0]),
            "min_count": int(nz[-1]),
        }
    return out


def plot_rank_frequency(
    sids: np.ndarray, table: MemoryTable, path: str
) -> None:
    fig, ax = plt.subplots(figsize=(6, 4.5))
    for k, h in enumerate(table.head_names):
        counts = np.bincount(sids[:, k], minlength=table.num_slots[k])
        nz = np.sort(counts[counts > 0])[::-1]
        ax.loglog(np.arange(1, len(nz) + 1), nz, marker=".", ms=3, lw=1,
                  label=h)
    ax.set_xlabel("slot rank")
    ax.set_ylabel("hit count")
    ax.set_title("V3 slot rank-frequency (log-log)")
    ax.legend()
    fig.tight_layout()
    fig.savefig(path, dpi=120)
    plt.close(fig)


# ----------------------------------------------------------------------- V4

def plot_loss_curves(
    hist_mem: dict[str, list[float]],
    hist_abl: dict[str, list[float]],
    path: str,
) -> None:
    fig, ax = plt.subplots(figsize=(7, 4.5))
    for hist, name, color in (
        (hist_mem, "with_memory", "tab:blue"),
        (hist_abl, "without_memory", "tab:orange"),
    ):
        ep = np.arange(1, len(hist["train_loss"]) + 1)
        ax.plot(ep, hist["train_loss"], color=color, lw=1.2,
                label=f"{name} train")
        ax.plot(ep, hist["valid_loss"], color=color, lw=1.2, ls="--",
                label=f"{name} valid")
    ax.set_xlabel("epoch")
    ax.set_ylabel("BCE loss (pos_weight)")
    ax.set_title("V4 ablation loss curves")
    ax.legend()
    fig.tight_layout()
    fig.savefig(path, dpi=120)
    plt.close(fig)
