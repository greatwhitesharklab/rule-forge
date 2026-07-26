"""CLAB-lite world configuration (design doc §5 P0).

All generative parameters of the synthetic credit world live here so that
unit tests and P1 consumers share a single source of truth. The config is
deterministic: every stochastic draw comes from one seeded numpy Generator
inside ``SyntheticWorld``, which is what makes same-seed runs bit-identical.

Layering:
    8 latent factors  -> 6 concepts (sigmoid of factor z-scores)
    concepts + active rule weights -> bad logit -> outcome
Concepts are the carrier of experience: a credit officer can learn concepts,
not raw factors (§1.2 — slot value_text is stated over concepts).
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace

# ---------------------------------------------------------------------------
# Latent factors (8)
# ---------------------------------------------------------------------------
# Each factor is sampled per case from a fixed distribution, then standardized
# with hand-set (loc, scale) ≈ (mean, std) of that distribution. Concepts are
# computed on the standardized values, so coefficients are comparable.
#
# obs_model / obs_param describe how the decision-time OBSERVABLE proxy field
# is derived from the latent factor:
#   "add"  -> x + N(0, obs_param), then clipped to obs_clip
#   "mul"  -> x * exp(N(0, obs_param))   (multiplicative noise, x >= 0)
#   "thin" -> Binomial(x, obs_param)     (under-reporting of count factors)
# The observable is a noisy proxy: concept truth is never exposed as a field.


@dataclass(frozen=True)
class FactorSpec:
    name: str
    dist: str  # "beta" | "gamma" | "poisson" | "lognormal"
    params: tuple[float, ...]
    loc: float  # standardization constant (~dist mean)
    scale: float  # standardization constant (~dist std)
    observable: str  # decision-time visible field name
    obs_model: str  # "add" | "mul" | "thin"
    obs_param: float
    obs_clip: tuple[float, float] | None = None


FACTORS: tuple[FactorSpec, ...] = (
    # Monthly income stability in [0,1] (1 = perfectly stable salary).
    FactorSpec("income_stability", "beta", (5.0, 2.0), 0.714, 0.160,
               "income_volatility_obs", "add", 0.08, (0.0, 1.0)),
    # Debt-to-income ratio (existing monthly debt service / monthly income).
    FactorSpec("debt_burden", "gamma", (2.0, 0.2), 0.40, 0.283,
               "debt_to_income_obs", "mul", 0.10, (0.0, 3.0)),
    # Length of credit history in years.
    FactorSpec("credit_history_years", "gamma", (2.5, 2.0), 5.0, 3.16,
               "credit_history_years_reported", "add", 0.5, (0.0, 40.0)),
    # Number of past delinquencies on file (under-reported in applications).
    FactorSpec("past_delinquencies", "poisson", (0.8,), 0.8, 0.894,
               "delinquencies_reported", "thin", 0.6, None),
    # Tenure at current employer in years.
    FactorSpec("employment_tenure_years", "gamma", (2.0, 1.5), 3.0, 2.12,
               "months_employed", "add", 0.5, (0.0, 480.0)),
    # Liquid savings expressed in months of living expenses.
    FactorSpec("savings_months", "lognormal", (0.182, 0.9), 1.8, 2.39,
               "savings_months_obs", "mul", 0.15, (0.0, 60.0)),
    # Requested loan amount / annual income.
    FactorSpec("loan_to_income", "gamma", (2.0, 0.25), 0.5, 0.354,
               "requested_loan_to_income", "mul", 0.10, (0.0, 5.0)),
    # Number of lending platforms with active loans (多头借贷).
    FactorSpec("platform_loan_count", "poisson", (1.5,), 1.5, 1.225,
               "platform_loans_disclosed", "thin", 0.8, None),
)

# income_volatility_obs is a reversed proxy of income_stability (volatility
# rises when stability falls); months_employed is 12x tenure. These two are
# handled by explicit scale transforms in world._observe().

OBSERVABLE_TRANSFORMS: dict[str, tuple[str, float]] = {
    # observable name -> (transform, factor multiplier applied before noise)
    "income_volatility_obs": ("reverse", 1.0),  # y = (1 - x) + noise
    "months_employed": ("scale", 12.0),  # y = 12 * x + noise
}


# ---------------------------------------------------------------------------
# Concepts (6)
# ---------------------------------------------------------------------------
# concept = sigmoid(intercept + sum(coef * z(factor))). Coefficients are the
# business meaning of the concept; intercepts place the marginal concept mean
# near 0.5 so rule thresholds cut the tails.


@dataclass(frozen=True)
class ConceptSpec:
    name: str
    cn_name: str  # human-readable label (value_text material, §1.2)
    intercept: float
    terms: tuple[tuple[str, float], ...]  # (factor_name, coefficient)


CONCEPTS: tuple[ConceptSpec, ...] = (
    ConceptSpec("cash_flow_strain", "现金流紧张度", -0.2, (
        ("debt_burden", 0.9), ("loan_to_income", 0.8), ("savings_months", -0.7))),
    ConceptSpec("credit_worthiness", "信用资质", -0.3, (
        ("credit_history_years", 0.8), ("past_delinquencies", -1.0),
        ("employment_tenure_years", 0.4))),
    ConceptSpec("over_leverage", "多头借贷程度", -0.6, (
        ("platform_loan_count", 1.0), ("debt_burden", 0.6))),
    ConceptSpec("repayment_capacity", "偿付能力", -0.4, (
        ("income_stability", 0.9), ("savings_months", 0.7),
        ("employment_tenure_years", 0.3))),
    ConceptSpec("borrowing_aggressiveness", "借贷激进度", -0.5, (
        ("loan_to_income", 0.8), ("platform_loan_count", 0.9))),
    ConceptSpec("profile_stability", "画像稳定性", -0.5, (
        ("income_stability", 0.8), ("employment_tenure_years", 0.6),
        ("credit_history_years", 0.5))),
)


# ---------------------------------------------------------------------------
# World config
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class WorldConfig:
    """All knobs of the synthetic world; stochasticity comes from `seed`."""

    seed: int = 20260726
    # Regime switching: each episode switches with prob switch_prob, i.e.
    # inter-switch gap ~ Geometric(switch_prob), expected 10 episodes (§5 P0).
    switch_prob: float = 0.1
    # Outcome delay: label becomes visible `delay` episodes after generation
    # (post-loan seasoning), inclusive range [delay_min, delay_max].
    delay_min: int = 1
    delay_max: int = 3
    # Outcome model: logit = base_logit + sum(concept_logit_coef * (c - 0.5))
    #                + sum(fired rule weights) + N(0, noise_std)
    base_logit: float = -2.1
    noise_std: float = 0.35
    concept_logit_coef: tuple[tuple[str, float], ...] = (
        ("cash_flow_strain", 1.2), ("credit_worthiness", -1.0),
        ("over_leverage", 1.0), ("repayment_capacity", -0.8),
        ("borrowing_aggressiveness", 0.5), ("profile_stability", -0.6),
    )
    # Regime drift: on a switch, each rule is mutated with this probability.
    drift_rule_fraction: float = 0.3
    factors: tuple[FactorSpec, ...] = field(default=FACTORS)
    concepts: tuple[ConceptSpec, ...] = field(default=CONCEPTS)


def default_config(seed: int = 20260726, **overrides: object) -> WorldConfig:
    """World config for a given seed; `overrides` are dataclass field patches
    (e.g. switch_prob=0.0 for a static world in tests)."""
    return replace(WorldConfig(seed=seed), **overrides)
