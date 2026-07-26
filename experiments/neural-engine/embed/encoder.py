"""Encoder wrapper: text -> retrieval keys / value vectors (design doc §1.1).

P1 local encoder is Qwen3-Embedding-0.6B (CPU-feasible, MRL dimensions):
  * embed_keys   -> float32 [N, 256]  (slot key_vec, FAISS retrieval)
  * embed_values -> float32 [N, 1024] (slot value_vec, content/injection)

The model (~1.2GB) is loaded lazily on first encode, never at import or
construction; Embedder.default() is a thread-safe lazy singleton. An
`encode_fn` can be injected to bypass the model entirely (tests use a
deterministic hash projection, see embed.fake).

All outputs are L2-normalized: the slot store uses IndexFlatIP, so inner
product == cosine similarity.
"""

from __future__ import annotations

import threading
from typing import Callable, Sequence

import numpy as np

KEY_DIM = 256
VALUE_DIM = 1024
DEFAULT_MODEL = "Qwen/Qwen3-Embedding-0.6B"

# Injected encoder: (texts, target_dim) -> [N, target_dim] (need not be
# normalized; Embedder normalizes afterwards).
EncodeFn = Callable[[Sequence[str], int], np.ndarray]


class Embedder:
    """Lazy-loading text encoder with injectable encode function."""

    _default: "Embedder | None" = None
    _default_lock = threading.Lock()

    def __init__(
        self,
        model_name: str = DEFAULT_MODEL,
        device: str = "cpu",
        encode_fn: EncodeFn | None = None,
    ) -> None:
        self.model_name = model_name
        self.device = device
        self._encode_fn = encode_fn
        self._model: object | None = None
        self._load_lock = threading.Lock()

    @classmethod
    def default(cls) -> "Embedder":
        """Process-wide shared instance (double-checked locking)."""
        if cls._default is None:
            with cls._default_lock:
                if cls._default is None:
                    cls._default = cls()
        return cls._default

    def embed_keys(self, texts: Sequence[str]) -> np.ndarray:
        """Retrieval keys: float32 [N, 256], L2-normalized."""
        return self._encode(texts, KEY_DIM)

    def embed_values(self, texts: Sequence[str]) -> np.ndarray:
        """Content vectors: float32 [N, 1024], L2-normalized."""
        return self._encode(texts, VALUE_DIM)

    # ------------------------------------------------------------------ internals

    def _encode(self, texts: Sequence[str], dim: int) -> np.ndarray:
        if not texts:
            return np.zeros((0, dim), dtype=np.float32)
        if self._encode_fn is not None:
            out = np.asarray(self._encode_fn(list(texts), dim), dtype=np.float32)
        else:
            model = self._load_model()
            out = np.asarray(
                model.encode(list(texts), convert_to_numpy=True), dtype=np.float32
            )
            # MRL: any prefix of the full embedding is a valid vector; slice
            # to the target dim and let the normalization below rescale it.
            out = out[:, :dim]
        if out.shape != (len(texts), dim):
            raise ValueError(
                f"encoder returned shape {out.shape}, expected {(len(texts), dim)}"
            )
        norms = np.linalg.norm(out, axis=1, keepdims=True)
        norms[norms == 0.0] = 1.0
        return (out / norms).astype(np.float32)

    def _load_model(self) -> object:
        """Load SentenceTransformer on first use (double-checked locking).

        The import lives inside this method so `import embed` stays cheap and
        the 1.2GB weight load happens only when a real encode is requested.
        """
        if self._model is None:
            with self._load_lock:
                if self._model is None:
                    from sentence_transformers import SentenceTransformer

                    self._model = SentenceTransformer(
                        self.model_name, truncate_dim=VALUE_DIM, device=self.device
                    )
        return self._model
