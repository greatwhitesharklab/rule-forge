"""P2 local-LLM package: lazy model wrapper + Engram gated injection weld."""

from .injection import MemoryHit, MemoryInjection, MemoryReader, SlotReader
from .local_llm import LocalLLM

__all__ = [
    "LocalLLM",
    "MemoryHit",
    "MemoryInjection",
    "MemoryReader",
    "SlotReader",
]
