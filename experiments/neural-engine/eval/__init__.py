"""P1 acceptance experiment (design doc §5): two-arm contrast + four curves."""

from eval.arms import (
    Arm,
    ArmConfig,
    EmbeddingCache,
    build_arms,
    memory_score,
    mix_scores,
    readonly_hits,
    rep_bad,
    warmup_memory,
)
from eval.curves import (
    Alignment,
    bootstrap_ci,
    decision_profit,
    dividend_curve,
    reputation_alignment,
    zero_shot_auc,
)
from eval.harness import (
    ArmResult,
    EpisodeRecord,
    ExperimentConfig,
    ExperimentResult,
    run_experiment,
)

__all__ = [
    "Arm",
    "ArmConfig",
    "EmbeddingCache",
    "build_arms",
    "memory_score",
    "mix_scores",
    "readonly_hits",
    "rep_bad",
    "warmup_memory",
    "Alignment",
    "bootstrap_ci",
    "decision_profit",
    "dividend_curve",
    "reputation_alignment",
    "zero_shot_auc",
    "ArmResult",
    "EpisodeRecord",
    "ExperimentConfig",
    "ExperimentResult",
    "run_experiment",
]
