"""Real Qwen3-0.6B integration tests (CPU, local HF cache).

Marked `slow_model`: collected by default, skippable via `-m "not slow_model"`.
Never hard-fails on a missing/incomplete download — the P2 environment may
still be fetching the 1.2GB snapshot in the background; absence means skip.
"""

from __future__ import annotations

import numpy as np
import pytest
import torch

from llm import LocalLLM, MemoryHit, MemoryInjection

pytestmark = pytest.mark.slow_model

MODEL_ID = "Qwen/Qwen3-0.6B"


def _model_snapshot_ready() -> bool:
    """True iff the HF cache holds a complete-enough Qwen3-0.6B snapshot."""
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


class _ConstReader:
    """Fixed 1024-d hits (real hidden size) so zero-init identity is tested
    against actual memory content, not an empty retrieval."""

    def __init__(self, n: int = 3, seed: int = 0):
        rng = np.random.default_rng(seed)
        self.hits = [
            MemoryHit(
                value_vec=rng.standard_normal(1024).astype(np.float32),
                weight=0.8 - 0.2 * i,
                key_vec=rng.standard_normal(256).astype(np.float32),
            )
            for i in range(n)
        ]

    def read(self, query: np.ndarray, k: int) -> list[MemoryHit]:
        assert query.shape == (256,)
        return self.hits[:k]


@pytest.fixture(scope="module")
def llm() -> LocalLLM:
    if not _model_snapshot_ready():
        pytest.skip(f"{MODEL_ID} snapshot not complete in HF cache")
    try:
        return LocalLLM(MODEL_ID, device="cpu").load()
    except Exception as exc:  # partial download, offline box, corrupt file...
        pytest.skip(f"{MODEL_ID} unavailable: {exc}")


def test_generate_smoke(llm: LocalLLM) -> None:
    out = llm.generate("信贷审批中,最重要的风险信号是", max_new_tokens=8)
    assert isinstance(out, str)


def test_hidden_size_matches_slot_value_dim(llm: LocalLLM) -> None:
    """The design's injection channel: Qwen3-0.6B hidden == slot value_vec dim."""
    assert llm.model.config.hidden_size == 1024


def test_zero_init_identity_real_model(llm: LocalLLM) -> None:
    ids = llm.tokenizer("现金流紧张、多头借贷的申请人", return_tensors="pt")[
        "input_ids"
    ]
    with torch.no_grad():
        baseline = llm.model(ids).logits
    with MemoryInjection(llm.model, _ConstReader(), layer_idx=2) as inj:
        with torch.no_grad():
            welded = llm.model(ids).logits
        assert all(h > 0 for h in inj.last_hits)
        assert all(g > 0.0 for g in inj.last_gates)
    diff = (welded - baseline).abs().max().item()
    assert diff < 1e-5, f"max|delta| = {diff}"
