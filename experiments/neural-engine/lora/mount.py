"""Adapter mount/unmount via peft's standard mechanism (design §1.4 挂载).

mount_adapter wraps the base model in a PeftModel with the given adapter
generation loaded; unmount_adapter (PeftModel.unload) strips the wrapper and
hands back the bitwise-identical base — the same object tree the other
channels (injection weld, generation) keep references to.

Both accept either a raw HF model or the LocalLLM wrapper; with LocalLLM the
mounted/restored model is swapped in place so llm.generate() picks it up.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from llm import LocalLLM


def mount_adapter(model_or_llm: Any, adapter_dir: str | Path) -> Any:
    """Attach the adapter generation at `adapter_dir`; returns the mounted object."""
    from peft import PeftModel

    if isinstance(model_or_llm, LocalLLM):
        model_or_llm._model = PeftModel.from_pretrained(
            model_or_llm.model, str(adapter_dir)
        )
        return model_or_llm
    return PeftModel.from_pretrained(model_or_llm, str(adapter_dir))


def unmount_adapter(mounted: Any) -> Any:
    """Detach the adapter and restore the base model (bitwise identical)."""
    from peft import PeftModel

    if isinstance(mounted, LocalLLM):
        if isinstance(mounted._model, PeftModel):
            mounted._model = mounted._model.unload()
        return mounted
    if isinstance(mounted, PeftModel):
        return mounted.unload()
    return mounted
