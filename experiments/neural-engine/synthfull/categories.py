"""Zipf-distributed high-cardinality categorical sampling.

Value frequency follows a Zipf law: P(value i) ~ (i+1)^-alpha over a fixed
pool. Sampling uses inverse-CDF transform on a precomputed cumulative
distribution, which is O(log pool) per draw, fully vectorized, and consumes
exactly one uniform per case (fixed RNG draw order).
"""

from __future__ import annotations

import numpy as np

from synthfull.config import CategoricalSpec


class CategoricalSampler:
    """Samples value indices for one categorical field."""

    def __init__(self, spec: CategoricalSpec) -> None:
        self.spec = spec
        ranks = np.arange(1, spec.pool_size + 1, dtype=np.float64)
        probs = ranks ** -spec.zipf_alpha
        self.probs = probs / probs.sum()
        self._cdf = np.cumsum(self.probs)

    def sample(self, rng: np.random.Generator, n: int) -> np.ndarray:
        """Value indices [n] int32 (one uniform draw per case)."""
        u = rng.random(n)
        idx = np.searchsorted(self._cdf, u, side="right")
        return np.minimum(idx, self.spec.pool_size - 1).astype(np.int32)

    def top_share(self, fraction: float) -> float:
        """Analytic probability mass of the top `fraction` of the pool."""
        k = max(1, int(round(self.spec.pool_size * fraction)))
        return float(self.probs[:k].sum())
