"""G1 prompt strategy (design doc §1.3): templates + slot filling, no LLM."""

from .builder import (
    DEFAULT_OUTPUT_SCHEMAS,
    DeadEndLookup,
    G1Prompt,
    Retriever,
    make_prompt,
)
from .templates import (
    CASE_ANALYSIS_TEMPLATE,
    EXPLANATION_TEMPLATE,
    FEATURE_PROPOSAL_TEMPLATE,
    TEMPLATES,
)

__all__ = [
    "CASE_ANALYSIS_TEMPLATE",
    "DEFAULT_OUTPUT_SCHEMAS",
    "EXPLANATION_TEMPLATE",
    "FEATURE_PROPOSAL_TEMPLATE",
    "TEMPLATES",
    "DeadEndLookup",
    "G1Prompt",
    "Retriever",
    "make_prompt",
]
