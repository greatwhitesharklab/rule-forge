"""P1 scoring baseline: feature pipeline + rolling GBDT scorer + policy.

Design doc §8 (feature engineering system) and §4 (nightly consolidation,
where the GBDT retrains on freshly matured outcomes).
"""

from scoring.features import (
    FeatureFn,
    FeatureRegistry,
    FeatureSpec,
    build_default_registry,
    feature,
)
from scoring.policy import Policy, decide
from scoring.scorer import RollingResult, RollingScorer, ScoreBatch, ScorerConfig

__all__ = [
    "FeatureFn",
    "FeatureRegistry",
    "FeatureSpec",
    "build_default_registry",
    "feature",
    "Policy",
    "decide",
    "RollingResult",
    "RollingScorer",
    "ScoreBatch",
    "ScorerConfig",
]
