"""P2 acceptance helpers: joint LoRA + memory-injection training, gate probe.

Eval-only code (the lora/llm/nightly packages stay untouched). What lives
here and why:

* StagedMemoryReader — the injection's query_proj is a FROZEN random
  projection (retrieval is non-differentiable by design, see llm/injection.py),
  so the model-side query cannot express "this case vs that case" yet. The
  experiment therefore stages per-sample hit lists built from the case TEXT
  key against the slot store (the same semantic retrieval the production
  SlotReader performs, minus the theta gate) and the hook serves them in
  batch order. Gate features Σw / max w then reflect true relatedness, which
  is what the P2 gate-α criterion measures. Learning the query path itself
  is the documented P3 upgrade.
* build_joint_optimizer — one AdamW over three parameter groups: LoRA
  matrices (from the peft wrapper), mem_out, gate. Base weights and the
  frozen query_proj are structurally excluded.
* length_grouped_batches — equal-length batching. transformers 5.x hands a
  4-D causal mask to decoder layers whenever the 2-D attention mask contains
  padding, which breaks the injection hook's token-count pooling; batches
  with uniform length and an all-ones mask take the sdpa fast path (mask
  kwarg None) and work. Overlong sequences in a batch are left-truncated to
  the batch width — encode_pair's own semantics: the supervised completion
  tail always survives.
* Probe/metrics utilities shared by the acceptance runner and the tests.
"""

from __future__ import annotations

import json
from collections import deque
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence

import numpy as np
import torch

from llm import MemoryHit, MemoryInjection
from lora.train import TARGET_MODULES

# Off-domain probe texts: topical Chinese sentences with no credit content,
# used to stage the "unrelated context" gate-probe arm.
OFF_DOMAIN_TEXTS: tuple[str, ...] = (
    "今天天气晴朗,适合出门散步锻炼身体。",
    "这道菜的烹饪方法是先焯水后红烧。",
    "他每天坚持阅读历史书籍和科幻小说。",
    "周末我们计划去郊外爬山和野餐。",
    "这部电影讲述了一个关于友情的故事。",
    "学习一门新的语言需要长期练习。",
    "春天的花园里开满了各种颜色的花。",
    "运动员们正在为下一场比赛做准备。",
)

# P2 acceptance thresholds (design doc §5).
GATE_RELATED_MIN = 0.7
GATE_UNRELATED_MAX = 0.2


@dataclass(frozen=True)
class P2Config:
    """CPU-sized knobs for the P2 acceptance run."""

    seed: int = 20260726
    model_id: str = "Qwen/Qwen3-0.6B"
    layer_idx: int = 2
    top_k: int = 8
    # Slot-library warmup (canonical mode, outcomes reflowed).
    warmup_episodes: int = 4
    warmup_per_episode: int = 60
    # Joint training.
    distill_max_pairs: int = 200
    max_len: int = 128
    batch_size: int = 2
    max_steps: int = 100
    lora_lr: float = 5e-4
    injection_lr: float = 1e-2
    # Fraction of case-pair examples staged with MISMATCHED (off-domain)
    # memory: the only way the gate gets an explicit "close for irrelevant
    # context" gradient — related-only training just closes it everywhere.
    mismatch_fraction: float = 0.25
    # Phase 1: train the injection path alone (LoRA lr held at 0). With the
    # LM channel frozen, memory is the ONLY way to fit the completion, so
    # mem_out must extract the outcome signal and the gate must open for
    # relevant memory — full joint training from step 0 lets LoRA memorize
    # the task and teaches the gate to close PROPORTIONALLY to memory
    # pressure (the P2 smoke run's inverted-alpha failure).
    freeze_lora_steps: int = 60
    lora_r: int = 16
    lora_alpha: int = 32
    lora_dropout: float = 0.05
    lora_target_modules: tuple[str, ...] = TARGET_MODULES
    # Gate probe.
    probe_samples: int = 24
    # Promotion gate replay set.
    replay_pairs: int = 40
    old_probe_pairs: int = 16


# ------------------------------------------------------------------- reader


class StagedMemoryReader:
    """MemoryReader serving pre-staged hit lists, one per batch sample.

    stage() queues one entry per sample of the upcoming forward pass
    (None = memory miss); read() pops them in the hook's per-sample order.
    An empty queue degrades to a miss, so forgetting to stage never leaks
    stale hits into a forward.
    """

    def __init__(self) -> None:
        self._queue: deque[list[MemoryHit] | None] = deque()

    def stage(self, hits_per_sample: Iterable[Sequence[MemoryHit] | None]) -> None:
        self._queue = deque(
            None if hits is None else list(hits) for hits in hits_per_sample
        )

    def read(self, query: np.ndarray, k: int) -> list[MemoryHit]:
        if not self._queue:
            return []
        hits = self._queue.popleft()
        return [] if hits is None else hits[:k]


def hits_for_key(store: Any, key_vec: np.ndarray, k: int) -> list[MemoryHit]:
    """Cosine retrieval WITHOUT the theta gate (probe/training staging).

    Weight = clamped cosine. The production read path would also multiply
    a_tmp**GAMMA and cut at THETA; the probe keeps the full ranking so the
    gate itself (not the retrieval gate) is what discriminates related from
    unrelated contexts.
    """
    hits: list[MemoryHit] = []
    for slot_id, a_sem in store.search(np.asarray(key_vec, dtype=np.float32), k):
        slot = store.get_slot(slot_id)
        if slot is None:
            continue
        hits.append(
            MemoryHit(
                value_vec=np.asarray(slot.value_vec, dtype=np.float32),
                weight=float(max(a_sem, 0.0)),
                key_vec=np.asarray(slot.key_vec, dtype=np.float32),
            )
        )
    return hits


def build_probe_sets(
    embedder: Any,
    store: Any,
    case_texts: Sequence[str],
    k: int,
    off_domain_texts: Sequence[str] = OFF_DOMAIN_TEXTS,
) -> tuple[list[list[MemoryHit] | None], list[list[MemoryHit] | None]]:
    """Related vs unrelated probe arms (P2 acceptance criterion 1).

    related:   each case's own text key -> semantically matching experience.
    unrelated: off-domain text keys -> top-k hits that are only spuriously
               similar (low cosine), i.e. a case paired with irrelevant
               experience. Empty stores degrade either arm to misses.
    """
    related: list[list[MemoryHit] | None] = []
    for text in case_texts:
        key = embedder.embed_keys([text])[0]
        hits = hits_for_key(store, key, k)
        related.append(hits or None)
    unrelated: list[list[MemoryHit] | None] = []
    for i in range(len(case_texts)):
        key = embedder.embed_keys([off_domain_texts[i % len(off_domain_texts)]])[0]
        hits = hits_for_key(store, key, k)
        unrelated.append(hits or None)
    return related, unrelated


# -------------------------------------------------------------- joint optimizer


def build_joint_optimizer(
    peft_model: torch.nn.Module,
    injection: MemoryInjection,
    lora_lr: float,
    injection_lr: float,
) -> torch.optim.AdamW:
    """One AdamW, three groups: LoRA matrices + mem_out + gate.

    peft freezes every base weight (requires_grad=False), so group 0 is the
    LoRA surface by construction; the frozen query_proj is excluded
    explicitly. The base never sees an optimizer — snapshot_base_params /
    assert_base_unchanged (lora.train) guard it at runtime.
    """
    lora_params = [p for p in peft_model.parameters() if p.requires_grad]
    mem_params = list(injection.mem_out.parameters())
    gate_params = list(injection.gate.parameters())
    if not lora_params:
        raise ValueError("no trainable LoRA parameters found on the peft model")
    return torch.optim.AdamW(
        [
            {"params": lora_params, "lr": lora_lr},
            {"params": mem_params, "lr": injection_lr},
            {"params": gate_params, "lr": injection_lr},
        ]
    )


# ------------------------------------------------------------------ batching


def left_truncate(example: dict[str, list[int]], width: int) -> dict[str, list[int]]:
    """Keep the tail: the supervised completion survives (encode_pair semantics)."""
    return {
        "input_ids": example["input_ids"][-width:],
        "labels": example["labels"][-width:],
    }


def length_grouped_batches(
    examples: Sequence[Any],
    batch_size: int,
    rng: np.random.Generator,
) -> list[list[Any]]:
    """Sort by token length, chunk into batches, shuffle chunk order.

    Items are dicts with input_ids/labels (+ any payload). Chunks are
    returned unshuffled internally so staged hits stay aligned; chunk ORDER
    is shuffled for SGD noise.
    """
    ordered = sorted(examples, key=lambda e: len(e["input_ids"]))
    chunks = [
        ordered[i : i + batch_size] for i in range(0, len(ordered), batch_size)
    ]
    perm = rng.permutation(len(chunks))
    return [chunks[i] for i in perm]


def collate_uniform(chunk: Sequence[dict[str, list[int]]]) -> dict[str, torch.Tensor]:
    """Stack a chunk after left-truncating every item to the chunk width.

    All-ones attention mask (uniform length, no padding) — required for the
    injection hook under transformers 5.x (see module docstring).
    """
    width = min(len(e["input_ids"]) for e in chunk)
    ids = torch.tensor(
        [left_truncate(e, width)["input_ids"] for e in chunk], dtype=torch.long
    )
    labels = torch.tensor(
        [left_truncate(e, width)["labels"] for e in chunk], dtype=torch.long
    )
    return {
        "input_ids": ids,
        "labels": labels,
        "attention_mask": torch.ones_like(ids),
    }


# ------------------------------------------------------------------ gate probe


@torch.no_grad()
def run_gate_probe(
    model: torch.nn.Module,
    injection: MemoryInjection,
    reader: StagedMemoryReader,
    encoded_prompts: Sequence[dict[str, list[int]]],
    staged: Sequence[Sequence[MemoryHit] | None],
    batch_size: int,
) -> tuple[list[float], list[int]]:
    """Forward the probe batches and collect per-sample gate α / hit counts."""
    model.eval()
    gates: list[float] = []
    hits: list[int] = []
    for i in range(0, len(encoded_prompts), batch_size):
        chunk = encoded_prompts[i : i + batch_size]
        batch = collate_uniform(chunk)
        reader.stage(staged[i : i + batch_size])
        model(input_ids=batch["input_ids"], attention_mask=batch["attention_mask"])
        gates.extend(injection.last_gates)
        hits.extend(injection.last_hits)
    return gates, hits


def summarize_gates(values: Sequence[float]) -> dict[str, float | int]:
    arr = np.asarray(list(values), dtype=np.float64)
    if arr.size == 0:
        return {"n": 0, "mean": None, "p10": None, "p50": None, "p90": None}
    return {
        "n": int(arr.size),
        "mean": float(arr.mean()),
        "p10": float(np.percentile(arr, 10)),
        "p50": float(np.percentile(arr, 50)),
        "p90": float(np.percentile(arr, 90)),
    }


def plot_gate_hist(
    related: Sequence[float],
    unrelated: Sequence[float],
    path: Path | str,
    pre_related: Sequence[float] = (),
    pre_unrelated: Sequence[float] = (),
) -> None:
    """Two-arm gate-α histogram (+ optional pre-training reference lines)."""
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    fig, ax = plt.subplots(figsize=(9, 5))
    bins = np.linspace(0.0, 1.0, 21)
    ax.hist(list(related), bins=bins, alpha=0.6, color="C0",
            label=f"related (n={len(related)}, mean={np.mean(related):.3f})")
    ax.hist(list(unrelated), bins=bins, alpha=0.6, color="C1",
            label=f"unrelated (n={len(unrelated)}, mean={np.mean(unrelated):.3f})")
    ax.axvline(GATE_RELATED_MIN, color="C0", ls="--", lw=1,
               label=f"target related > {GATE_RELATED_MIN}")
    ax.axvline(GATE_UNRELATED_MAX, color="C1", ls="--", lw=1,
               label=f"target unrelated < {GATE_UNRELATED_MAX}")
    if len(pre_related):
        ax.axvline(float(np.mean(pre_related)), color="C0", ls=":", lw=1,
                   label=f"pre-train related mean {np.mean(pre_related):.3f}")
    if len(pre_unrelated):
        ax.axvline(float(np.mean(pre_unrelated)), color="C1", ls=":", lw=1,
                   label=f"pre-train unrelated mean {np.mean(pre_unrelated):.3f}")
    ax.set_xlabel("gate α (sigmoid output)")
    ax.set_ylabel("count")
    ax.set_title("P2 gate α distribution: related vs unrelated context")
    ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig(path, dpi=120)
    plt.close(fig)


# -------------------------------------------------------------------- metrics


def gate_verdict(related_mean: float | None, unrelated_mean: float | None) -> bool:
    """P2 criterion 1: related context > 0.7 AND unrelated < 0.2."""
    if related_mean is None or unrelated_mean is None:
        return False
    return related_mean > GATE_RELATED_MIN and unrelated_mean < GATE_UNRELATED_MAX


def build_p2_metrics(
    config: P2Config,
    timing: dict[str, Any],
    training: dict[str, Any],
    gate: dict[str, Any],
    promotion: dict[str, Any],
) -> dict[str, Any]:
    """Assemble the p2_metrics.json payload (schema pinned by tests)."""
    gate_pass = gate_verdict(
        gate["post"]["related"]["mean"], gate["post"]["unrelated"]["mean"]
    )
    promo_pass = bool(promotion["promoted"])
    return {
        "experiment": "P2 acceptance: local LLM + welded Engram + LoRA channel",
        "config": asdict(config),
        "timing": timing,
        "training": training,
        "gate": {**gate, "thresholds": {
            "related_min": GATE_RELATED_MIN, "unrelated_max": GATE_UNRELATED_MAX
        }},
        "promotion": promotion,
        "verdicts": {
            "gate_alpha": "PASS" if gate_pass else "FAIL",
            "lora_promotion": "PASS" if promo_pass else "FAIL",
            "overall": "PASS" if (gate_pass and promo_pass) else "FAIL",
        },
    }


def write_metrics(metrics: dict[str, Any], path: Path | str) -> None:
    Path(path).write_text(json.dumps(metrics, ensure_ascii=False, indent=2))
