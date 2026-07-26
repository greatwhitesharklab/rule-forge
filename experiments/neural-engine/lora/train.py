"""Nightly LoRA candidate training (design §1.4 peft spec, §4 step ④).

Hand-rolled causal-LM loop (no HF Trainer — the nightly job needs explicit
control): prompt tokens are masked to -100 so only the completion is learned.
Only LoRA parameters carry gradients; the base weights are hash/norm
snapshotted before training and asserted bitwise-unchanged afterwards — a
runtime guard, not just a test, because a base drift would silently corrupt
every other channel sharing the model.

Artifacts land in ``<output_root>/YYYYMMDD[-seq]/`` (peft save_pretrained +
meta.json), one directory per generation, so versions are natural and the
promotion gate can address any generation by path.
"""

from __future__ import annotations

import hashlib
import json
import math
import subprocess
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence

import torch

from .distill import DistillPair

TARGET_MODULES = ("q_proj", "k_proj", "v_proj", "o_proj",
                  "gate_proj", "up_proj", "down_proj")


@dataclass(frozen=True)
class LoraTrainConfig:
    """peft spec from design §1.4 + loop knobs (all CPU-sized)."""

    r: int = 16
    lora_alpha: int = 32
    lora_dropout: float = 0.05
    target_modules: tuple[str, ...] = TARGET_MODULES
    lr: float = 1e-4
    max_steps: int = 200
    batch_size: int = 4
    max_len: int = 512
    patience: int = 20  # early stop: optimizer steps without loss improvement
    seed: int = 20260726


@dataclass
class AdapterArtifact:
    adapter_dir: Path
    meta: dict[str, Any]
    steps: int


# ----------------------------------------------------------- base freeze guard

class BaseSnapshot:
    """Digest of every base parameter: name -> (sha1 of raw bytes, L2 norm).

    Keeps references to the Parameter objects themselves: peft injection moves
    the same tensors into lora wrapper modules, so re-hashing the referenced
    objects after training still reads the live base weights.
    """

    def __init__(self, model: torch.nn.Module):
        self.digests: dict[str, tuple[str, float]] = {}
        self._refs: dict[str, torch.nn.Parameter] = {}
        for name, p in model.named_parameters():
            raw = p.detach().cpu().numpy().tobytes()
            self.digests[name] = (hashlib.sha1(raw).hexdigest(), float(p.detach().norm()))
            self._refs[name] = p


def snapshot_base_params(model: torch.nn.Module) -> BaseSnapshot:
    return BaseSnapshot(model)


def assert_base_unchanged(snap: BaseSnapshot, model: torch.nn.Module) -> None:
    """Raise AssertionError if any snapshotted base parameter moved."""
    for name, expected in snap.digests.items():
        p = snap._refs[name]
        raw = p.detach().cpu().numpy().tobytes()
        now = (hashlib.sha1(raw).hexdigest(), float(p.detach().norm()))
        assert now == expected, f"base parameter drifted during LoRA training: {name}"


# ------------------------------------------------------------------ tokenizing

def encode_pair(
    tokenizer: Any, prompt: str, completion: str, max_len: int
) -> dict[str, list[int]]:
    """input_ids = prompt+completion; labels mask the prompt to -100.

    Overlong pairs keep the tail: the completion (the supervised signal)
    survives truncation, the prompt is cut from the left.
    """
    p_ids = tokenizer(prompt, return_tensors="pt")["input_ids"][0].tolist()
    c_ids = tokenizer(completion, return_tensors="pt")["input_ids"][0].tolist()
    ids = (p_ids + c_ids)[-max_len:]
    labels = ([-100] * len(p_ids) + c_ids)[-max_len:]
    return {"input_ids": ids, "labels": labels}


def _collate(
    examples: list[dict[str, list[int]]], pad_id: int
) -> dict[str, torch.Tensor]:
    width = max(len(e["input_ids"]) for e in examples)
    input_ids, labels, attn = [], [], []
    for e in examples:
        pad = width - len(e["input_ids"])
        input_ids.append(e["input_ids"] + [pad_id] * pad)
        labels.append(e["labels"] + [-100] * pad)
        attn.append([1] * len(e["input_ids"]) + [0] * pad)
    return {
        "input_ids": torch.tensor(input_ids, dtype=torch.long),
        "labels": torch.tensor(labels, dtype=torch.long),
        "attention_mask": torch.tensor(attn, dtype=torch.long),
    }


# ------------------------------------------------------------------- artifacts

def _git_commit() -> str | None:
    try:
        out = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=Path(__file__).resolve().parent,
            capture_output=True, text=True, timeout=5,
        )
        return out.stdout.strip() or None
    except Exception:
        return None


def _next_adapter_dir(output_root: Path, today: str) -> Path:
    """adapters/YYYYMMDD, or -2/-3/... when that date already has generations."""
    base = output_root / today
    if not base.exists():
        return base
    seq = 2
    while (output_root / f"{today}-{seq}").exists():
        seq += 1
    return output_root / f"{today}-{seq}"


# --------------------------------------------------------------------- training

def train_lora(
    base_model: torch.nn.Module,
    tokenizer: Any,
    dataset: Sequence[DistillPair],
    config: LoraTrainConfig = LoraTrainConfig(),
    output_root: Path | str | None = None,
    today: str | None = None,
) -> AdapterArtifact:
    """Train a LoRA candidate on the distill set and save a versioned adapter.

    `today` pins the version directory date (tests / replay); defaults to the
    current UTC date. max_steps=0 is legal and saves an untrained identity
    adapter (LoRA B is zero-init), e.g. a no-op challenger.
    """
    from peft import LoraConfig, get_peft_model

    if output_root is None:
        output_root = Path(__file__).resolve().parent / "adapters"
    output_root = Path(output_root)
    today = today or datetime.now(timezone.utc).strftime("%Y%m%d")

    torch.manual_seed(config.seed)
    snap = snapshot_base_params(base_model)

    peft_cfg = LoraConfig(
        r=config.r,
        lora_alpha=config.lora_alpha,
        lora_dropout=config.lora_dropout,
        target_modules=list(config.target_modules),
        task_type="CAUSAL_LM",
    )
    model = get_peft_model(base_model, peft_cfg)
    model.train()

    examples = [encode_pair(tokenizer, p.prompt, p.completion, config.max_len)
                for p in dataset]
    pad_id = int(getattr(tokenizer, "pad_token_id", 0) or 0)
    trainable = [p for p in model.parameters() if p.requires_grad]
    opt = torch.optim.AdamW(trainable, lr=config.lr)

    t0 = time.time()
    steps = 0
    first_loss: float | None = None
    best_loss = math.inf
    since_improve = 0
    rng = torch.Generator().manual_seed(config.seed)
    while steps < config.max_steps and examples:
        order = torch.randperm(len(examples), generator=rng).tolist()
        epoch_stalled = True
        for i in range(0, len(order), config.batch_size):
            batch = _collate([examples[j] for j in order[i:i + config.batch_size]], pad_id)
            loss = model(**batch).loss
            loss.backward()
            opt.step()
            opt.zero_grad()
            steps += 1
            value = float(loss.item())
            if first_loss is None:
                first_loss = value
            if value < best_loss - 1e-6:
                best_loss, since_improve, epoch_stalled = value, 0, False
            else:
                since_improve += 1
            if steps >= config.max_steps or since_improve >= config.patience:
                break
        if since_improve >= config.patience or epoch_stalled:
            break

    assert_base_unchanged(snap, base_model)  # runtime guard: only LoRA moved
    model.eval()

    adapter_dir = _next_adapter_dir(output_root, today)
    adapter_dir.mkdir(parents=True, exist_ok=False)
    model.save_pretrained(str(adapter_dir))

    meta: dict[str, Any] = {
        "created_at": datetime.now(timezone.utc).isoformat(),
        "n_examples": len(dataset),
        "steps": steps,
        "first_loss": first_loss,
        "final_loss": best_loss if steps else None,
        "duration_sec": round(time.time() - t0, 3),
        "lr": config.lr,
        "peft": {
            "r": config.r,
            "lora_alpha": config.lora_alpha,
            "lora_dropout": config.lora_dropout,
            "target_modules": list(config.target_modules),
        },
        "git_commit": _git_commit(),
    }
    (adapter_dir / "meta.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False))

    # Restore the caller's model to the pristine base: the candidate lives on
    # disk now; the promotion gate (not the trainer) decides what gets mounted.
    restored = model.unload()
    for p in restored.parameters():
        p.requires_grad_(True)
    return AdapterArtifact(adapter_dir=adapter_dir, meta=meta, steps=steps)
