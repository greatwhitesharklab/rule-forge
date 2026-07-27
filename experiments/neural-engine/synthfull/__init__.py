"""CLAB-full: synthetic credit world generator (upgrade of CLAB-lite).

Three modalities — numeric factor observables, high-cardinality Zipf
categoricals, behavior event sequences — plus a RANDOM causal rule layer
(RandomRuleGenerator), regime switching (Geometric(0.1) gaps), and outcomes
delayed 1~3 episodes. Blind discipline: rule content is seed-determined and
never referenced anywhere in code, comments, tests, or docs.
"""

from synthfull.categories import CategoricalSampler
from synthfull.config import (
    CATEGORICALS,
    EVENT_VOCAB,
    MAX_SEQ_LEN,
    MODE_NAMES,
    MODES,
    SEQ_STAT_NAMES,
    CategoricalSpec,
    FullConfig,
    ModeSpec,
    default_config,
)
from synthfull.rulegen import (
    EXPERIENCE,
    HELDOUT,
    Condition,
    FeatureView,
    FullRule,
    RandomRuleGenerator,
    rules_payload,
    rules_to_json,
)
from synthfull.sequences import seq_stats
from synthfull.world import (
    CaseBook,
    FullWorld,
    GroundTruth,
    OutcomeLedger,
    RegimeEvent,
    WeightMutation,
    WorldData,
)

__all__ = [
    "CategoricalSampler",
    "CategoricalSpec",
    "CATEGORICALS",
    "EVENT_VOCAB",
    "MAX_SEQ_LEN",
    "MODE_NAMES",
    "MODES",
    "SEQ_STAT_NAMES",
    "FullConfig",
    "ModeSpec",
    "default_config",
    "EXPERIENCE",
    "HELDOUT",
    "Condition",
    "FeatureView",
    "FullRule",
    "RandomRuleGenerator",
    "rules_payload",
    "rules_to_json",
    "seq_stats",
    "CaseBook",
    "FullWorld",
    "GroundTruth",
    "OutcomeLedger",
    "RegimeEvent",
    "WeightMutation",
    "WorldData",
]
