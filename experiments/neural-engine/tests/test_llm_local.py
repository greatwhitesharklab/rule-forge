"""LocalLLM wrapper tests (P2 task 1): lazy loading, device injection, generate.

All tests run on the tiny random-weight Qwen3 — no network, no real weights.
Real-model coverage lives in test_llm_real_model.py (marked slow_model).
"""

from __future__ import annotations

import pytest
import transformers

from _tiny import FakeTokenizer, make_tiny_qwen3
from llm import LocalLLM


def test_construction_does_not_load_model(monkeypatch: pytest.MonkeyPatch) -> None:
    """Constructing a LocalLLM must not touch from_pretrained (no download,
    no 1.2GB load) — loading is deferred to first use / explicit load()."""

    def boom(*args, **kwargs):
        raise AssertionError("from_pretrained called during construction")

    monkeypatch.setattr(
        transformers.AutoModelForCausalLM, "from_pretrained", staticmethod(boom)
    )
    monkeypatch.setattr(transformers.AutoTokenizer, "from_pretrained", staticmethod(boom))
    llm = LocalLLM(model_id="Qwen/Qwen3-0.6B")
    assert not llm.loaded


def test_import_does_not_load() -> None:
    """Module import must not have instantiated any model (lazy by design)."""
    import llm.local_llm as mod

    assert not any(
        isinstance(v, transformers.PreTrainedModel) for v in vars(mod).values()
    )


def test_device_defaults_to_cpu() -> None:
    assert LocalLLM(model_id="unused").device == "cpu"


def test_generate_greedy_with_injected_tiny_model() -> None:
    llm = LocalLLM(model=make_tiny_qwen3(seed=1), tokenizer=FakeTokenizer(), device="cpu")
    assert llm.loaded  # injected weights count as loaded (test path)
    out = llm.generate("申请人负债率偏高", max_new_tokens=3)
    assert isinstance(out, str)


def test_generate_sampling_path() -> None:
    llm = LocalLLM(model=make_tiny_qwen3(seed=2), tokenizer=FakeTokenizer())
    out = llm.generate("hello", max_new_tokens=2, temperature=0.7, top_p=0.9)
    assert isinstance(out, str)


def test_inputs_are_placed_on_configured_device() -> None:
    llm = LocalLLM(model=make_tiny_qwen3(seed=3), tokenizer=FakeTokenizer(), device="cpu")
    out = llm.generate("x", max_new_tokens=1)
    assert next(llm.model.parameters()).device.type == "cpu"
    assert isinstance(out, str)
