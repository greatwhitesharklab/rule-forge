"""Shared test helpers: a tiny random-weight Qwen3 + a fake tokenizer.

No network, no downloads — the model is built from a small Qwen3Config with
random weights, so construction is sub-second on CPU. Importable from sibling
test modules because pytest prepends this directory to sys.path (no
__init__.py in tests/).
"""

from __future__ import annotations

import torch
from transformers import Qwen3Config, Qwen3ForCausalLM


def make_tiny_qwen3(seed: int = 0, vocab_size: int = 1000, layers: int = 3) -> Qwen3ForCausalLM:
    """Random-weight Qwen3 small enough for CPU unit tests (hidden 64)."""
    torch.manual_seed(seed)
    cfg = Qwen3Config(
        hidden_size=64,
        num_hidden_layers=layers,  # >= 3 so the default weld layer_idx=2 exists
        intermediate_size=128,
        num_attention_heads=2,
        num_key_value_heads=2,
        vocab_size=vocab_size,
        pad_token_id=0,
        eos_token_id=1,
        bos_token_id=2,
    )
    return Qwen3ForCausalLM(cfg).eval()


class FakeTokenizer:
    """Deterministic byte-level stand-in; vocab ids stay inside the tiny vocab."""

    pad_token_id = 0
    eos_token_id = 1

    def __init__(self, vocab_size: int = 1000):
        self.vocab_size = vocab_size

    def __call__(self, text: str, return_tensors: str = "pt") -> dict[str, torch.Tensor]:
        ids = [(b * 7 + 3) % (self.vocab_size - 8) + 8 for b in text.encode("utf-8")]
        if not ids:
            ids = [4]
        t = torch.tensor([ids], dtype=torch.long)
        return {"input_ids": t, "attention_mask": torch.ones_like(t)}

    def decode(self, ids, skip_special_tokens: bool = True) -> str:
        return " ".join(str(int(i)) for i in ids)
