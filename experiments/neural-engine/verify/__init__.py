"""Local verifiers (design doc §3): the immune system of the loop.

Every cloud output is a candidate until one of these accepts it; each verdict
carries a quality score Q ∈ [0,1] for the cloud reputation ledger.
"""

from .analysis import verify_analysis
from .explanation import DEFAULT_BANNED_WORDS, verify_explanation
from .feature import (
    FeatureThresholds,
    backtest_frame,
    backtest_frame_from_data,
    verify_feature,
)
from .metrics import coverage, direction_free_auc, information_value, lift
from .sandbox import SandboxResult, check_expression_ast, run_expression
from .verdict import FAIL, PASS, QUARANTINE, Verdict

__all__ = [
    "DEFAULT_BANNED_WORDS",
    "FAIL",
    "PASS",
    "QUARANTINE",
    "FeatureThresholds",
    "SandboxResult",
    "Verdict",
    "backtest_frame",
    "backtest_frame_from_data",
    "check_expression_ast",
    "coverage",
    "direction_free_auc",
    "information_value",
    "lift",
    "run_expression",
    "verify_analysis",
    "verify_explanation",
    "verify_feature",
]
