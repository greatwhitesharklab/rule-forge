"""Cloud adaptation layer (design doc §2): providers, sanitize, contracts, ledger."""

from .agent_bridge import AgentBridgeProvider, BridgeProtocolError, BridgeTimeoutError
from .contracts import (
    TASK_TYPES,
    ContractError,
    Provenance,
    TaskPackage,
    TaskResult,
    prompt_hash,
    render_prompt,
    validate_package,
    validate_result,
)
from .ledger import CostLedger
from .providers import (
    PROVIDERS,
    CloudLLM,
    MockProvider,
    OpenAIProvider,
    ProviderConfig,
    ProviderConfigError,
    TaskResultError,
)
from .sanitize import (
    OutboundBlockedError,
    PiiHit,
    amount_bucket_code,
    sanitize_text,
    scan_pii,
    set_alert_hook,
    verify_outbound,
)

__all__ = [
    "AgentBridgeProvider",
    "BridgeProtocolError",
    "BridgeTimeoutError",
    "TASK_TYPES",
    "ContractError",
    "Provenance",
    "TaskPackage",
    "TaskResult",
    "prompt_hash",
    "render_prompt",
    "validate_package",
    "validate_result",
    "CostLedger",
    "PROVIDERS",
    "CloudLLM",
    "MockProvider",
    "OpenAIProvider",
    "ProviderConfig",
    "ProviderConfigError",
    "TaskResultError",
    "OutboundBlockedError",
    "PiiHit",
    "amount_bucket_code",
    "sanitize_text",
    "scan_pii",
    "set_alert_hook",
    "verify_outbound",
]
