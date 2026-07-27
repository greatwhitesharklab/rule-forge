"""CLAB-full world configuration: three modalities + random causal layer.

Extends CLAB-lite with (1) high-cardinality Zipf categoricals (device
fingerprint / phone prefix / region), (2) per-case behavior event sequences
generated from latent behavior modes, and (3) a RANDOM causal rule layer
produced by ``RandomRuleGenerator`` instead of hand-written rules.

Blind discipline: this module (and every module/test/doc in the project)
defines mechanisms and distribution PARAMETERS only. No concrete generated
rule content — chosen fields, thresholds, value sets, or weights of any
specific seed — may appear anywhere in the repository.

The numeric latent-factor layer reuses the CLAB-lite factor specs so both
worlds share one source of truth for the credit-risk covariates.
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace

from synth.config import FACTORS, OBSERVABLE_TRANSFORMS, FactorSpec

__all__ = [
    "CategoricalSpec",
    "CATEGORICALS",
    "EVENT_VOCAB",
    "EVENT_DUR",
    "SUBMIT_EVENT",
    "ModeSpec",
    "MODES",
    "MODE_NAMES",
    "MAX_SEQ_LEN",
    "SEQ_STAT_NAMES",
    "FullConfig",
    "default_config",
    "FACTORS",
    "OBSERVABLE_TRANSFORMS",
    "FactorSpec",
]


# ---------------------------------------------------------------------------
# High-cardinality categorical modality
# ---------------------------------------------------------------------------
# Usage frequency follows a Zipf law: value i has probability ~ i^-alpha.
# A few values are extremely hot (fraud farms, popular devices), the tail is
# long. Pool sizes and skew are config knobs, not rule content.


@dataclass(frozen=True)
class CategoricalSpec:
    name: str  # casebook field name
    cn_name: str  # human-readable label for rule text templates
    pool_size: int
    zipf_alpha: float


CATEGORICALS: tuple[CategoricalSpec, ...] = (
    CategoricalSpec("device_id", "设备指纹", 50_000, 1.05),
    CategoricalSpec("phone_prefix", "手机号段", 200, 1.10),
    CategoricalSpec("region", "地域", 50, 0.80),
)


# ---------------------------------------------------------------------------
# Behavior-sequence modality
# ---------------------------------------------------------------------------
# Each case carries one event sequence over a small vocabulary; every event
# has a duration (seconds). Sequences are generated from a LATENT behavior
# mode (normal / hesitant / instant / batch). The mode never enters the
# feature side — only the events and durations do.

EVENT_VOCAB: tuple[str, ...] = (
    "field_focus", "field_edit", "paste", "backspace", "submit",
    "idle_long", "scroll", "blur", "verify", "otp_wait",
)
SUBMIT_EVENT: int = EVENT_VOCAB.index("submit")

# Per-event log-duration (seconds) parameters: (mu, sigma) of the lognormal.
EVENT_DUR: tuple[tuple[float, float], ...] = (
    (-0.7, 0.5), (0.2, 0.5), (-1.9, 0.4), (-1.2, 0.4), (-1.6, 0.4),
    (1.8, 0.5), (-0.2, 0.5), (-0.9, 0.5), (0.9, 0.5), (2.1, 0.3),
)


@dataclass(frozen=True)
class ModeSpec:
    name: str
    prob: float  # population share
    len_range: tuple[int, int]  # inclusive event-count range
    speed: float  # multiplicative duration factor (>1 slower)
    event_probs: tuple[float, ...]  # over EVENT_VOCAB


# fmt: off
MODES: tuple[ModeSpec, ...] = (
    # Normal applicants: balanced editing flow.
    ModeSpec("normal", 0.70, (12, 30), 1.0,
             (0.22, 0.20, 0.03, 0.06, 0.08, 0.05, 0.15, 0.08, 0.08, 0.05)),
    # Hesitant applicants: long, many pauses and corrections.
    ModeSpec("hesitant", 0.15, (20, 40), 1.6,
             (0.15, 0.18, 0.02, 0.12, 0.05, 0.18, 0.12, 0.10, 0.05, 0.03)),
    # Instant fillers: very few steps, very fast, paste-heavy.
    ModeSpec("instant", 0.10, (4, 8), 0.25,
             (0.30, 0.10, 0.18, 0.01, 0.15, 0.01, 0.10, 0.02, 0.08, 0.05)),
    # Batch/farm applicants: paste-driven templated flow.
    ModeSpec("batch", 0.05, (10, 18), 0.5,
             (0.28, 0.12, 0.25, 0.02, 0.10, 0.02, 0.08, 0.03, 0.06, 0.04)),
)
# fmt: on

MODE_NAMES: tuple[str, ...] = tuple(m.name for m in MODES)
MAX_SEQ_LEN: int = max(m.len_range[1] for m in MODES)

# Sequence-level statistics exposed to the rule generator as condition
# sources (all computable from decision-time features).
SEQ_STAT_NAMES: tuple[str, ...] = (
    "seq_len", "paste_count", "backspace_count", "idle_count", "edit_count",
    "focus_count", "total_duration", "mean_duration", "max_duration",
    "paste_ratio",
)


# ---------------------------------------------------------------------------
# Full world config
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class FullConfig:
    """All knobs of the CLAB-full world; stochasticity comes from `seed`.

    The same seed drives BOTH the random rule generator and the world
    sampling (independent Generator streams), so one seed fully determines
    rule content and data.
    """

    seed: int = 20260726
    # Regime switching: per-episode switch probability (gap ~ Geometric(p)).
    switch_prob: float = 0.1
    # Outcome delay (episodes until label visibility), inclusive range.
    delay_min: int = 1
    delay_max: int = 3
    # Outcome model: logit = base_logit + sum(fired rule weights) + noise.
    base_logit: float = -2.2
    noise_std: float = 0.35
    # Regime drift: on a switch, each rule's weight mutates with this prob.
    drift_rule_fraction: float = 0.3
    # Random causal layer: pool sizes (experience pool disclosed downstream,
    # held-out pool active but never disclosed).
    n_experience: int = 20
    n_heldout: int = 10
    # Condition source mix: (numeric bin, categorical value-set, seq stat).
    cond_kind_probs: tuple[float, float, float] = (0.5, 0.3, 0.2)
    # Conditions per rule: probabilities for 1, 2, 3 conjuncts.
    cond_count_probs: tuple[float, float, float] = (0.5, 0.35, 0.15)
    # Categorical value-set sizes: probabilities for 1, 2, 3 values; pools
    # larger than `big_pool_threshold` get +`big_pool_bonus` values so their
    # fire rates stay non-degenerate.
    cat_set_size_probs: tuple[float, float, float] = (0.4, 0.35, 0.25)
    big_pool_threshold: int = 10_000
    big_pool_bonus: int = 1
    # Rule effect (logit delta): magnitude range and P(positive direction).
    weight_mag_range: tuple[float, float] = (0.25, 0.8)
    pos_weight_prob: float = 0.55
    # Numeric / seq-stat thresholds cut a TAIL of the pilot distribution:
    # ">" draws q from tail_hi_q, "<" from tail_lo_q.
    tail_hi_q: tuple[float, float] = (0.55, 0.90)
    tail_lo_q: tuple[float, float] = (0.10, 0.45)
    # Pilot sample size used by the generator to calibrate thresholds.
    pilot_size: int = 4096
    # Degenerate-rule rejection: a candidate rule is redrawn while its PILOT
    # fire rate falls outside [fire_floor, fire_cap] (statistical check only,
    # blind to rule content); after max_redraws the last candidate is kept.
    fire_floor: float = 0.003
    fire_cap: float = 0.95
    max_redraws: int = 50
    # Modality specs.
    factors: tuple[FactorSpec, ...] = field(default=FACTORS)
    categoricals: tuple[CategoricalSpec, ...] = field(default=CATEGORICALS)
    modes: tuple[ModeSpec, ...] = field(default=MODES)


def default_config(seed: int = 20260726, **overrides: object) -> FullConfig:
    """Full config for a given seed; `overrides` are dataclass field patches
    (e.g. switch_prob=0.0 for a static world in tests)."""
    return replace(FullConfig(seed=seed), **overrides)
