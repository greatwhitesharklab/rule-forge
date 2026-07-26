"""Real Qwen3-0.6B LoRA smoke test (CPU, local HF cache).

Marked `slow_model`: collected by default, skippable via `-m "not slow_model"`.
Skips (never hard-fails) when the model snapshot is not in the local cache —
same policy as test_llm_real_model.py. Trains 2 steps on 3 short pairs and
checks the artifact + mount/unmount round-trip, nothing about quality.
"""

from __future__ import annotations

from pathlib import Path

import pytest
import torch

from llm import LocalLLM
from lora.distill import DistillPair
from lora.mount import mount_adapter, unmount_adapter
from lora.train import LoraTrainConfig, train_lora

pytestmark = pytest.mark.slow_model

MODEL_ID = "Qwen/Qwen3-0.6B"


def _model_snapshot_ready() -> bool:
    try:
        from huggingface_hub import scan_cache_dir

        for repo in scan_cache_dir().repos:
            if repo.repo_id != MODEL_ID:
                continue
            for rev in repo.revisions:
                names = {f.file_name for f in rev.files}
                if {"model.safetensors", "config.json", "tokenizer.json"} <= names:
                    return True
    except Exception:
        pass
    return False


@pytest.fixture(scope="module")
def llm() -> LocalLLM:
    if not _model_snapshot_ready():
        pytest.skip(f"{MODEL_ID} snapshot not complete in HF cache")
    try:
        return LocalLLM(MODEL_ID, device="cpu").load()
    except Exception as exc:
        pytest.skip(f"{MODEL_ID} unavailable: {exc}")


def test_real_model_lora_smoke(llm: LocalLLM, tmp_path: Path) -> None:
    pairs = [
        DistillPair(prompt="案例摘要: 收入稳定", completion="批准", source="case"),
        DistillPair(prompt="案例摘要: 多头借贷", completion="拒绝", source="case"),
        DistillPair(prompt="经验复述:", completion="现金流覆盖是首要信号", source="slot"),
    ]
    cfg = LoraTrainConfig(lr=1e-4, max_steps=2, batch_size=2, max_len=64)
    art = train_lora(llm.model, llm.tokenizer, pairs, cfg,
                     output_root=tmp_path, today="20260726")
    assert (art.adapter_dir / "adapter_model.safetensors").exists()
    assert art.meta["n_examples"] == 3

    ids = llm.tokenizer("信贷审批", return_tensors="pt")["input_ids"]
    with torch.no_grad():
        base_logits = llm.model(ids).logits
    mount_adapter(llm, art.adapter_dir)
    assert isinstance(llm.generate("信贷审批中", max_new_tokens=4), str)
    unmount_adapter(llm)
    with torch.no_grad():
        assert torch.equal(base_logits, llm.model(ids).logits)
