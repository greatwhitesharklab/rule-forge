"""Numeric latent-factor layer for CLAB-full.

Same generative approach as CLAB-lite (see synth/world.py): per-case latent
factors from fixed distributions, then decision-time observable proxies with
additive / multiplicative / thinning noise. Factored into pure functions so
both the world and the rule generator's threshold-calibration pilot share
one implementation (identical draw order).
"""

from __future__ import annotations

import numpy as np

from synth.config import OBSERVABLE_TRANSFORMS, FactorSpec


def sample_factors(
    rng: np.random.Generator, specs: tuple[FactorSpec, ...], n: int
) -> np.ndarray:
    """Latent factors [n, len(specs)], drawn column by column (fixed order)."""
    return np.stack([_sample_one(rng, f, n) for f in specs], axis=1)


def observe_factors(
    rng: np.random.Generator, specs: tuple[FactorSpec, ...], factors: np.ndarray
) -> np.ndarray:
    """Decision-time observable proxies [n, len(specs)] of the factors."""
    return np.stack(
        [_observe_one(rng, f, factors[:, i], factors.shape[0])
         for i, f in enumerate(specs)],
        axis=1,
    )


def _sample_one(rng: np.random.Generator, spec: FactorSpec, n: int) -> np.ndarray:
    p, dist = spec.params, spec.dist
    if dist == "beta":
        return rng.beta(p[0], p[1], n)
    if dist == "gamma":
        return rng.gamma(p[0], p[1], n)
    if dist == "poisson":
        return rng.poisson(p[0], n).astype(np.float64)
    if dist == "lognormal":
        return rng.lognormal(p[0], p[1], n)
    raise ValueError(f"unknown dist: {dist}")


def _observe_one(
    rng: np.random.Generator, spec: FactorSpec, x: np.ndarray, n: int
) -> np.ndarray:
    name = spec.observable
    if name in OBSERVABLE_TRANSFORMS:
        kind, mult = OBSERVABLE_TRANSFORMS[name]
        x = (1.0 - x) if kind == "reverse" else x * mult
    model, param = spec.obs_model, spec.obs_param
    if model == "add":
        y = x + rng.normal(0.0, param, n)
    elif model == "mul":
        y = x * np.exp(rng.normal(0.0, param, n))
    elif model == "thin":
        y = rng.binomial(x.astype(np.int64), param).astype(np.float64)
    else:
        raise ValueError(f"unknown obs_model: {model}")
    return np.clip(y, *spec.obs_clip) if spec.obs_clip else y
