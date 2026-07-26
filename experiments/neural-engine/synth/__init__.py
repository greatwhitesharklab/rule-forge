"""CLAB-lite: synthetic credit world generator (design doc §5 P0).

A reproducible, drifting synthetic credit world with known ground truth:
8 latent factors -> 6 concepts -> 20 experience-pool rules + 10 held-out
rules, regime switching (Geometric(0.1) gaps), outcomes delayed 1~3 episodes.
"""

from synth.config import CONCEPTS, FACTORS, WorldConfig, default_config
from synth.rules import (
    EXPERIENCE,
    EXPERIENCE_RULES,
    HELDOUT,
    HELDOUT_RULES,
    Rule,
    build_rule_pool,
)
from synth.world import (
    CaseBook,
    GroundTruth,
    OutcomeLedger,
    RegimeEvent,
    SyntheticWorld,
    WeightMutation,
    WorldData,
)

__all__ = [
    "CONCEPTS",
    "FACTORS",
    "WorldConfig",
    "default_config",
    "EXPERIENCE",
    "HELDOUT",
    "EXPERIENCE_RULES",
    "HELDOUT_RULES",
    "Rule",
    "build_rule_pool",
    "CaseBook",
    "GroundTruth",
    "OutcomeLedger",
    "RegimeEvent",
    "SyntheticWorld",
    "WeightMutation",
    "WorldData",
]
