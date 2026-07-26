"""Embedding layer: text vectorization for retrieval keys and experience
value_text (design doc §1.1, P1)."""

from .canonicalize import (
    FIELD_MAP,
    FieldSpec,
    bin_label,
    canonicalize,
    case_row_from_casebook,
    experience_text,
)
from .encoder import KEY_DIM, VALUE_DIM, Embedder
from .memory import SemanticMemory

__all__ = [
    "Embedder",
    "KEY_DIM",
    "VALUE_DIM",
    "SemanticMemory",
    "FIELD_MAP",
    "FieldSpec",
    "bin_label",
    "canonicalize",
    "case_row_from_casebook",
    "experience_text",
]
