"""LoRA training tests (design doc §1.4 peft spec, §4 step ④).

All unit tests run on the tiny random Qwen3 fixture (hidden 64 / 3 layers /
vocab 1000). Its module tree exposes all seven spec target_modules
(q/k/v/o_proj + gate/up/down_proj), so the production LoraConfig is used
verbatim — no test-only target list.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
import torch

from _tiny import FakeTokenizer, make_tiny_qwen3
from lora.distill import DistillPair
from lora.train import (
    LoraTrainConfig,
    assert_base_unchanged,
    encode_pair,
    snapshot_base_params,
    train_lora,
)


def pairs(n: int = 6) -> list[DistillPair]:
    return [
        DistillPair(prompt=f"案例摘要: case {i}", completion=f"结论 {i}: 批准", source="case")
        for i in range(n)
    ]


def fast_cfg(**kw) -> LoraTrainConfig:
    # High LR + few steps: enough to move the tiny model measurably.
    opts = {"lr": 1e-2, "max_steps": 30, "batch_size": 2}
    opts.update(kw)
    return LoraTrainConfig(**opts)


class TestEncodePair:
    def test_prompt_tokens_are_masked(self) -> None:
        tok = FakeTokenizer()
        enc = encode_pair(tok, "prompt text", "completion", max_len=512)
        n_prompt = len(tok("prompt text", return_tensors="pt")["input_ids"][0])
        labels = enc["labels"]
        assert labels[:n_prompt] == [-100] * n_prompt
        assert all(l != -100 for l in labels[n_prompt:])
        assert len(enc["input_ids"]) == len(labels)

    def test_overlong_pairs_keep_the_completion_tail(self) -> None:
        tok = FakeTokenizer()
        enc = encode_pair(tok, "p" * 100, "completion", max_len=20)
        assert len(enc["input_ids"]) == 20
        # the surviving supervised tokens are the completion's tail
        assert enc["labels"][-1] != -100


class TestBaseFrozen:
    def test_snapshot_detects_mutation(self) -> None:
        model = make_tiny_qwen3()
        snap = snapshot_base_params(model)
        with torch.no_grad():
            next(model.parameters()).add_(1.0)
        with pytest.raises(AssertionError):
            assert_base_unchanged(snap, model)

    def test_snapshot_passes_when_untouched(self) -> None:
        model = make_tiny_qwen3()
        snap = snapshot_base_params(model)
        assert_base_unchanged(snap, model)  # no raise

    def test_training_leaves_base_weights_bitwise_identical(self, tmp_path: Path) -> None:
        model = make_tiny_qwen3()
        snap = snapshot_base_params(model)
        train_lora(model, FakeTokenizer(), pairs(), fast_cfg(), output_root=tmp_path, today="20260726")
        assert_base_unchanged(snap, model)  # train_lora also asserts internally


class TestTrainLora:
    def test_loss_decreases_and_only_lora_params_move(self, tmp_path: Path) -> None:
        torch.manual_seed(0)
        model = make_tiny_qwen3()
        art = train_lora(
            model, FakeTokenizer(), pairs(), fast_cfg(), output_root=tmp_path, today="20260726"
        )
        assert art.steps > 0
        assert art.meta["first_loss"] > art.meta["final_loss"]
        # the saved adapter actually learned (lora_B left its zero init)...
        from safetensors.torch import load_file

        weights = load_file(str(art.adapter_dir / "adapter_model.safetensors"))
        lora_b = [t for n, t in weights.items() if "lora_B" in n]
        assert lora_b and any(float(t.abs().sum()) > 0 for t in lora_b)
        # ...while the caller's model was restored to the pristine base
        assert not any("lora_" in n for n, _ in model.named_parameters())

    def test_adapter_dir_is_date_versioned_with_sequence(self, tmp_path: Path) -> None:
        tok = FakeTokenizer()
        a1 = train_lora(make_tiny_qwen3(), tok, pairs(), fast_cfg(), output_root=tmp_path, today="20260726")
        a2 = train_lora(make_tiny_qwen3(), tok, pairs(), fast_cfg(), output_root=tmp_path, today="20260726")
        assert a1.adapter_dir.name == "20260726"
        assert a2.adapter_dir.name == "20260726-2"
        assert (a1.adapter_dir / "adapter_model.safetensors").exists()
        assert (a2.adapter_dir / "adapter_config.json").exists()

    def test_meta_json_records_provenance(self, tmp_path: Path) -> None:
        art = train_lora(
            make_tiny_qwen3(), FakeTokenizer(), pairs(4), fast_cfg(),
            output_root=tmp_path, today="20260726",
        )
        meta = json.loads((art.adapter_dir / "meta.json").read_text())
        assert meta["n_examples"] == 4
        assert meta["peft"] == {
            "r": 16, "lora_alpha": 32, "lora_dropout": 0.05,
            "target_modules": ["q_proj", "k_proj", "v_proj", "o_proj",
                               "gate_proj", "up_proj", "down_proj"],
        }
        assert meta["duration_sec"] >= 0
        assert "git_commit" in meta  # None off-repo; the key is always present

    def test_zero_steps_saves_identity_adapter(self, tmp_path: Path) -> None:
        """max_steps=0 yields a valid untrained adapter (LoRA B is zero-init,
        so the adapter is an exact identity map) — used as a no-op challenger."""
        art = train_lora(
            make_tiny_qwen3(), FakeTokenizer(), pairs(), fast_cfg(max_steps=0),
            output_root=tmp_path, today="20260726",
        )
        assert art.steps == 0
        assert (art.adapter_dir / "adapter_config.json").exists()

    def test_early_stop_triggers_when_loss_stalls(self, tmp_path: Path) -> None:
        # A single repeated example saturates quickly; patience=3 must cut the
        # run well before max_steps.
        one = pairs(1)
        art = train_lora(
            make_tiny_qwen3(), FakeTokenizer(), one,
            fast_cfg(max_steps=500, patience=3), output_root=tmp_path, today="20260726",
        )
        assert art.steps < 500
