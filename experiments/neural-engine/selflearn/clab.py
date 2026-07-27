"""CLAB-lite 合成世界的自学习闭环适配层(机制一实验,闭环侧)。

职责:
  1. 把 synth 世界包装成 SelfLearnLoop 可消费的 dev 帧(episode 字符串 +
     outcome + 8 可观测字段);时间红线 = 帧内标签全部在 dev 窗末已成熟
     (visible_episode <= dev 窗末),未成熟案例直接排除,eval 窗案例绝不入帧;
  2. 基础特征:8 个 L0 原字段 + scoring 内置 L1(复用 build_default_registry);
  3. ClabAutoCloud:自动候选云端(暴力基线臂)——程序化枚举单字段分箱 /
     二阶乘积 / 比值 / 分箱合取,按先验 IV 粗排,每轮以 feature_proposal
     契约格式喂 top-M,回答「给定足够候选,验证漏斗能否筛出规律」。

信息红线:本模块只读 CaseBook / OutcomeLedger(决策时刻可观测信息 + 成熟
结局)。世界的潜在因子、概念与规则池对闭环不可见;发现力判卷在
eval.clab_discovery(测量侧)完成。
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

import numpy as np
import pandas as pd

from cloud.contracts import Provenance, TaskPackage, TaskResult
from scoring.features import build_default_registry
from synth.config import FACTORS
from synth.world import WorldData
from verify.metrics import information_value

from .features import compile_l2_expression

# 决策时刻可观测的 8 个字段(CaseBook 白名单,与 synth 因子一一对应)。
CLAB_FIELDS: tuple[str, ...] = tuple(f.observable for f in FACTORS)

# 字段槽陈述(策略记忆初始化用;只描述字段含义,不含任何规律内容)。
CLAB_FIELD_STATEMENTS: dict[str, str] = {
    "income_volatility_obs": "月收入波动率代理(0~1,越高收入越不稳定)",
    "debt_to_income_obs": "存量债务月供 / 月收入(带噪观测)",
    "credit_history_years_reported": "自报信用历史年限",
    "delinquencies_reported": "自报历史逾期次数(存在少报)",
    "months_employed": "现单位在职月数",
    "savings_months_obs": "流动储蓄可覆盖月数(带噪观测)",
    "requested_loan_to_income": "本次申请金额 / 年收入",
    "platform_loans_disclosed": "自报有未结清贷款的平台数(存在少报)",
}


@dataclass
class ClabSplit:
    """dev 窗切分结果:dev 帧 + 帧内行对应的案例下标(判卷对齐用)。"""

    dev_df: pd.DataFrame  # episode("000" 起,零填充)+ outcome + 8 可观测字段
    dev_case_idx: np.ndarray  # 行对齐的世界案例下标(仅位置信息,无内容)
    dev_episodes: int


def build_clab_split(world: WorldData, *, dev_episodes: int) -> ClabSplit:
    """前 dev_episodes 个 episode 为 dev 窗,其余为评估窗(本函数不返回)。

    只纳入「episode < dev_episodes 且结局在 dev 窗末已可见」的案例 ——
    迭代视角下这些信息在 dev 窗结束时刻全部合法可得(LAG 红线)。
    """
    n_episodes = int(world.casebook.episode.max()) + 1
    if not 1 <= dev_episodes < n_episodes:
        raise ValueError(
            f"dev_episodes={dev_episodes} 超出合法范围 [1, {n_episodes})"
        )
    ep = world.casebook.episode
    cutoff = dev_episodes - 1
    mask = (ep < dev_episodes) & (world.ledger.visible_episode <= cutoff)
    idx = np.nonzero(mask)[0]
    df = pd.DataFrame(world.casebook.observables[idx], columns=list(CLAB_FIELDS))
    df.insert(0, "episode", pd.array([f"{e:03d}" for e in ep[idx]]))
    df["outcome"] = world.ledger.outcome[idx]
    return ClabSplit(dev_df=df, dev_case_idx=idx, dev_episodes=dev_episodes)


def clab_base_features(df: pd.DataFrame) -> tuple[np.ndarray, list[str]]:
    """基础特征库:8 个 L0 原字段 + 内置 L1 交互(训练/打分唯一口径)。"""
    registry = build_default_registry(CLAB_FIELDS)
    out = registry.compute(df)
    return out.to_numpy(dtype=np.float64), list(out.columns)


# ---------------------------------------------------------------------------
# 自动候选云端(暴力基线臂)
# ---------------------------------------------------------------------------


class ClabAutoCloud:
    """程序化候选生成器:枚举 + 先验 IV 粗排,按轮吐出 feature_proposal 契约。

    先验 IV 用 dev 帧标签粗算,只为排序(把探索预算优先给信号强的方向);
    准入裁决仍由闭环验证器(沙箱 + §8.4 门槛)独立完成 —— 粗排不过线,
    验证漏斗才是闸门。
    """

    provider_name = "clab-auto"
    model_name = "enumerator-v1"

    def __init__(
        self,
        df: pd.DataFrame,
        labels: np.ndarray,
        *,
        quantiles: tuple[float, ...] = (0.6, 0.7, 0.8, 0.9),
        conj_quantiles: tuple[float, ...] = (0.7, 0.8),
        prior_sample: int = 20_000,
        seed: int = 20260727,
    ) -> None:
        del seed  # 枚举与排序均确定;参数留位以保持实验配置显式
        candidates = self.enumerate_candidates(
            df, quantiles=quantiles, conj_quantiles=conj_quantiles
        )
        y = np.asarray(labels)
        stride = max(1, len(df) // prior_sample)
        sub_df = df.iloc[::stride]
        sub_y = y[::stride]
        ranked: list[dict[str, Any]] = []
        for c in candidates:
            try:
                values = compile_l2_expression(c["expression"])(sub_df).to_numpy()
            except Exception:
                continue  # 进程内都跑不了的候选不配占用验证预算
            ranked.append({**c, "prior_iv": information_value(values, sub_y)})
        ranked.sort(key=lambda c: -c["prior_iv"])
        self.candidates: list[dict[str, Any]] = ranked
        self._cursor = 0
        self.seen_contexts: list[dict[str, Any]] = []

    @staticmethod
    def enumerate_candidates(
        df: pd.DataFrame,
        *,
        quantiles: tuple[float, ...] = (0.6, 0.7, 0.8, 0.9),
        conj_quantiles: tuple[float, ...] = (0.7, 0.8),
    ) -> list[dict[str, str]]:
        """枚举候选特征(纯函数,不用标签):

        - single:      (df.f > q) / (df.f < q),q 取分位数;
        - product:     df.a * df.b;
        - ratio:       df.a / (df.b + 1e-06)(两个方向);
        - conjunction: ((df.a > qa) & (df.b > qb)) 四种方向 × 分位数组合。
        """
        fields = list(df.columns)
        alias = {f: f"f{i}" for i, f in enumerate(fields)}
        qv = {f: {q: float(df[f].quantile(q)) for q in quantiles} for f in fields}
        out: list[dict[str, str]] = []
        seen: set[str] = set()

        def emit(kind: str, name: str, expression: str, rationale: str) -> None:
            if expression in seen:
                return  # 低基数字段分位数撞阈值会产生重复式,去重
            seen.add(expression)
            out.append({"kind": kind, "name": name,
                        "expression": expression, "rationale": rationale})

        for f in fields:
            a = alias[f]
            for q in quantiles:
                t = f"{qv[f][q]:.6f}"
                pct = int(round(q * 100))
                emit("single", f"sg_{a}_gt{pct}", f"(df.{f} > {t})",
                     f"{f} 处于高尾(P{pct} 分位 {t} 以上)的申请人风险有差异")
                emit("single", f"sg_{a}_lt{pct}", f"(df.{f} < {t})",
                     f"{f} 处于低尾(P{pct} 分位 {t} 以下)的申请人风险有差异")

        for i, fa in enumerate(fields):
            for fb in fields[i + 1:]:
                aa, ab = alias[fa], alias[fb]
                emit("product", f"pr_{aa}_{ab}", f"df.{fa} * df.{fb}",
                     f"{fa} 与 {fb} 的乘积交互:两者叠加放大风险")
                emit("ratio", f"rt_{aa}_{ab}", f"df.{fa} / (df.{fb} + 1e-06)",
                     f"{fa} 相对 {fb} 的比值:相对水平比绝对量更刻画风险")
                emit("ratio", f"rt_{ab}_{aa}", f"df.{fb} / (df.{fa} + 1e-06)",
                     f"{fb} 相对 {fa} 的比值:相对水平比绝对量更刻画风险")

        cqv = {f: {q: float(df[f].quantile(q)) for q in conj_quantiles}
               for f in fields}
        for i, fa in enumerate(fields):
            for fb in fields[i + 1:]:
                aa, ab = alias[fa], alias[fb]
                for qa in conj_quantiles:
                    for qb in conj_quantiles:
                        ta = f"{cqv[fa][qa]:.6f}"
                        tb = f"{cqv[fb][qb]:.6f}"
                        pa, pb = int(round(qa * 100)), int(round(qb * 100))
                        for op_a, op_b, tag in (
                            (">", ">", "gg"), (">", "<", "gl"),
                            ("<", ">", "lg"), ("<", "<", "ll"),
                        ):
                            emit(
                                "conjunction",
                                f"cj_{aa}_{tag}_{ab}_{pa}{pb}",
                                f"((df.{fa} {op_a} {ta}) & (df.{fb} {op_b} {tb}))",
                                f"{fa} {op_a} P{pa} 且 {fb} {op_b} P{pb} "
                                "的合取人群:双尾叠加是典型风险/优质segment",
                            )
        return out

    def execute(self, task: TaskPackage) -> TaskResult:
        """按轮推进:每轮吐出 constraints.max_features 个未提过的候选。"""
        self.seen_contexts.append(dict(task.context))
        m = int(task.constraints.get("max_features", 20))
        batch = self.candidates[self._cursor:self._cursor + m]
        self._cursor += len(batch)
        features = [
            {"name": c["name"], "expression": c["expression"],
             "rationale": c["rationale"]}
            for c in batch
        ]
        return TaskResult(
            task_id=task.task_id, task_type=task.task_type,
            content={"features": features},
            provenance=Provenance(
                provider=self.provider_name, model=self.model_name,
                model_version="v1",
                timestamp=datetime.now(timezone.utc).isoformat(),
                prompt_hash="", cost_tokens=0,
            ),
        )
