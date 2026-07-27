"""自学习特征迭代闭环(新定位:记忆用在训练/迭代阶段,推理由 GBDT 执行)。

本地出题(GBDT 指路 + 策略记忆 + G1 模板)→ 云端构造(AgentBridge 真实在环
/ replay 固化重放)→ 本地验证(沙箱 + dev 窗回测 + §8.4 门槛)→ 入库
(L2 特征注册 + shadow 特征槽 / retired 死路槽)→ GBDT 重训。
"""

from .config import DEFAULT_LGBM_PARAMS, LoopConfig
from .features import compile_l2_expression, max_abs_correlation, register_l2_feature
from .gbdt import (
    importance_top,
    predict_bad_proba,
    profile_unexplained,
    regime_stats,
    residual_signal_analysis,
    train_gbdt,
    unexplained_bads,
)
from .loop import ProposalOutcome, RoundRecord, SelfLearnLoop
from .memory import StrategyMemory
from .replay import ReplayFeature, ReplayFormatError, ReplayProvider, load_replay

__all__ = [
    "DEFAULT_LGBM_PARAMS",
    "LoopConfig",
    "ProposalOutcome",
    "ReplayFeature",
    "ReplayFormatError",
    "ReplayProvider",
    "RoundRecord",
    "SelfLearnLoop",
    "StrategyMemory",
    "compile_l2_expression",
    "importance_top",
    "load_replay",
    "max_abs_correlation",
    "predict_bad_proba",
    "profile_unexplained",
    "regime_stats",
    "register_l2_feature",
    "train_gbdt",
    "unexplained_bads",
]
