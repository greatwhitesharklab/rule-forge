"""P2 joint-training tests (tiny random Qwen3, no network).

Pins the eval-side training machinery:
  * build_joint_optimizer param groups = LoRA + mem_out + gate, with base
    weights and the frozen query_proj structurally excluded;
  * a few joint steps move mem_out AND gate (zero-init dynamics: mem_out
    leaves zero first, then the gate gets a gradient) while the base stays
    bitwise frozen (lora.train runtime guard);
  * length-grouped uniform collation keeps the supervised completion tail.
"""

from __future__ import annotations

import numpy as np
import torch

from _tiny import FakeTokenizer, make_tiny_qwen3
from eval.p2_common import (
    StagedMemoryReader,
    build_joint_optimizer,
    collate_uniform,
    length_grouped_batches,
)
from llm import MemoryHit, MemoryInjection
from lora.train import assert_base_unchanged, encode_pair, snapshot_base_params

HIDDEN = 64
QUERY_DIM = 16


def _staged_hits(n: int, seed: int = 0) -> list[MemoryHit]:
    rng = np.random.default_rng(seed)
    return [
        MemoryHit(
            value_vec=rng.standard_normal(HIDDEN).astype(np.float32),
            weight=0.9 - 0.2 * i,
            key_vec=rng.standard_normal(QUERY_DIM).astype(np.float32),
        )
        for i in range(n)
    ]


def _joint_setup():
    base = make_tiny_qwen3(seed=0, layers=3)
    snap = snapshot_base_params(base)  # before peft wraps: tracks base only
    reader = StagedMemoryReader()
    inj = MemoryInjection(base, reader, layer_idx=2, query_dim=QUERY_DIM)
    from peft import LoraConfig, get_peft_model

    model = get_peft_model(
        base,
        LoraConfig(r=4, lora_alpha=8, lora_dropout=0.0,
                   target_modules=["q_proj", "v_proj"], task_type="CAUSAL_LM"),
    )
    return model, inj, reader, snap


def test_optimizer_param_groups():
    model, inj, _reader, _snap = _joint_setup()
    opt = build_joint_optimizer(model, inj, lora_lr=1e-4, injection_lr=1e-3)

    assert len(opt.param_groups) == 3
    group_ids = [{id(p) for p in g["params"]} for g in opt.param_groups]
    lora_ids, mem_ids, gate_ids = group_ids

    # LoRA group: only peft trainables, and they are lora_* matrices.
    lora_named = {n: p for n, p in model.named_parameters() if p.requires_grad}
    assert lora_named, "peft wrapper exposes no trainable params"
    assert all("lora_" in n for n in lora_named)
    assert {id(p) for p in lora_named.values()} == lora_ids

    # Injection groups.
    assert {id(p) for p in inj.mem_out.parameters()} == mem_ids
    assert {id(p) for p in inj.gate.parameters()} == gate_ids

    # Excluded: every base weight and the frozen query_proj.
    optimized = lora_ids | mem_ids | gate_ids
    base_named = [p for n, p in model.named_parameters() if "lora_" not in n]
    assert base_named and all(id(p) not in optimized for p in base_named)
    assert all(id(p) not in optimized for p in inj.query_proj.parameters())

    # Group LRs are independent.
    lrs = [g["lr"] for g in opt.param_groups]
    assert lrs == [1e-4, 1e-3, 1e-3]


def test_joint_steps_move_mem_out_and_gate_base_frozen():
    model, inj, reader, snap = _joint_setup()
    tok = FakeTokenizer()
    pairs = [
        ("案例摘要: 收入稳定", "批准放款。"),
        ("案例摘要: 多头借贷", "拒绝。"),
        ("案例摘要: 负债率高", "拒绝。"),
        ("案例摘要: 储蓄充足", "批准放款。"),
    ]
    examples = [encode_pair(tok, p, c, 64) for p, c in pairs]
    for e in examples:  # uniform width via tiling; keep the supervised tail
        e["input_ids"] = (e["input_ids"] * 3)[-24:]
        e["labels"] = (e["labels"] * 3)[-24:]
        assert any(l != -100 for l in e["labels"])
    opt = build_joint_optimizer(model, inj, lora_lr=1e-3, injection_lr=1e-2)
    model.train()

    for step in range(4):
        e = examples[step % len(examples)]
        reader.stage([_staged_hits(2, seed=step)])
        batch = collate_uniform([e])
        loss = model(**batch).loss
        loss.backward()
        opt.step()
        opt.zero_grad()

    assert float(inj.mem_out.weight.abs().sum()) > 0.0, "mem_out stayed at zero-init"
    assert float(inj.gate.weight.abs().sum()) > 0.0, "gate never received a gradient"
    assert_base_unchanged(snap, model)  # runtime guard: base bitwise frozen
    # (snapshot refs follow the same Parameter objects through peft wrapping)
    inj.detach()


def test_length_grouped_batches_preserve_completion_tail():
    examples = [
        {"input_ids": [1, 2, 3, 4, 5], "labels": [-100, -100, 3, 4, 5]},
        {"input_ids": [7, 8, 9], "labels": [-100, 8, 9]},
        {"input_ids": [11, 12, 13, 14], "labels": [-100, -100, 13, 14]},
    ]
    rng = np.random.default_rng(0)
    chunks = length_grouped_batches(examples, 2, rng)
    assert sorted(len(c) for c in chunks) == [1, 2]
    for chunk in chunks:
        batch = collate_uniform(chunk)
        width = min(len(e["input_ids"]) for e in chunk)
        assert batch["input_ids"].shape == (len(chunk), width)
        assert torch.equal(batch["attention_mask"], torch.ones_like(batch["input_ids"]))
        # Left-truncation keeps the tail: supervised tokens survive.
        assert (batch["labels"] != -100).any()
    # The 2-item chunk is left-truncated to the shorter item's width.
    two = next(c for c in chunks if len(c) == 2)
    batch = collate_uniform(two)
    assert batch["input_ids"].shape[1] == 3
    long_item = next(e for e in two if len(e["input_ids"]) == 4)
    assert batch["input_ids"][two.index(long_item)].tolist() == [12, 13, 14]


def test_staged_reader_order_and_miss():
    reader = StagedMemoryReader()
    hits = _staged_hits(2)
    q = np.zeros(QUERY_DIM, dtype=np.float32)
    assert reader.read(q, 8) == []  # unstaged -> miss, never stale hits
    reader.stage([hits, None, hits])
    assert reader.read(q, 8) == hits
    assert reader.read(q, 8) == []  # explicit miss
    assert reader.read(q, 1) == hits[:1]  # top_k truncation
    assert reader.read(q, 8) == []  # queue drained
