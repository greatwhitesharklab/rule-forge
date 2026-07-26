"""Lazy-loading local LLM wrapper (design doc §1.1, P2 generation).

The local LLM exists for three jobs only: reading unstructured text, Scribe
experience induction, and carrying the LoRA slow-memory channel. This wrapper
keeps it boring: construction/import never touches the network or loads the
~1.2GB weights — loading happens on first generate()/load() call. CPU-first:
float32 on CPU (bf16 arithmetic is *slower* than fp32 on typical x86 CPUs,
and P2 runs on a busy shared box with no guaranteed GPU headroom).
"""

from __future__ import annotations

from typing import Any

import torch


class LocalLLM:
    """Thin lazy wrapper around a HF causal LM + its tokenizer.

    Parameters
    ----------
    model_id:
        HF repo id (default Qwen/Qwen3-0.6B per design §1.1).
    device:
        Torch device string; default "cpu" (GPU is shared/occupied in P2).
    model / tokenizer:
        Optional pre-built objects (tests inject a tiny random Qwen3 + fake
        tokenizer). Supplying them marks the wrapper as loaded and bypasses
        from_pretrained entirely.
    """

    DEFAULT_MODEL_ID = "Qwen/Qwen3-0.6B"

    def __init__(
        self,
        model_id: str = DEFAULT_MODEL_ID,
        device: str = "cpu",
        model: Any | None = None,
        tokenizer: Any | None = None,
    ):
        self.model_id = model_id
        self.device = device
        self._model = model
        self._tokenizer = tokenizer

    @property
    def loaded(self) -> bool:
        return self._model is not None and self._tokenizer is not None

    @property
    def model(self) -> Any:
        if not self.loaded:
            self.load()
        return self._model

    @property
    def tokenizer(self) -> Any:
        if not self.loaded:
            self.load()
        return self._tokenizer

    def load(self) -> "LocalLLM":
        """Actually pull weights into memory (idempotent)."""
        if self.loaded:
            return self
        from transformers import AutoModelForCausalLM, AutoTokenizer

        self._tokenizer = AutoTokenizer.from_pretrained(self.model_id)
        dtype = torch.float32 if self.device == "cpu" else torch.bfloat16
        self._model = (
            AutoModelForCausalLM.from_pretrained(self.model_id, dtype=dtype)
            .to(self.device)
            .eval()
        )
        return self

    @torch.no_grad()
    def generate(
        self,
        prompt: str,
        max_new_tokens: int = 64,
        temperature: float = 0.0,
        top_p: float = 1.0,
    ) -> str:
        """Generate the continuation of `prompt` (prompt itself excluded)."""
        if not self.loaded:
            self.load()
        inputs = self._tokenizer(prompt, return_tensors="pt")
        inputs = {k: v.to(self.device) for k, v in inputs.items()}
        gen_kwargs: dict[str, Any] = {"max_new_tokens": max_new_tokens}
        if temperature and temperature > 0:
            gen_kwargs.update(do_sample=True, temperature=temperature, top_p=top_p)
        else:
            gen_kwargs["do_sample"] = False  # greedy; no sampling kwargs (warnings)
        out = self._model.generate(**inputs, **gen_kwargs)
        new_ids = out[0][inputs["input_ids"].shape[1]:]
        return self._tokenizer.decode(new_ids, skip_special_tokens=True)
