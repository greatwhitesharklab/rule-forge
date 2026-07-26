"""Adapter mount/unmount tests: peft-standard attach and exact base restore."""

from __future__ import annotations

from pathlib import Path

import torch

from _tiny import FakeTokenizer, make_tiny_qwen3
from llm import LocalLLM
from lora.distill import DistillPair
from lora.mount import mount_adapter, unmount_adapter
from lora.train import LoraTrainConfig, train_lora


def logits(model, tok, text: str) -> torch.Tensor:
    with torch.no_grad():
        return model(**{k: v for k, v in tok(text).items()}).logits


def trained_adapter(tmp_path: Path):
    torch.manual_seed(0)
    model = make_tiny_qwen3()
    tok = FakeTokenizer()
    pairs = [DistillPair(prompt="案例", completion="批准", source="case")] * 2
    art = train_lora(
        model, tok, pairs, LoraTrainConfig(lr=1e-2, max_steps=10, batch_size=2),
        output_root=tmp_path, today="20260726",
    )
    return model, tok, art.adapter_dir


class TestRawModelMount:
    def test_trained_adapter_changes_logits(self, tmp_path: Path) -> None:
        model, tok, adapter_dir = trained_adapter(tmp_path)
        before = logits(model, tok, "hello world")
        mounted = mount_adapter(model, adapter_dir)
        after = logits(mounted, tok, "hello world")
        assert not torch.equal(before, after)

    def test_unmount_restores_base_bitwise(self, tmp_path: Path) -> None:
        model, tok, adapter_dir = trained_adapter(tmp_path)
        before = logits(model, tok, "hello world")
        mounted = mount_adapter(model, adapter_dir)
        restored = unmount_adapter(mounted)
        assert torch.equal(before, logits(restored, tok, "hello world"))


class TestLocalLLMMount:
    def test_mount_and_unmount_through_wrapper(self, tmp_path: Path) -> None:
        model, tok, adapter_dir = trained_adapter(tmp_path)
        llm = LocalLLM(model=model, tokenizer=tok)
        before = logits(llm.model, tok, "hello world")
        mount_adapter(llm, adapter_dir)
        assert not torch.equal(before, logits(llm.model, tok, "hello world"))
        unmount_adapter(llm)
        assert torch.equal(before, logits(llm.model, tok, "hello world"))

    def test_unmount_without_adapter_is_noop(self, tmp_path: Path) -> None:
        model, tok, _ = trained_adapter(tmp_path)
        llm = LocalLLM(model=model, tokenizer=tok)
        before = llm.model
        unmount_adapter(llm)
        assert llm.model is before
