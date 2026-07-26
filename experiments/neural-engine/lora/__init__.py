"""P2 LoRA consolidation channel (design §1.4): distill-set builder, nightly
candidate trainer, champion-challenger promotion gate, adapter mount/unmount."""

from .distill import CaseRecord, DistillConfig, DistillPair, build_distill_set
from .mount import mount_adapter, unmount_adapter
from .promote import (
    GateThresholds,
    GateVerdict,
    PromotionGate,
    mean_completion_nll,
    old_regime_probe,
)
from .train import (
    AdapterArtifact,
    LoraTrainConfig,
    assert_base_unchanged,
    encode_pair,
    snapshot_base_params,
    train_lora,
)

__all__ = [
    "AdapterArtifact",
    "CaseRecord",
    "DistillConfig",
    "DistillPair",
    "GateThresholds",
    "GateVerdict",
    "LoraTrainConfig",
    "PromotionGate",
    "assert_base_unchanged",
    "build_distill_set",
    "encode_pair",
    "mean_completion_nll",
    "mount_adapter",
    "old_regime_probe",
    "snapshot_base_params",
    "train_lora",
    "unmount_adapter",
]
