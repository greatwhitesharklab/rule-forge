"""自学习特征迭代闭环配置(设计文档 §8.5 自动探索循环)。

新定位:记忆用在训练/迭代阶段,推理由 GBDT 执行。本地出题(GBDT 指路 +
策略记忆 + G1 模板)→ 云端构造特征表达式 → 本地验证 → 入库 → GBDT 重训。

时间红线:dev 窗做特征开发/回测/训练;eval 窗只做最终滚动评估,
迭代过程绝不触碰(SelfLearnLoop 构造时硬性断言)。
"""

from __future__ import annotations

from dataclasses import dataclass, field

from verify.feature import FeatureThresholds

# 与 eval.lending_acceptance.LGBM_PARAMS 同参(闭环内外 GBDT 口径一致);
# random_state 训练时按 LoopConfig.seed 注入。
DEFAULT_LGBM_PARAMS: dict[str, object] = {
    "n_estimators": 120,
    "learning_rate": 0.08,
    "num_leaves": 15,
    "min_child_samples": 40,
    "subsample": 0.9,
    "subsample_freq": 1,
    "colsample_bytree": 0.9,
    "deterministic": True,
    "force_col_wise": True,
    "n_jobs": -1,
    "verbose": -1,
}


@dataclass(frozen=True)
class LoopConfig:
    """一轮迭代的所有可调参数;窗边界是字符串 episode(YYYY-MM)字典序比较。"""

    dev_start: str = "2013-01"  # dev 窗:特征开发/回测/GBDT 训练
    dev_end: str = "2015-12"
    eval_start: str = "2016-01"  # eval 窗:仅最终滚动评估用(迭代禁入)
    eval_end: str = "2018-12"

    top_k: int = 20  # GBDT 指路:每轮挑多少个"解释不了的坏账"
    max_features_per_round: int = 3  # 出题预算(探索耗材限流)
    importance_top: int = 10  # 出题 context 携带的特征重要性 top 数
    residual_bins: int = 10  # 残余信号:holdout proba 分箱数
    residual_top_numeric: int = 8  # 残余信号:数值字段 top-N
    residual_top_categorical: int = 5  # 残余信号:类别差异 top-N
    residual_top_tokens: int = 10  # 残余信号:emp_title 词频差 top-N
    corr_max: float = 0.9  # §8.4 增量价值:与现有特征相关性上限(含)
    corr_sample: int = 100_000  # 相关性计算的等距子采样行数上限
    max_train_rows: int = 200_000  # dev 窗 GBDT 单次训练行数上限(取最近)
    dev_holdout_episodes: int = 3  # dev 窗尾段留出,指路与 AUC 记录的样本外评估集
    seed: int = 20260727

    lgbm_params: dict[str, object] = field(
        default_factory=lambda: dict(DEFAULT_LGBM_PARAMS)
    )
    thresholds: FeatureThresholds = field(default_factory=FeatureThresholds)
