"""Feature pipeline: L0 raw observables + L1 rule-derived features.

Design doc §8.1 (four-layer feature structure) and §8.2 (unified pipeline,
human/machine-isomorphic): every feature is an executable function registered
with name / version / author, and its docstring is the assumption statement.

§8.3 rule 1 (online/offline consistency): ``FeatureRegistry.compute`` is the
ONLY feature-computation entry point. Training and inference both call it —
there is no second implementation to drift apart.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

import numpy as np
import pandas as pd

FeatureFn = Callable[[pd.DataFrame], pd.Series]

_EPS = 1e-6  # guards divisions against zero denominators


@dataclass(frozen=True)
class FeatureSpec:
    """Executable feature definition with provenance (§8.2 form)."""

    name: str
    version: str
    author: str
    level: str  # "L0" raw field | "L1" rule-derived
    assumption: str  # business hypothesis, taken from the function docstring
    func: FeatureFn


class FeatureRegistry:
    """Ordered feature registry; `compute` is the single computation path."""

    def __init__(self) -> None:
        self._specs: dict[str, FeatureSpec] = {}

    def register(self, spec: FeatureSpec) -> FeatureSpec:
        if not spec.name or not spec.version or not spec.author:
            raise ValueError("feature requires non-empty name/version/author")
        if not spec.assumption.strip():
            raise ValueError(f"feature {spec.name!r} requires a docstring assumption")
        if spec.name in self._specs:
            raise ValueError(f"duplicate feature: {spec.name!r}")
        self._specs[spec.name] = spec
        return spec

    def __len__(self) -> int:
        return len(self._specs)

    @property
    def names(self) -> tuple[str, ...]:
        return tuple(self._specs)

    def spec(self, name: str) -> FeatureSpec:
        return self._specs[name]

    def specs(self) -> tuple[FeatureSpec, ...]:
        return tuple(self._specs.values())

    def compute(self, obs: pd.DataFrame) -> pd.DataFrame:
        """Compute the full feature matrix from raw observable fields.

        Same function for train and inference (§8.3 rule 1). Column order is
        registry order, so matrices are schema-stable across calls.
        """
        cols = {
            name: np.asarray(spec.func(obs), dtype=np.float64)
            for name, spec in self._specs.items()
        }
        return pd.DataFrame(cols, index=obs.index)


def feature(
    *,
    name: str,
    version: str,
    author: str,
    level: str = "L1",
    registry: FeatureRegistry,
) -> Callable[[FeatureFn], FeatureFn]:
    """Decorator registering a feature function; its docstring is the
    assumption statement (§8.2 definition form)."""

    def deco(fn: FeatureFn) -> FeatureFn:
        registry.register(
            FeatureSpec(
                name=name,
                version=version,
                author=author,
                level=level,
                assumption=(fn.__doc__ or "").strip(),
                func=fn,
            )
        )
        return fn

    return deco


def build_default_registry(observable_names: tuple[str, ...]) -> FeatureRegistry:
    """L0 passthroughs for every observable + the built-in L1 set.

    The L1 features are rule-derived ratios/interactions over DECISION-TIME
    fields only (CaseBook observables) — no labels, no latent truth.
    """
    reg = FeatureRegistry()

    for col in observable_names:

        def _passthrough(df: pd.DataFrame, _col: str = col) -> pd.Series:
            return df[_col]

        _passthrough.__doc__ = f"L0 raw field passthrough: {col} (no assumption)."
        reg.register(
            FeatureSpec(col, "1.0", "synth.world", "L0",
                        _passthrough.__doc__, _passthrough)
        )

    @feature(name="total_leverage", version="1.0", author="p1_baseline", registry=reg)
    def total_leverage(df: pd.DataFrame) -> pd.Series:
        """Existing debt service plus the requested loan burden: total
        repayment pressure relative to income predicts default better than
        either term alone."""
        return df["debt_to_income_obs"] + df["requested_loan_to_income"]

    @feature(name="savings_runway", version="1.0", author="p1_baseline", registry=reg)
    def savings_runway(df: pd.DataFrame) -> pd.Series:
        """Liquid savings per unit of debt burden: a liquidity buffer absorbs
        income shocks, so high runway lowers default risk."""
        return df["savings_months_obs"] / (df["debt_to_income_obs"] + _EPS)

    @feature(name="delinquency_intensity", version="1.0", author="p1_baseline",
             registry=reg)
    def delinquency_intensity(df: pd.DataFrame) -> pd.Series:
        """Past delinquencies per year of credit history: normalizing by the
        exposure window separates chronic offenders from long histories with
        isolated incidents."""
        return df["delinquencies_reported"] / (df["credit_history_years_reported"] + 1.0)

    @feature(name="multi_platform_leverage", version="1.0", author="p1_baseline",
             registry=reg)
    def multi_platform_leverage(df: pd.DataFrame) -> pd.Series:
        """Multi-platform borrowing interacts with new loan size: stacking a
        large request on top of many active platforms signals liquidity
        desperation."""
        return df["platform_loans_disclosed"] * df["requested_loan_to_income"]

    @feature(name="job_income_stability", version="1.0", author="p1_baseline",
             registry=reg)
    def job_income_stability(df: pd.DataFrame) -> pd.Series:
        """Employment tenure (log-scaled) only protects when income is also
        stable: a long-tenured borrower with volatile income is still risky."""
        return np.log1p(df["months_employed"]) * (1.0 - df["income_volatility_obs"])

    return reg
