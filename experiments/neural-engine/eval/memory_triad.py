"""Session PM 作为「可写参数记忆」的三项性质测量(论文主线:运行时参数记忆)。

测 1 写入-召回保真度(run_write_recall):
  32 条信贷经验陈述(「模式描述 → 风险结论」,结论来自 4 项候选集,按种子
  随机指派——映射对基座不可推断,测的是记忆通道本身的保真度)。前 8 条经
  reward-weighted TTT 写入 Session PM(LoRA r=8,只挂 q/v),后 8 条作干扰项。
  三臂对比:基座(无 PM)/ 随机初始化 PM(未写入)/ TTT 写入 PM。
  判定形式:多项选择判别——给模式前缀,比较 4 个候选结论的 mean token
  logprob,argmax 命中金标即召回(可判定、无采样噪声)。

测 2 连续写入干扰度(run_forgetting):
  K=4 批 × 8 条(4 个主题,模拟 regime 更替)顺序写入同一 Session PM;
  每批写完测所有历史批次召回率,得 批次×写后时间点 遗忘矩阵。
  对照:基座(无 PM)与「全部经验拼进 prompt」的上下文携带臂(只测最终
  时间点)——上下文携带召回高但每查询付 ~700 token 上下文本;参数写入
  零上下文本但有遗忘。必要性/代价同图呈现。

测 3 regime 适应速度(run_regime_adaptation):
  CLAB 世界流式闭环(dev 40 期 / 10 轮,frontier 每轮推进 4 期),regime
  切换点取 world.regimes 的 ground truth。三臂:
    A: Session PM 经 TTT 在线更新(reward = 当轮 avg_iv)
    B: 无 PM(仅基座,adapter disable)
    C: 检索式外部记忆(最近 W=4 期各字段 IV 检索注入 prompt)
  指标:切换点后 avg_iv 恢复到切换前水平 90% 所需轮数。

真模型 Qwen3-0.6B(CPU,fp32)。运行(cwd = experiments/neural-engine):
  uv run python -m eval.memory_triad --test all
"""

from __future__ import annotations

import argparse
import json
import math
import os
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
import torch
import torch.nn.functional as F

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

from selflearn.ttt import TTTConfig, ttt_step

# ---------------------------------------------------------------------------
# 经验库:4 主题 × 8 条模式;结论由种子随机指派(对基座不可推断)
# ---------------------------------------------------------------------------

CONCLUSIONS: tuple[str, ...] = ("违约倾向高", "违约倾向低", "资质中性待观察", "需人工复核")

# 4 个主题 ≈ 4 个 regime;每个主题 8 条模式描述。
PATTERN_BANK: tuple[tuple[str, ...], ...] = (
    (  # 主题 0:共债与查询
        "多头借贷≥3 且查询密集",
        "近30天查询>6 且信用卡刷爆",
        "共债平台≥5 且收入平稳",
        "查询环比翻倍 且授信使用率>80%",
        "网贷占比高 且近7天查询>3",
        "首贷且共债平台=2",
        "多头借贷=2 且查询稀疏",
        "无共债 且近90天零查询",
    ),
    (  # 主题 1:收入与负债
        "收入波动大 且负债比>60%",
        "月收入>2万 且负债比<20%",
        "收入稳定 但月供占收入70%",
        "自由职业 且流水季节性明显",
        "工资打卡 且社保连续24月",
        "负债比40% 且收入缓慢增长",
        "收入骤降30% 且存款不足1月开销",
        "公积金高基数 且负债比<30%",
    ),
    (  # 主题 2:逾期与信用历史
        "历史逾期1次且已结清超2年",
        "当前逾期 且金额<500",
        "近2年逾期≥3次",
        "信用历史<6个月 且无逾期",
        "逾期已结清 且近12月记录良好",
        "曾有呆账 但近24月正常",
        "信用历史10年 且零逾期",
        "展期记录1次 且后续履约正常",
    ),
    (  # 主题 3:经营稳定性(小微)
        "经营年限<1年 且流水下滑",
        "经营5年 且纳税连续",
        "行业下行 但流水稳定",
        "开店2年 且旺季流水占比高",
        "上下游集中 且账期拉长",
        "经营3年 且近6月流水上升",
        "一年内频繁变更经营地址",
        "纳税评级A 且流水平稳",
    ),
)


@dataclass(frozen=True)
class ExperienceItem:
    """一条信贷经验:模式描述 → 风险结论。"""

    pattern: str
    conclusion: str
    theme: int


def item_prompt(pattern: str) -> str:
    """经验陈述的查询前缀(写入与召回同一格式)。"""
    return f"信贷经验:{pattern} →"


def build_experience_bank(seed: int = 20260805) -> list[list[ExperienceItem]]:
    """4 批 × 8 条;结论在每批内按种子打散,4 候选各出现 2 次(组内均衡)。

    随机指派是有意的:若结论可从模式语义推断,基座就能答对,测不出
    「写入→召回」通道的保真度。
    """
    bank: list[list[ExperienceItem]] = []
    for theme, patterns in enumerate(PATTERN_BANK):
        rng = np.random.Generator(np.random.PCG64(seed + theme))
        idx = np.arange(len(CONCLUSIONS)).repeat(2)  # 每候选 2 次
        rng.shuffle(idx)
        bank.append([
            ExperienceItem(pattern=p, conclusion=CONCLUSIONS[int(i)], theme=theme)
            for p, i in zip(patterns, idx)
        ])
    return bank


# ---------------------------------------------------------------------------
# 通用原语:挂 PM / 随机化 / 清零 / 打分 / 写入
# ---------------------------------------------------------------------------


def attach_session_pm(model: Any, r: int = 8, lr: float = 3e-4):
    """挂 Session PM(peft LoRA,只 q/v)并返 (peft_model, optimizer)。

    不用 selflearn.ttt.setup_ttt_lora:它只返 optimizer、不返 PeftModel
    包装,而本实验需要 disable_adapter() 来在同一模型实例上跑「无 PM」
    对照臂。peft 的 get_peft_model 就地改造 base 模块树,llm._model 的
    generate 同样经过 LoRA。重复调用幂等(已挂则复用)。
    """
    from peft import LoraConfig, get_peft_model

    if hasattr(model, "peft_config") and model.peft_config:
        pm_model = model
    else:
        peft_cfg = LoraConfig(
            r=r, lora_alpha=r * 2, lora_dropout=0.0,
            target_modules=["q_proj", "v_proj"], task_type="CAUSAL_LM",
        )
        pm_model = get_peft_model(model, peft_cfg)
    trainable = [p for p in pm_model.parameters() if p.requires_grad]
    optimizer = torch.optim.AdamW(trainable, lr=lr)
    return pm_model, optimizer


def zero_pm(pm_model: Any) -> None:
    """Session PM 清零(LoRA B 归 0 → PM 对输出无影响)。"""
    with torch.no_grad():
        for name, p in pm_model.named_parameters():
            if "lora_B" in name:
                p.zero_()


def randomize_pm(pm_model: Any, scale: float = 0.02, seed: int = 0) -> None:
    """随机初始化 PM(lora_B ~ N(0, scale)):「挂 PM 但未写入」对照臂。"""
    gen = torch.Generator().manual_seed(seed)
    with torch.no_grad():
        for name, p in pm_model.named_parameters():
            if "lora_B" in name:
                p.copy_(torch.randn(p.shape, generator=gen) * scale)


def completion_logprob(model: Any, tokenizer: Any, prompt: str,
                       completion: str) -> float:
    """completion 在 prompt 条件下的 mean token logprob(teacher forcing)。

    prompt/completion 分别编码再拼接(与 ttt._encode_pair 一致),保证
    各候选间 prompt 前缀逐 token 相同。
    """
    p_ids = tokenizer(prompt, return_tensors="pt")["input_ids"][0]
    c_ids = tokenizer(completion, return_tensors="pt")["input_ids"][0]
    ids = torch.cat([p_ids, c_ids]).unsqueeze(0)
    with torch.no_grad():
        logits = model(input_ids=ids).logits[0]  # [seq, vocab]
    logps = F.log_softmax(logits.float(), dim=-1)
    n = int(c_ids.shape[0])
    total = 0.0
    for k in range(n):
        pos = int(p_ids.shape[0]) + k - 1  # 预测第 k 个 completion token 的位置
        total += float(logps[pos, c_ids[k]])
    return total / max(n, 1)


def score_candidates(model: Any, tokenizer: Any, prompt: str,
                     candidates: tuple[str, ...] = CONCLUSIONS) -> list[float]:
    """各候选结论的 mean token logprob。"""
    return [completion_logprob(model, tokenizer, prompt, c) for c in candidates]


def recall_accuracy(model: Any, tokenizer: Any, items: list[ExperienceItem],
                    context: str | None = None) -> tuple[float, list[dict]]:
    """多项选择召回率:argmax(候选 logprob) == 金标 的比例。

    context: 非空则拼在查询前(上下文携带对照臂)。
    """
    correct = 0
    details: list[dict] = []
    for it in items:
        q = item_prompt(it.pattern)
        prompt = f"{context}\n{q}" if context else q
        scores = score_candidates(model, tokenizer, prompt)
        pick = CONCLUSIONS[int(np.argmax(scores))]
        hit = pick == it.conclusion
        correct += int(hit)
        details.append({
            "pattern": it.pattern, "gold": it.conclusion, "pick": pick,
            "hit": hit, "scores": [round(s, 4) for s in scores],
        })
    acc = correct / max(len(items), 1)
    return acc, details


def write_items(pm_model: Any, tokenizer: Any, optimizer: Any,
                items: list[ExperienceItem], *, reward: float,
                config: TTTConfig, epochs: int = 1) -> list[dict]:
    """把一批经验经 reward-weighted TTT 写入 Session PM。"""
    metrics: list[dict] = []
    for _ in range(epochs):
        for it in items:
            m = ttt_step(pm_model, tokenizer, optimizer,
                         prompt=item_prompt(it.pattern),
                         completion=it.conclusion,
                         reward=reward, config=config)
            metrics.append(m)
    return metrics


# ---------------------------------------------------------------------------
# 测 1:写入-召回保真度
# ---------------------------------------------------------------------------


def run_write_recall(llm: Any, ttt_config: TTTConfig, *,
                     n_write: int = 8, n_distractor: int = 8,
                     write_epochs: int = 3,
                     bank: list[list[ExperienceItem]] | None = None) -> dict:
    """三臂:基座 / 随机 PM(未写入)/ TTT 写入 PM;另测干扰项选择性。"""
    bank = bank or build_experience_bank()
    written = bank[0][:n_write]
    distractors = bank[1][:n_distractor]
    tok = llm._tokenizer

    pm, opt = attach_session_pm(llm._model, r=ttt_config.lora_r, lr=ttt_config.lr)
    zero_pm(pm)

    # 臂 1:基座(adapter 禁用,等价于无 PM)
    with pm.disable_adapter():
        base_written, base_w_det = recall_accuracy(pm, tok, written)
        base_distr, _ = recall_accuracy(pm, tok, distractors)

    # 臂 2:随机初始化 PM(挂 PM 但未写入)
    randomize_pm(pm, seed=ttt_config.lora_r)
    rand_written, _ = recall_accuracy(pm, tok, written)
    rand_distr, _ = recall_accuracy(pm, tok, distractors)

    # 臂 3:清零后 TTT 写入
    zero_pm(pm)
    write_metrics = write_items(pm, tok, opt, written, reward=1.0,
                                config=ttt_config, epochs=write_epochs)
    pm_written, pm_w_det = recall_accuracy(pm, tok, written)
    pm_distr, _ = recall_accuracy(pm, tok, distractors)

    return {
        "n_write": n_write, "n_distractor": n_distractor,
        "ttt": {"steps": ttt_config.steps, "lr": ttt_config.lr,
                "epochs": write_epochs,
                "final_sft_loss": round(write_metrics[-1]["sft_loss"], 4)},
        "arms": {
            "base": {"written_recall": round(base_written, 4),
                     "distractor_recall": round(base_distr, 4)},
            "random_pm": {"written_recall": round(rand_written, 4),
                          "distractor_recall": round(rand_distr, 4)},
            "written_pm": {"written_recall": round(pm_written, 4),
                           "distractor_recall": round(pm_distr, 4)},
        },
        "details_written_set": [
            {"pattern": d["pattern"], "gold": d["gold"],
             "base_pick": b["pick"], "written_pm_pick": d["pick"]}
            for d, b in zip(pm_w_det, base_w_det)
        ],
    }


# ---------------------------------------------------------------------------
# 测 2:连续写入干扰度(遗忘曲线)
# ---------------------------------------------------------------------------


def run_forgetting(llm: Any, ttt_config: TTTConfig, *,
                   write_epochs: int = 3,
                   bank: list[list[ExperienceItem]] | None = None) -> dict:
    """K 批顺序写入;每批写完测所有历史批次召回;对照基座与上下文携带。"""
    bank = bank or build_experience_bank()
    k = len(bank)
    tok = llm._tokenizer

    pm, opt = attach_session_pm(llm._model, r=ttt_config.lora_r, lr=ttt_config.lr)
    zero_pm(pm)

    # 对照 1:基座(无 PM),各批召回
    with pm.disable_adapter():
        base_recall = [round(recall_accuracy(pm, tok, b)[0], 4) for b in bank]

    # 参数写入臂:matrix[i][j] = 批 i 在写完批 j 后的召回率(i <= j)
    matrix: list[list[float | None]] = [[None] * k for _ in range(k)]
    for j in range(k):
        write_items(pm, tok, opt, bank[j], reward=1.0,
                    config=ttt_config, epochs=write_epochs)
        for i in range(j + 1):
            matrix[i][j] = round(recall_accuracy(pm, tok, bank[i])[0], 4)

    # 对照 2:上下文携带(全部经验拼进 prompt,只测最终时间点)
    context = "\n".join(item_prompt(it.pattern) + " " + it.conclusion
                        for b in bank for it in b)
    with pm.disable_adapter():
        ctx_recall = [round(recall_accuracy(pm, tok, b, context=context)[0], 4)
                      for b in bank]
    ctx_tokens = int(tok(context, return_tensors="pt")["input_ids"].shape[1])

    return {
        "k_batches": k, "batch_size": len(bank[0]),
        "ttt": {"steps": ttt_config.steps, "lr": ttt_config.lr,
                "epochs": write_epochs},
        "matrix_batch_x_timestep": matrix,  # None = 该批尚未写入
        "base_recall_per_batch": base_recall,
        "context_carry": {"recall_per_batch": ctx_recall,
                          "context_tokens": ctx_tokens},
        "param_write_context_tokens": 0,
    }


def plot_forgetting(report: dict, path: Path) -> None:
    """遗忘矩阵热图 + 各批召回随写入步的折线(含基座/上下文对照)。"""
    matrix = report["matrix_batch_x_timestep"]
    k = report["k_batches"]
    base = report["base_recall_per_batch"]
    ctx = report["context_carry"]["recall_per_batch"]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(13, 5))

    # 左:热图(None → NaN)
    grid = np.array([[np.nan if v is None else v for v in row] for row in matrix])
    im = ax1.imshow(grid, vmin=0, vmax=1, cmap="viridis", aspect="auto")
    for i in range(k):
        for j in range(k):
            if not np.isnan(grid[i, j]):
                ax1.text(j, i, f"{grid[i, j]:.2f}", ha="center", va="center",
                         fontsize=8,
                         color="white" if grid[i, j] < 0.6 else "black")
    ax1.set_xticks(range(k), [f"after write b{j}" for j in range(k)])
    ax1.set_yticks(range(k), [f"batch {i}" for i in range(k)])
    ax1.set_title("Forgetting matrix: recall(batch i | after writing batch j)")
    fig.colorbar(im, ax=ax1, label="recall@accuracy")

    # 右:折线
    xs = list(range(k))
    for i in range(k):
        ys = [matrix[i][j] if j >= i else np.nan for j in xs]
        ax2.plot(xs, ys, marker="o", ms=4, label=f"batch {i} (param PM)")
        ax2.axhline(base[i], color=f"C{i}", ls=":", lw=0.8)
        ax2.plot(xs[-1], ctx[i], marker="*", ms=10, color=f"C{i}")
    ax2.set_xticks(xs, [f"after b{j}" for j in xs])
    ax2.set_ylim(-0.05, 1.05)
    ax2.set_ylabel("recall@accuracy")
    ax2.set_title("Forgetting curves (dotted=base, star=context-carry)")
    ax2.legend(fontsize=7)
    fig.tight_layout()
    path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(path, dpi=150)
    plt.close(fig)


# ---------------------------------------------------------------------------
# 测 3:regime 适应速度(参数记忆 vs 检索记忆 vs 无记忆)
# ---------------------------------------------------------------------------


def recent_field_iv_text(dev_df: Any, frontier: int, w: int = 4) -> str:
    """臂 C 的检索记忆:最近 W 期各字段 IV,拼成 prompt 注入文本。"""
    from selflearn.clab import CLAB_FIELDS
    from verify.metrics import information_value

    recent = dev_df[dev_df["episode"] >= f"{max(frontier - w, 0):03d}"]
    if len(recent) < 50:
        return ""
    y = recent["outcome"].to_numpy().astype(np.int8)
    ivs = []
    for f in CLAB_FIELDS:
        iv = information_value(recent[f].to_numpy(), y)
        if math.isfinite(iv):
            ivs.append((f, float(iv)))
    ivs.sort(key=lambda t: -t[1])
    body = ", ".join(f"{f}={iv:.3f}" for f, iv in ivs)
    return f"\n检索记忆(最近{w}期字段IV,降序): {body}"


def recovery_rounds(series: list[float], switch_rounds: list[int],
                    rounds: int, frac: float = 0.9) -> list[dict]:
    """每个切换:avg_iv 恢复到切换前水平 frac 所需轮数。

    series: 1-based 轮次的 avg_iv(list[round-1])。r_s = 首个 frontier 覆盖
    切换点的轮次(1-based)。pre = 切换前最多 2 轮均值;pre <= 0 的切换跳过
    (无基线可恢复)。未恢复记 rounds - r_s + 1(censored)。
    """
    out: list[dict] = []
    for r_s in switch_rounds:
        if r_s < 2 or r_s >= rounds:
            continue
        pre = float(np.mean(series[max(0, r_s - 3):r_s - 1]))
        if pre <= 0:
            continue
        thr = frac * pre
        rec: int | None = None
        for r in range(r_s, rounds + 1):
            if series[r - 1] >= thr:
                rec = r - r_s + 1
                break
        out.append({
            "switch_round": r_s,
            "pre_level": round(pre, 4),
            "threshold": round(thr, 4),
            "recovery_rounds": rec if rec is not None else rounds - r_s + 1,
            "recovered": rec is not None,
        })
    return out


def _run_adaptation_arm(
    arm: str,
    world: Any,
    llm: Any,
    pm_model: Any,
    optimizer: Any,
    *,
    seed: int,
    rounds: int,
    dev_episodes: int,
    episodes: int,
    work_dir: Path,
    ttt_config: TTTConfig,
    retrieval_w: int = 4,
) -> list[dict]:
    """单臂流式闭环:frontier 每轮推进 dev_episodes/rounds 期。"""
    from eval.orchestrator_eval import OrchestratorCloud
    from selflearn import DEFAULT_LGBM_PARAMS, LoopConfig, SelfLearnLoop, StrategyMemory
    from selflearn.clab import CLAB_FIELDS, CLAB_FIELD_STATEMENTS, build_clab_split, clab_base_features
    from slots import SlotConfig, SlotService

    if work_dir.exists():
        shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True, exist_ok=True)
    memory = StrategyMemory(SlotService(work_dir / "slots.db", SlotConfig()))
    memory.init_field_slots(CLAB_FIELD_STATEMENTS)

    class _TriadCloud(OrchestratorCloud):
        """臂 C 专用:prompt 注入检索记忆文本(经 _extra_prompt_context 钩子)。"""

        retrieval_text = ""

        def _extra_prompt_context(self) -> str:
            return self.retrieval_text if arm == "C" else ""

    cloud = _TriadCloud(llm=llm, fields_str=" ".join(CLAB_FIELDS),
                        cloud_llm=None, allow_fallback=True)

    series: list[dict] = []
    for r in range(1, rounds + 1):
        frontier = dev_episodes * r // rounds
        split_r = build_clab_split(world, dev_episodes=frontier)
        regime = int(world.casebook.regime_id[split_r.dev_case_idx[-1]])

        if arm == "C":
            cloud.retrieval_text = recent_field_iv_text(
                split_r.dev_df, frontier, retrieval_w)

        loop_cfg = LoopConfig(
            dev_start="000", dev_end=f"{frontier - 1:03d}",
            eval_start=f"{frontier:03d}", eval_end=f"{episodes - 1:03d}",
            top_k=20, max_features_per_round=20,
            dev_holdout_episodes=2,
            lgbm_params=dict(DEFAULT_LGBM_PARAMS), seed=seed,
        )
        loop = SelfLearnLoop(split_r.dev_df, config=loop_cfg,
                             base_features=clab_base_features,
                             cloud=cloud, memory=memory)
        if arm == "A":
            rec = loop.run_round(r)  # adapter 开启:PM 参与提案
        else:
            with pm_model.disable_adapter():  # 纯基座(B);基座+检索上下文(C)
                rec = loop.run_round(r)

        ivs = [p.metrics.get("iv", 0) for p in rec.proposals
               if p.verdict == "pass"
               and isinstance(p.metrics.get("iv", 0), (int, float))]
        avg_iv = float(np.mean(ivs)) if ivs else 0.0
        series.append({
            "round": r, "frontier": frontier, "regime": regime,
            "avg_iv": round(avg_iv, 4), "n_passed": len(ivs),
            "completion": cloud.last_completion or "",
        })
        print(f"  [arm {arm}] round {r}/{rounds}: avg_iv={avg_iv:.4f} "
              f"regime={regime} action={cloud.last_completion!r}")

        if arm == "A" and cloud.last_prompt:
            ttt_step(pm_model, llm._tokenizer, optimizer,
                     prompt=cloud.last_prompt,
                     completion=cloud.last_completion or "GBDT income_volatility",
                     reward=avg_iv, config=ttt_config)

    memory.service.persist()
    return series


def run_regime_adaptation(llm: Any, ttt_config: TTTConfig, *,
                          seed: int = 20260730, rounds: int = 10,
                          dev_episodes: int = 40, episodes: int = 60,
                          per_episode: int = 200,
                          work_root: Path | None = None) -> dict:
    """三臂闭环:A=Session PM+TTT,B=无 PM,C=检索记忆;恢复轮数对比。"""
    from synth import SyntheticWorld, default_config

    world = SyntheticWorld(default_config(seed=seed)).run(episodes, per_episode)
    switches = [e.episode for e in world.regimes if 0 < e.episode < dev_episodes]
    # 切换点 e → 首个 frontier 覆盖它的轮次(1-based)
    switch_rounds = sorted({e * rounds // dev_episodes + 1 for e in switches})

    pm, opt = attach_session_pm(llm._model, r=ttt_config.lora_r, lr=ttt_config.lr)
    zero_pm(pm)

    work_root = work_root or Path("eval/artifacts-memory-triad/work")
    arms: dict[str, list[dict]] = {}
    for arm in ("A", "B", "C"):
        if arm == "A":
            zero_pm(pm)  # 防串扰:臂 A 从干净 PM 开始
        arms[arm] = _run_adaptation_arm(
            arm, world, llm, pm, opt, seed=seed, rounds=rounds,
            dev_episodes=dev_episodes, episodes=episodes,
            work_dir=work_root / f"arm{arm}", ttt_config=ttt_config)

    result: dict[str, Any] = {
        "seed": seed, "rounds": rounds, "dev_episodes": dev_episodes,
        "switch_episodes": switches, "switch_rounds": switch_rounds,
        "arms": {},
    }
    for arm, series in arms.items():
        iv_series = [s["avg_iv"] for s in series]
        rec = recovery_rounds(iv_series, switch_rounds, rounds)
        result["arms"][arm] = {
            "series": series,
            "avg_iv_overall": round(float(np.mean(iv_series)), 4),
            "recovery": rec,
            "mean_recovery_rounds": round(
                float(np.mean([x["recovery_rounds"] for x in rec])), 3
            ) if rec else None,
        }
    return result


def plot_adaptation(report: dict, path: Path) -> None:
    """三臂 avg_iv 逐轮曲线 + regime 切换竖线。"""
    rounds = report["rounds"]
    xs = list(range(1, rounds + 1))
    labels = {"A": "A: Session PM (TTT)", "B": "B: no PM (base)",
              "C": "C: retrieval memory"}
    colors = {"A": "tab:red", "B": "tab:gray", "C": "tab:blue"}
    fig, ax = plt.subplots(figsize=(9, 5))
    for arm in ("A", "B", "C"):
        ys = [s["avg_iv"] for s in report["arms"][arm]["series"]]
        ax.plot(xs, ys, marker="o", ms=4, color=colors[arm], label=labels[arm])
    for r_s in report["switch_rounds"]:
        ax.axvline(r_s - 0.5, color="green", ls="--", lw=0.9)
    ax.set_xlabel("round")
    ax.set_ylabel("avg_iv (passed proposals)")
    ax.set_title("Regime adaptation: rounds-to-90%-recovery after switch "
                 "(green dashed = regime switch)")
    ax.legend(fontsize=8)
    fig.tight_layout()
    path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(path, dpi=150)
    plt.close(fig)


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------


def main() -> None:
    parser = argparse.ArgumentParser(description="Session PM 可写参数记忆三测")
    parser.add_argument("--test", choices=("1", "2", "3", "all"), default="all")
    parser.add_argument("--seed", type=int, default=20260730)
    parser.add_argument("--rounds", type=int, default=10)
    parser.add_argument("--write-epochs", type=int, default=3)
    parser.add_argument("--ttt-steps", type=int, default=3)
    parser.add_argument("--ttt-lr", type=float, default=3e-4)
    parser.add_argument("--model", type=str, default="Qwen/Qwen3-0.6B")
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-memory-triad"))
    args = parser.parse_args()

    torch.set_num_threads(os.cpu_count() or 8)

    from llm.local_llm import LocalLLM

    print(f">>> loading {args.model}")
    llm = LocalLLM(model_id=args.model, device="cpu")
    llm.load()

    ttt_config = TTTConfig(steps=args.ttt_steps, lr=args.ttt_lr,
                           lora_r=8, max_len=256)
    args.out.mkdir(parents=True, exist_ok=True)

    if args.test in ("1", "all"):
        print("=== 测 1:写入-召回保真度 ===")
        r1 = run_write_recall(llm, ttt_config, write_epochs=args.write_epochs)
        (args.out / "test1_write_recall.json").write_text(
            json.dumps(r1, indent=2, ensure_ascii=False))
        print(json.dumps(r1["arms"], indent=2, ensure_ascii=False))

    if args.test in ("2", "all"):
        print("=== 测 2:连续写入干扰度(遗忘曲线)===")
        r2 = run_forgetting(llm, ttt_config, write_epochs=args.write_epochs)
        (args.out / "test2_forgetting.json").write_text(
            json.dumps(r2, indent=2, ensure_ascii=False))
        plot_forgetting(r2, args.out / "forgetting_curve.png")
        print("matrix:", r2["matrix_batch_x_timestep"])
        print("base:", r2["base_recall_per_batch"],
              "ctx:", r2["context_carry"]["recall_per_batch"])

    if args.test in ("3", "all"):
        print("=== 测 3:regime 适应速度 ===")
        torch.manual_seed(args.seed)
        r3 = run_regime_adaptation(llm, ttt_config, seed=args.seed,
                                   rounds=args.rounds,
                                   work_root=args.out / "work")
        (args.out / "test3_regime_adaptation.json").write_text(
            json.dumps(r3, indent=2, ensure_ascii=False, default=str))
        plot_adaptation(r3, args.out / "adaptation_speed.png")
        for arm in ("A", "B", "C"):
            a = r3["arms"][arm]
            print(f"arm {arm}: avg_iv={a['avg_iv_overall']:.4f} "
                  f"mean_recovery={a['mean_recovery_rounds']}")

    print(f"artifacts in {args.out}")


if __name__ == "__main__":
    main()
