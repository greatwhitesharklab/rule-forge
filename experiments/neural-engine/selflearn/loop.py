"""自学习特征迭代闭环主循环(设计文档 §8.5 自动探索循环)。

每轮迭代(全部在 dev 窗上,eval 窗禁入):
  ① GBDT 指路:当前特征库训练 GBDT,dev 尾段留出集上挑"解释不了的坏账"
     (proba 低但 outcome=1, top_k)+ 特征重要性 top;
  ② 记忆查询:策略槽库检索相关经验 + 死路清单;
  ③ 出题:G1 feature_proposal 模板(context=疑难画像聚合统计/现有特征/
     死路/regime 段统计)→ 云端(AgentBridge 真实在环 / replay 固化重放);
  ④ 验证:沙箱 + dev 窗回测(§8.4:IV/lift/覆盖率/泄漏)+ 相关性 < 0.9;
  ⑤ 入库:pass 注册 L2 特征(author=云端 provenance)+ shadow 特征槽;
     fail 写死路槽(retired + 死因);
  ⑥ GBDT 重训,记录 dev 留出集 AUC 变化。

时间红线:构造时断言帧内所有 episode ∈ [dev_start, dev_end];验证帧剥离
outcome/episode(verify.backtest_frame_from_data)。eval 窗数据在本类里
不存在任何入口。
"""

from __future__ import annotations

import math
import re
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Callable

import numpy as np
import pandas as pd
from sklearn.metrics import roc_auc_score

from prompts import make_prompt
from scoring.features import FeatureRegistry
from verify import FAIL, PASS, QUARANTINE, backtest_frame_from_data, verify_feature

from .config import LoopConfig
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
from .memory import (
    CAUSE_DISCRIMINATION,
    CAUSE_COVERAGE,
    CAUSE_LEAK,
    CAUSE_NAME,
    CAUSE_REPLACED,
    CAUSE_SANDBOX,
    StrategyMemory,
)
from .types import RoundExtras

_NAME_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")

# base_features(df) -> (X, names);生产:eval.lending_acceptance.build_base_features
BaseFeaturesFn = Callable[[pd.DataFrame], tuple[np.ndarray, list[str]]]


@dataclass
class ProposalOutcome:
    """单个云端特征提案的裁决记录。"""

    name: str
    expression: str
    rationale: str
    verdict: str  # "pass" | "fail" | "quarantine"
    reasons: tuple[str, ...] = ()
    metrics: dict[str, float] = field(default_factory=dict)
    q: float = 0.0

    def as_dict(self) -> dict[str, Any]:
        return {
            "name": self.name, "expression": self.expression,
            "rationale": self.rationale, "verdict": self.verdict,
            "reasons": list(self.reasons), "metrics": self.metrics, "q": self.q,
        }


@dataclass
class RoundRecord:
    """一轮迭代的完整记录(验收脚本落 jsonl)。"""

    round_no: int
    task_id: str
    n_unexplained: int
    auc_before: float | None
    auc_after: float | None
    proposals: list[ProposalOutcome]
    accepted: list[str]
    # 阶段 1.2 instrumentation:reward 计算需要的额外数据。
    # 默认 None 保持向后兼容(老调用方/验收脚本不感知);baseline runner 填充它。
    extras: "RoundExtras | None" = None

    def as_dict(self) -> dict[str, Any]:
        d = {
            "round": self.round_no, "task_id": self.task_id,
            "n_unexplained": self.n_unexplained,
            "auc_dev_before": self.auc_before, "auc_dev_after": self.auc_after,
            "accepted": self.accepted,
            "proposals": [p.as_dict() for p in self.proposals],
        }
        if self.extras is not None:
            d["extras"] = {"cloud_calls": self.extras.cloud_calls,
                           "dead_end_repeats": self.extras.dead_end_repeats}
        return d


class SelfLearnLoop:
    """dev 窗上的特征迭代闭环;eval 窗在本类无任何入口。"""

    def __init__(
        self,
        dev_df: pd.DataFrame,
        *,
        config: LoopConfig,
        base_features: BaseFeaturesFn,
        cloud: Any,  # CloudLLM 同构:execute(TaskPackage) -> TaskResult
        memory: StrategyMemory,
        registry: FeatureRegistry | None = None,
        run_id: str | None = None,
    ) -> None:
        cfg = config
        if "episode" not in dev_df.columns or "outcome" not in dev_df.columns:
            raise ValueError("loop frame needs 'episode' and 'outcome' columns")
        episodes = dev_df["episode"].astype(str)
        outside = episodes[(episodes < cfg.dev_start) | (episodes > cfg.dev_end)]
        if len(outside):
            raise ValueError(
                f"time red-line: loop frame contains episode {outside.iloc[0]!r} "
                f"outside dev window [{cfg.dev_start}, {cfg.dev_end}] — "
                "the eval window must never enter iteration"
            )
        dev_eps = sorted(episodes.unique())
        if len(dev_eps) <= cfg.dev_holdout_episodes:
            raise ValueError(
                f"dev window has {len(dev_eps)} episodes, needs more than "
                f"dev_holdout_episodes={cfg.dev_holdout_episodes}"
            )

        self.cfg = cfg
        self.cloud = cloud
        self.memory = memory
        self.registry = registry or FeatureRegistry()
        # Run-scoped task_id suffix: same-day reruns must not collide on
        # bridge outbox/inbox file names (paired by task_id). Replay aligns
        # on the selflearn-rNN round prefix, so the suffix is replay-safe.
        self.run_id = run_id or (
            datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
            + "-" + uuid.uuid4().hex[:6]
        )

        df = dev_df.copy()
        df["episode"] = episodes
        df = df.sort_values("episode", kind="stable").reset_index(drop=True)
        self._episodes = df["episode"].to_numpy()
        # 验证帧:剥离 outcome/episode,表达式沙箱永远见不到标签与时间键
        self.verify_df, self.labels = backtest_frame_from_data(df)
        self._X_base, self.base_names = base_features(self.verify_df)

        holdout_eps = set(dev_eps[-cfg.dev_holdout_episodes:])
        self._holdout_mask = np.isin(self._episodes, list(holdout_eps))

        # §8.4 相关性参考矩阵:现有特征(基础矩阵,等距子采样控成本),
        # 新特征入库后追加其采样值。
        self._corr_stride = max(1, len(df) // cfg.corr_sample)
        self._corr_ref = pd.DataFrame(
            self._X_base[:: self._corr_stride], columns=self.base_names
        )

    # ---------------------------------------------------------------- 特征矩阵

    def current_matrix(self) -> tuple[np.ndarray, list[str]]:
        """当前特征库矩阵(基础特征 + 已入库 L2);训练/打分唯一入口。"""
        if len(self.registry) == 0:
            return self._X_base, list(self.base_names)
        X_l2 = self.registry.compute(self.verify_df).to_numpy()
        return (
            np.hstack([self._X_base, X_l2]),
            list(self.base_names) + list(self.registry.names),
        )

    # ---------------------------------------------------------------- GBDT 指路

    def _train_and_score(self) -> tuple[Any, np.ndarray, float | None, list[dict]]:
        X, names = self.current_matrix()
        train_idx = np.nonzero(~self._holdout_mask)[0]
        if len(train_idx) > self.cfg.max_train_rows:
            train_idx = train_idx[-self.cfg.max_train_rows:]
        test_idx = np.nonzero(self._holdout_mask)[0]
        model = train_gbdt(X[train_idx], self.labels[train_idx],
                           params=self.cfg.lgbm_params, seed=self.cfg.seed)
        proba = predict_bad_proba(model, X)
        y_te = self.labels[test_idx]
        auc = (
            float(roc_auc_score(y_te, proba[test_idx]))
            if len(np.unique(y_te)) == 2 else float("nan")
        )
        top = importance_top(model, names, self.cfg.importance_top)
        return model, proba, auc, top

    # ---------------------------------------------------------------- 单轮迭代

    def run_round(self, round_no: int) -> RoundRecord:
        cfg = self.cfg
        task_id = f"selflearn-r{round_no:02d}-{self.run_id}"

        # ① GBDT 指路(dev 留出集样本外)
        _, proba, auc_before, imp_top = self._train_and_score()
        hold_idx = np.nonzero(self._holdout_mask)[0]
        hold_df = self.verify_df.iloc[hold_idx]
        hold_labels = self.labels[hold_idx]
        hold_proba = proba[hold_idx]
        # Missed bads are searched on the holdout only: training rows are
        # explained by construction, their "misses" carry no residual signal.
        idx = unexplained_bads(hold_labels, hold_proba, cfg.top_k)
        profile = profile_unexplained(hold_df.assign(outcome=hold_labels), idx)
        residual = residual_signal_analysis(
            hold_df, hold_labels, hold_proba, cfg.top_k,
            n_bins=cfg.residual_bins,
            top_numeric=cfg.residual_top_numeric,
            top_categorical=cfg.residual_top_categorical,
            top_tokens=cfg.residual_top_tokens,
        )

        # ② 记忆查询:相关经验 + 死路清单
        query = (
            f"解释不了的坏账画像: {profile.get('n', 0)} 例; "
            f"重要性 top: {', '.join(t['feature'] for t in imp_top[:5])}"
        )
        experience = self.memory.experience_summaries(query, k=5)
        dead_ends = self.memory.dead_end_list()

        # ③ 出题(G1 feature_proposal;context 全是聚合统计,无逐行 PII)
        existing = list(self.base_names) + list(self.registry.names)
        payload = {
            "task_id": task_id,
            "context": {
                "case_profiles": [profile],
                "existing_features": existing,
                "dead_ends": dead_ends,
                "regime_stats": regime_stats(
                    pd.DataFrame({"episode": self._episodes, "outcome": self.labels})
                ),
                "importance_top": imp_top,
                # Quantitative map: same-proba-bin comparison, so differences
                # are residual signal the current GBDT does NOT exploit.
                "residual_signals": residual,
                "residual_signals_guide": (
                    "Missed bads vs goods scored in the SAME proba bin: every "
                    "difference here is residual signal the current GBDT does "
                    "not exploit — prioritize features over these fields. "
                    "numeric: sorted by |cohens_d| (missed vs control mean gap "
                    "in full-frame std units), with distribution stats and KS; "
                    "categorical: missed_share minus control_share per value; "
                    "emp_title_tokens: token frequency gaps, direction hints "
                    "for text-derived features."
                ),
            },
            "constraints": {
                "max_features": cfg.max_features_per_round,
                "must_be_executable": (
                    "pandas/numpy expression over df; sandbox whitelist (§3.2) applies"
                ),
                "no_future_info": True,
            },
        }
        g1 = make_prompt(
            "feature_proposal", payload,
            retriever=lambda _p: experience,
            dead_end_lookup=lambda _p: dead_ends,
        )
        result = self.cloud.execute(g1.package)
        author = f"{result.provenance.provider}:{result.provenance.model}#{task_id}"
        proposals = result.content.get("features", [])[: cfg.max_features_per_round]

        # ④ 验证 + ⑤ 入库/死路
        # 阶段 1.2 instrumentation:采本死路档案的 name 集合(判重复用)。
        # 死路槽的 value_text 格式见 memory.add_dead_end:"死路:{name} — {statement};死因:{cause}"
        # 提取 name = "死路:" 后到 " — " 前的子串。
        dead_end_names: set[str] = set()
        for s in self.memory.service.store.all_slots():
            if s.status != "retired" or not s.value_text.startswith("死路:"):
                continue
            # "死路:{name} — ..." → name
            rest = s.value_text[len("死路:"):]
            dead_end_names.add(rest.split(" — ", 1)[0])

        outcomes: list[ProposalOutcome] = []
        accepted: list[str] = []
        batch_names: set[str] = set()
        dead_end_repeats = 0
        for prop in proposals:
            name = str(prop.get("name", ""))
            # 计数:提案 name 已在死路档案 = 重复探索(reward B 的信号)
            if name and name in dead_end_names:
                dead_end_repeats += 1
            outcome = self._judge(prop, existing, batch_names, author)
            batch_names.add(prop.get("name", ""))
            outcomes.append(outcome)
            if outcome.verdict == "pass":
                accepted.append(outcome.name)

        # ⑥ GBDT 重训,记录 dev 留出集 AUC 变化
        auc_after = auc_before
        if accepted:
            _, _, auc_after, _ = self._train_and_score()

        # 阶段 1.2:产 RoundExtras(reward 计算用)。
        # cloud_calls=1:run_round 现在一次只调一次云端(line 276);留字段为编排器扩展。
        extras = RoundExtras(cloud_calls=1, dead_end_repeats=dead_end_repeats)
        return RoundRecord(
            round_no=round_no, task_id=task_id, n_unexplained=int(len(idx)),
            auc_before=auc_before, auc_after=auc_after,
            proposals=outcomes, accepted=accepted, extras=extras,
        )

    def run(self, rounds: int) -> list[RoundRecord]:
        return [self.run_round(r) for r in range(1, rounds + 1)]

    # ---------------------------------------------------------------- 验证裁决

    def _judge(
        self,
        prop: dict,
        existing: list[str],
        batch_names: set[str],
        author: str,
    ) -> ProposalOutcome:
        name = str(prop.get("name", ""))
        expression = str(prop.get("expression", ""))
        rationale = str(prop.get("rationale", ""))

        def dead_end(cause: str, verdict: str, reasons: tuple[str, ...],
                     metrics: dict[str, float] | None = None, q: float = 0.0):
            self.memory.add_dead_end(name or "(unnamed)", rationale, cause=cause)
            return ProposalOutcome(name, expression, rationale, verdict,
                                   reasons, metrics or {}, q)

        if not _NAME_RE.match(name):
            return dead_end(CAUSE_NAME, FAIL, (f"非法特征名 {name!r}",))
        if name in existing or name in batch_names:
            return dead_end(CAUSE_NAME, FAIL, (f"重名:与现有特征 {name!r} 冲突",))

        v = verify_feature(expression, self.verify_df, self.labels,
                           thresholds=self.cfg.thresholds)
        metrics = {k: (None if not math.isfinite(x) else round(x, 6))
                   for k, x in v.metrics.items()}
        if v.status == QUARANTINE:
            return dead_end(CAUSE_LEAK, QUARANTINE, v.reasons, metrics, v.quality)
        if v.status == FAIL:
            cause = CAUSE_SANDBOX if v.reasons[0].startswith("sandbox:") else (
                CAUSE_COVERAGE if "coverage" in v.reasons[0] else CAUSE_DISCRIMINATION
            )
            return dead_end(cause, FAIL, v.reasons, metrics, v.quality)

        # §8.4 增量价值:与现有特征相关性 < 0.9
        try:
            values = compile_l2_expression(expression)(self.verify_df).to_numpy()
        except Exception as exc:  # 沙箱已过但进程内求值失败(不该发生,防御)
            return dead_end(CAUSE_SANDBOX, FAIL, (f"in-process eval: {exc}",), metrics)
        corr = max_abs_correlation(values, self._corr_ref)
        metrics["max_abs_corr"] = round(corr, 6)
        if corr >= self.cfg.corr_max:
            return dead_end(
                CAUSE_REPLACED, FAIL,
                (f"增量不足:与现有特征最大相关性 {corr:.4f} >= {self.cfg.corr_max}",),
                metrics, v.quality,
            )

        # ⑤ 入库:注册表 L2 + shadow 特征槽
        register_l2_feature(
            self.registry, name=name, expression=expression,
            rationale=rationale, author=author,
        )
        self.memory.add_feature_slot(name, rationale, provenance=author)
        self._corr_ref[name] = values[:: self._corr_stride]
        return ProposalOutcome(name, expression, rationale, PASS, v.reasons,
                               metrics, v.quality)
