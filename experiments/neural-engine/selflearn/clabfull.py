"""CLAB-full 合成世界的自学习闭环适配层 + 三方云端(机制一 full 版)。

职责(与 selflearn/clab.py 同构,模态扩展):
  1. FullWorld → 闭环可消费 dev 帧:8 数值观测 + 3 类别 + 3 频次 + 10 seq
     统计量;时间红线与 lite 同语义(帧内标签全部在 dev 窗末已成熟);
  2. 类别高基处理:池内索引 int 列(表达式可 `df.region.isin([...])` 引用
     值集,过 AST 白名单)+ 帧内频次编码列 `<cat>_freq`(决策/迭代时刻可得,
     不用标签)。device_id 池 50k,不做 one-hot、不做序号阈值枚举(Zipf 序数
     无意义),值集标志由枚举臂按先验坏账率粗排选定;
  3. 三方云端(同一 feature_proposal 契约):
     - ClabFullAutoCloud:暴力枚举臂,新增 catset(类别值集标志)与 seq 阈值
       模态,先验 IV 粗排,top-M/轮;
     - LocalLLMCloud:本地小模型臂(Qwen3-0.6B),methodology.md 全文注入
       system 段,指路信号作为案例材料进 user 段;JSON 解析容错(复用
       scribe 的 extract_json),失败重试一次降级空;
     - build_agent_bridge_cloud:我在环臂接口(本实验不跑,主 agent 接入)。

信息红线:本模块只读 CaseBook / OutcomeLedger;规则池内容对闭环不可见,
发现力判卷在 eval.clabfull_comparison(测量侧)完成。
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

import numpy as np
import pandas as pd

from cloud.contracts import Provenance, TaskPackage, TaskResult, prompt_hash
from scoring.features import build_default_registry
from scribe.induce import extract_json
from selflearn.clab import direction_key
from synth.config import FACTORS
from synthfull.config import CATEGORICALS, SEQ_STAT_NAMES
from synthfull.sequences import seq_stats
from synthfull.world import WorldData
from verify.metrics import information_value

from .features import compile_l2_expression

GenerateFn = Callable[[str], str]

# ---------------------------------------------------------------------------
# 帧列定义
# ---------------------------------------------------------------------------

OBS_FIELDS: tuple[str, ...] = tuple(f.observable for f in FACTORS)
CAT_FIELDS: tuple[str, ...] = tuple(s.name for s in CATEGORICALS)
FREQ_FIELDS: tuple[str, ...] = tuple(f"{c}_freq" for c in CAT_FIELDS)
# seq 统计量进帧统一 seq_ 前缀(SEQ_STAT_NAMES 里 seq_len 本身已带前缀)
SEQ_FIELDS: tuple[str, ...] = tuple(
    n if n.startswith("seq_") else f"seq_{n}" for n in SEQ_STAT_NAMES
)
ALL_FEATURE_COLUMNS: tuple[str, ...] = (
    OBS_FIELDS + CAT_FIELDS + FREQ_FIELDS + SEQ_FIELDS
)
# 数值阈值/交互枚举字段:池内索引列除外(Zipf 序数无数值意义)
ENUM_NUMERIC_FIELDS: tuple[str, ...] = OBS_FIELDS + FREQ_FIELDS + SEQ_FIELDS

# 字段槽陈述(策略记忆初始化 + 小模型字段字典;只描述字段含义,不含规律)
CLABFULL_FIELD_STATEMENTS: dict[str, str] = {
    "income_volatility_obs": "月收入波动率代理(0~1,越高收入越不稳定)",
    "debt_to_income_obs": "存量债务月供 / 月收入(带噪观测)",
    "credit_history_years_reported": "自报信用历史年限",
    "delinquencies_reported": "自报历史逾期次数(存在少报)",
    "months_employed": "现单位在职月数",
    "savings_months_obs": "流动储蓄可覆盖月数(带噪观测)",
    "requested_loan_to_income": "本次申请金额 / 年收入",
    "platform_loans_disclosed": "自报有未结清贷款的平台数(存在少报)",
    "device_id": "设备指纹(高基类别,池内索引 int;用 isin([...]) 构造值集标志)",
    "phone_prefix": "手机号段(类别,池内索引 int;用 isin([...]) 构造值集标志)",
    "region": "地域(类别,池内索引 int;用 isin([...]) 构造值集标志)",
    "device_id_freq": "该设备在帧内的申请次数(聚集度,越高越疑似批量)",
    "phone_prefix_freq": "该号段在帧内的申请次数(聚集度)",
    "region_freq": "该地域在帧内的申请次数(聚集度)",
    "seq_len": "行为序列长度(事件数;秒填≈批量机审)",
    "seq_paste_count": "粘贴次数(包装/模板填写信号)",
    "seq_backspace_count": "回退删除次数(犹豫/试探信号)",
    "seq_idle_count": "长时间停顿次数(犹豫信号)",
    "seq_edit_count": "字段修改次数(反复修改≈试探)",
    "seq_focus_count": "字段聚焦次数",
    "seq_total_duration": "申请总时长(秒;极短≈机审)",
    "seq_mean_duration": "平均单步时长(秒)",
    "seq_max_duration": "最长单步时长(秒;长停顿)",
    "seq_paste_ratio": "粘贴事件占比",
}


# ---------------------------------------------------------------------------
# dev/eval 切分(时间红线与 lite 同语义)
# ---------------------------------------------------------------------------


@dataclass
class ClabFullSplit:
    """dev 窗切分结果:dev 帧 + 帧内行对应的案例下标(判卷对齐用)。"""

    dev_df: pd.DataFrame  # episode + outcome + ALL_FEATURE_COLUMNS
    dev_case_idx: np.ndarray
    dev_episodes: int


def build_clabfull_split(data: WorldData, *, dev_episodes: int) -> ClabFullSplit:
    """前 dev_episodes 个 episode 为 dev 窗;只纳入结局在 dev 窗末已可见的
    案例(LAG 红线,与 selflearn.clab.build_clab_split 同语义)。

    频次编码在 dev 窗内计算(value_counts,无标签);seq 统计量由
    synthfull.seq_stats 从决策时刻序列特征算出(纯函数,与世界同源)。
    """
    n_episodes = int(data.casebook.episode.max()) + 1
    if not 1 <= dev_episodes < n_episodes:
        raise ValueError(
            f"dev_episodes={dev_episodes} 超出合法范围 [1, {n_episodes})"
        )
    ep = data.casebook.episode
    cutoff = dev_episodes - 1
    mask = (ep < dev_episodes) & (data.ledger.visible_episode <= cutoff)
    idx = np.nonzero(mask)[0]

    cb = data.casebook
    df = pd.DataFrame(cb.observables[idx], columns=list(OBS_FIELDS))
    for c in CAT_FIELDS:
        df[c] = cb.categorical(c)[idx].astype(np.int64)
    stats = seq_stats(cb.seq_events[idx], cb.seq_durations[idx],
                      cb.seq_len[idx])
    for j, name in enumerate(SEQ_FIELDS):
        df[name] = stats[:, j]
    # 频次编码:dev 帧内 value_counts(迭代时刻合法可得,不用标签)
    for c, f in zip(CAT_FIELDS, FREQ_FIELDS):
        vc = df[c].value_counts()
        df[f] = df[c].map(vc).astype(np.float64)
    # 列序:episode + 特征列 + outcome
    df.insert(0, "episode", pd.array([f"{e:03d}" for e in ep[idx]]))
    df["outcome"] = data.ledger.outcome[idx]
    df = df[["episode", *ALL_FEATURE_COLUMNS, "outcome"]]
    return ClabFullSplit(dev_df=df, dev_case_idx=idx, dev_episodes=dev_episodes)


def clabfull_base_features(df: pd.DataFrame) -> tuple[np.ndarray, list[str]]:
    """基础特征库:8 obs 的 L0+内置 L1(复用 build_default_registry)
    + 类别池内索引/频次/seq 统计量的 L0 直出(训练/打分唯一口径)。"""
    registry = build_default_registry(OBS_FIELDS)
    base = registry.compute(df)
    extra = df[list(CAT_FIELDS + FREQ_FIELDS + SEQ_FIELDS)].astype(np.float64)
    out = pd.concat([base.reset_index(drop=True),
                     extra.reset_index(drop=True)], axis=1)
    return out.to_numpy(dtype=np.float64), list(out.columns)


# ---------------------------------------------------------------------------
# 枚举臂(暴力基线 full 版)
# ---------------------------------------------------------------------------


def direction_key_full(kind: str, parts: tuple[tuple[str, str], ...]) -> str:
    """full 版方向键:新增 catset(类别值集标志,按字段+方向归一)。"""
    if kind == "catset":
        field, sign = parts[0]
        return f"catset:{field}:{sign}"
    return direction_key(kind, parts)


class ClabFullAutoCloud:
    """程序化候选生成器 full 版:lite 四形态 + catset/seq 新模态。

    catset 值集由先验坏账率粗排选定(标签只用于排序,准入裁决仍是
    §8.4 验证漏斗);seq 统计量经 SEQ_FIELDS 列进 single 阈值枚举。
    """

    provider_name = "clabfull-auto"
    model_name = "enumerator-v2"

    def __init__(
        self,
        df: pd.DataFrame,
        labels: np.ndarray,
        *,
        quantiles: tuple[float, ...] = (0.6, 0.7, 0.8, 0.9),
        conj_quantiles: tuple[float, ...] = (0.7, 0.8),
        prior_sample: int = 20_000,
        cat_min_count: int = 30,
        cat_top_k: int = 3,
    ) -> None:
        candidates = self.enumerate_candidates(
            df, quantiles=quantiles, conj_quantiles=conj_quantiles
        )
        y = np.asarray(labels)
        stride = max(1, len(df) // prior_sample)
        sub_df = df.iloc[::stride]
        sub_y = y[::stride]
        candidates += self._catset_candidates(
            sub_df, sub_y, min_count=cat_min_count, top_k=cat_top_k
        )
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

    # ---- 候选枚举(纯函数,不用标签)--------------------------------------

    @staticmethod
    def enumerate_candidates(
        df: pd.DataFrame,
        *,
        quantiles: tuple[float, ...] = (0.6, 0.7, 0.8, 0.9),
        conj_quantiles: tuple[float, ...] = (0.7, 0.8),
    ) -> list[dict[str, str]]:
        """数值模态枚举(与 lite 同构,字段集换成 full 数值列):

        - single:      (df.f > q) / (df.f < q)(含全部 seq_* 与频次列;
                       类别池内索引列除外 —— Zipf 序数无数值意义);
        - product:     df.a * df.b;
        - ratio:       df.a / (df.b + 1e-06)(两个方向);
        - conjunction: ((df.a > qa) & (df.b > qb)) 四种方向 × 分位数组合。
        """
        fields = [f for f in df.columns if f in ENUM_NUMERIC_FIELDS]
        alias = {f: f"f{i}" for i, f in enumerate(fields)}
        qv = {f: {q: float(df[f].quantile(q)) for q in quantiles} for f in fields}
        out: list[dict[str, str]] = []
        seen: set[str] = set()

        def emit(kind: str, name: str, expression: str, rationale: str,
                 parts: tuple[tuple[str, str], ...]) -> None:
            if expression in seen:
                return
            seen.add(expression)
            out.append({"kind": kind, "name": name,
                        "expression": expression, "rationale": rationale,
                        "direction": direction_key_full(kind, parts)})

        for f in fields:
            a = alias[f]
            for q in quantiles:
                t = f"{qv[f][q]:.6f}"
                pct = int(round(q * 100))
                emit("single", f"sg_{a}_gt{pct}", f"(df.{f} > {t})",
                     f"{f} 处于高尾(P{pct} 分位 {t} 以上)的申请人风险有差异",
                     ((f, ">"),))
                emit("single", f"sg_{a}_lt{pct}", f"(df.{f} < {t})",
                     f"{f} 处于低尾(P{pct} 分位 {t} 以下)的申请人风险有差异",
                     ((f, "<"),))

        for i, fa in enumerate(fields):
            for fb in fields[i + 1:]:
                aa, ab = alias[fa], alias[fb]
                emit("product", f"pr_{aa}_{ab}", f"df.{fa} * df.{fb}",
                     f"{fa} 与 {fb} 的乘积交互:两者叠加放大风险",
                     ((fa, "*"), (fb, "*")))
                emit("ratio", f"rt_{aa}_{ab}", f"df.{fa} / (df.{fb} + 1e-06)",
                     f"{fa} 相对 {fb} 的比值:相对水平比绝对量更刻画风险",
                     ((fa, "/"), (fb, "/")))
                emit("ratio", f"rt_{ab}_{aa}", f"df.{fb} / (df.{fa} + 1e-06)",
                     f"{fb} 相对 {fa} 的比值:相对水平比绝对量更刻画风险",
                     ((fb, "/"), (fa, "/")))

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
                                ((fa, op_a), (fb, op_b)),
                            )
        return out

    # ---- 类别值集标志(标签只用于粗排)-----------------------------------

    @staticmethod
    def cat_value_sets(
        df: pd.DataFrame,
        labels: np.ndarray,
        *,
        fields: tuple[str, ...] = CAT_FIELDS,
        min_count: int = 30,
        top_k: int = 3,
    ) -> dict[str, dict[str, list[int]]]:
        """每类别字段的粗排值集:风险方向 top_k + 保护方向 top_1。

        平滑坏账率 (bad+0.5)/(count+1);样本数 < min_count 的值不参与
        (高基长尾上的单点坏账率是噪声)。返回值是池内索引 int 列表。
        """
        y = np.asarray(labels)
        out: dict[str, dict[str, list[int]]] = {}
        for c in fields:
            if c not in df.columns:
                continue
            s = df[c]
            counts = s.value_counts()
            eligible = counts[counts >= min_count]
            if len(eligible) == 0:
                continue
            rate: dict[int, float] = {}
            for v, cnt in eligible.items():
                m = (s == v).to_numpy()
                rate[int(v)] = float((y[m].sum() + 0.5) / (m.sum() + 1.0))
            risk = sorted(rate, key=lambda v: (-rate[v], v))[:top_k]
            prot = sorted(rate, key=lambda v: (rate[v], v))[:1]
            out[c] = {"risk": [int(v) for v in risk],
                      "protective": [int(v) for v in prot]}
        return out

    @classmethod
    def _catset_candidates(
        cls,
        sub_df: pd.DataFrame,
        sub_y: np.ndarray,
        *,
        min_count: int,
        top_k: int,
    ) -> list[dict[str, str]]:
        """catset 候选:风险值集前缀(1..top_k)+ 保护单值,isin 表达式。"""
        sets = cls.cat_value_sets(sub_df, sub_y, min_count=min_count,
                                  top_k=top_k)
        out: list[dict[str, str]] = []
        seen_expr: set[str] = set()

        def emit(name: str, expression: str, rationale: str,
                 sign: str, field: str) -> None:
            if expression in seen_expr:
                return  # 唯一合格值时 risk1 与 prot1 会撞同一值集
            seen_expr.add(expression)
            out.append({
                "kind": "catset", "name": name, "expression": expression,
                "rationale": rationale,
                "direction": direction_key_full("catset", ((field, sign),)),
            })

        for field, d in sets.items():
            for size, vals in enumerate(
                (d["risk"][:k] for k in range(1, top_k + 1)), start=1
            ):
                if len(vals) < size:
                    continue
                emit(
                    f"cs_{field}_risk{size}",
                    f"df.{field}.isin({vals}).astype(float)",
                    f"{field} 值集 {vals} 聚集坏账(先验坏账率粗排 top"
                    f"{size}),同设备/号段/地域批量申请是典型欺诈信号",
                    "risk", field,
                )
            for v in d["protective"]:
                emit(
                    f"cs_{field}_prot1",
                    f"df.{field}.isin([{v}]).astype(float)",
                    f"{field} 值 {v} 的申请人异常地好(保护方向,"
                    "验证器有对称 lift 判据)",
                    "prot", field,
                )
        return out

    # ---- 契约执行 ---------------------------------------------------------

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


# ---------------------------------------------------------------------------
# 小模型臂(本地 LLM + 方法论手册注入)
# ---------------------------------------------------------------------------


def load_methodology(path: str | Path | None = None) -> str:
    """挖掘方法论手册全文(默认 selflearn/methodology.md)。"""
    p = Path(path) if path is not None else Path(__file__).parent / "methodology.md"
    return p.read_text(encoding="utf-8")


def _chat_wrap(system: str, user: str) -> str:
    """Qwen3 chat 包装(system + user + 空 think 块,scribe._chat_wrap 的
    双段扩展):空 <think></think> 预填强制非思考模式,续写即 JSON 答案。"""
    return (
        f"<|im_start|>system\n{system}<|im_end|>\n"
        f"<|im_start|>user\n{user}<|im_end|>\n"
        "<|im_start|>assistant\n<think>\n\n</think>\n"
    )


class LocalLLMCloud:
    """本地小模型云端:methodology.md 全文 + 指路信号 → feature_proposal。

    手册作为 system 段全文注入(方法论槽的轻量实现;槽位表沉淀留后续),
    user 段 = 字段字典 + 指路信号(残余/重要性/疑难画像,截断控长)+
    死路 + 现有特征 + JSON 输出契约。产出永远是候选:extract_json 解析,
    失败重试一次(错误回注),再失败降级为空提案 —— 绝不向闭环抛异常。
    """

    provider_name = "local-llm"

    def __init__(
        self,
        generate_fn: GenerateFn,
        *,
        methodology: str | None = None,
        field_statements: dict[str, str] | None = None,
        max_proposals: int = 3,
        model_name: str = "qwen3-0.6b-local",
    ) -> None:
        self._generate = generate_fn
        self.methodology = methodology if methodology is not None else load_methodology()
        self.field_statements = field_statements or CLABFULL_FIELD_STATEMENTS
        self.max_proposals = max_proposals
        self.model_name = model_name
        self.seen_contexts: list[dict[str, Any]] = []
        self.last_prompt: str | None = None
        self.stats: dict[str, Any] = {
            "calls": 0, "retries": 0, "parse_failures": 0, "llm_errors": 0,
            "empty_results": 0, "latency_s": [], "prompt_chars": [],
        }

    @classmethod
    def from_llm(
        cls,
        llm: Any,  # llm.LocalLLM;typed loosely to keep import optional
        *,
        max_new_tokens: int = 320,
        temperature: float = 0.0,
        top_p: float = 1.0,
        seed: int | None = None,
        **kwargs: Any,
    ) -> "LocalLLMCloud":
        """包装 LocalLLM.generate 为出题 generate_fn(chat 包装在 execute
        内完成,generate_fn 收到的就是包好的完整 prompt)。

        temperature > 0 且给了 seed 时,每次调用前 torch.manual_seed
        (seed + 调用序号)—— 采样有多样性(贪心解码在「同 context 重复
        出题」上会死循环),同时整轮实验可复现。
        """
        calls = {"n": 0}

        def gen(prompt: str) -> str:
            if seed is not None and temperature > 0:
                import torch

                torch.manual_seed(seed + calls["n"])
            calls["n"] += 1
            return llm.generate(
                prompt, max_new_tokens=max_new_tokens,
                temperature=temperature, top_p=top_p,
            )

        return cls(gen, **kwargs)

    # ---- prompt 构造 ------------------------------------------------------

    def _build_messages(
        self, task: TaskPackage, cap: int, error: str | None
    ) -> tuple[str, str]:
        ctx = task.context
        field_lines = "\n".join(
            f"- {name}: {stmt}" for name, stmt in self.field_statements.items()
        )
        guide: list[str] = []
        profiles = ctx.get("case_profiles") or []
        for p in profiles[:1]:
            if isinstance(p, dict) and p.get("n"):
                guide.append(
                    f"疑难画像: {p.get('n')} 例解释不了的坏账"
                    f"(占 dev {p.get('share_of_dev', 0):.2%})"
                )
        imp = ctx.get("importance_top") or []
        if imp:
            guide.append("GBDT 重要性 top: " + ", ".join(
                f"{t['feature']}({t['importance']:.0f})" for t in imp[:5]
            ))
        residual = ctx.get("residual_signals") or {}
        for e in (residual.get("numeric") or [])[:5]:
            guide.append(
                f"残余信号: {e['feature']} 漏网坏账"
                f"{'偏高' if e.get('cohens_d', 0) > 0 else '偏低'}"
                f"(cohens_d={e.get('cohens_d', 0):.2f})"
            )
        guide_text = "\n".join(guide) if guide else "(本轮无指路信号)"
        dead = ctx.get("dead_ends") or []
        dead_text = "\n".join(f"- {d}" for d in dead[:10]) or "(空)"
        existing = list(ctx.get("existing_features") or [])
        exist_text = (
            f"共 {len(existing)} 个,前若干个: " + ", ".join(existing[:15])
            if existing else "(空)"
        )
        user = f"""# 字段字典(表达式可用的 df 列)
{field_lines}

# 指路信号(案例材料)
{guide_text}

# 死路清单(禁止重复)
{dead_text}

# 现有特征(避免重复)
{exist_text}

# 任务
按手册提出最多 {cap} 个特征,尽量提满 {cap} 个不同方向。表达式是
pandas over df 的单行式(如 (df.seq_paste_count > 3)、
df.region.isin([2, 5]).astype(float)、df.a / (df.b + 1e-06)),
只能用上面字段字典里的列。
铁律一:列引用必须带 df. 前缀 —— 写 (df.seq_paste_count > 3),
不要写 (seq_paste_count > 3)。
铁律二:name 是新特征的标识符,禁止与字段字典中的列同名(列名本身
已是基础特征,同名必被拒);用 llm_ 前缀 + 语义,如 llm_paste_hi、
llm_region_risk_set。
rationale 三段:机制 / 证据(指路信号哪条) / 与死路和现有特征的差异。
只输出 JSON,不要输出任何其他文字:
{{"features": [{{"name": "...", "expression": "...", "rationale": "..."}}]}}"""
        if error is not None:
            user += f"\n\n上次输出无法解析:{error}\n请重新输出,只输出上面要求的 JSON。"
        return self.methodology, user

    @staticmethod
    def _validate(obj: dict[str, Any], cap: int) -> list[dict[str, str]]:
        features = obj.get("features")
        if not isinstance(features, list):
            raise ValueError('"features" missing or not a list')
        out: list[dict[str, str]] = []
        for item in features:
            if not isinstance(item, dict):
                continue
            name, expr, rat = (item.get(k) for k in ("name", "expression", "rationale"))
            if not (isinstance(name, str) and name.strip()
                    and isinstance(expr, str) and expr.strip()
                    and isinstance(rat, str) and rat.strip()):
                continue
            out.append({"name": name.strip(), "expression": expr.strip(),
                        "rationale": rat.strip()})
        if not out:
            raise ValueError("no valid feature items in payload")
        return out[:cap]

    # ---- 契约执行 ----------------------------------------------------------

    def execute(self, task: TaskPackage) -> TaskResult:
        self.seen_contexts.append(dict(task.context))
        max_f = int(task.constraints.get("max_features", self.max_proposals))
        cap = max(1, min(max_f, self.max_proposals))
        error: str | None = None
        features: list[dict[str, str]] = []
        last_prompt = ""
        for attempt in range(2):
            system, user = self._build_messages(task, cap, error)
            prompt = _chat_wrap(system, user)
            last_prompt = prompt
            t0 = time.monotonic()
            try:
                text = self._generate(prompt)
            except Exception:
                self.stats["llm_errors"] += 1
                break  # 生成器坏了重试不会自愈,直接降级
            finally:
                self.stats["latency_s"].append(time.monotonic() - t0)
            self.stats["calls"] += 1
            self.stats["prompt_chars"].append(len(prompt))
            if attempt:
                self.stats["retries"] += 1
            try:
                features = self._validate(extract_json(text), cap)
                error = None
                break
            except ValueError as exc:
                error = str(exc)
        if error is not None:
            self.stats["parse_failures"] += 1
        if not features:
            self.stats["empty_results"] += 1
        self.last_prompt = last_prompt
        return TaskResult(
            task_id=task.task_id, task_type=task.task_type,
            content={"features": features},
            provenance=Provenance(
                provider=self.provider_name, model=self.model_name,
                model_version="v1",
                timestamp=datetime.now(timezone.utc).isoformat(),
                prompt_hash=prompt_hash(last_prompt), cost_tokens=0,
            ),
        )


# ---------------------------------------------------------------------------
# 我在环臂(接口留位;本实验不跑,由主 agent 接入)
# ---------------------------------------------------------------------------


def build_agent_bridge_cloud(
    bridge_dir: str | Path | None = None,
    **kwargs: Any,
) -> Any:
    """AgentBridge 在环云端(文件桥:outbox 出题,inbox 收提案)。

    与另两臂同一 feature_proposal 契约;用法见 cloud/bridge/README.md。
    """
    from cloud.agent_bridge import AgentBridgeProvider

    return AgentBridgeProvider(bridge_dir=bridge_dir, **kwargs)
