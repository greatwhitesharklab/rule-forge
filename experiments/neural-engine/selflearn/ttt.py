"""阶段 2 方向 A:TTT(Test-Time Training)在线更新。

跟 GRPO(方向 B)的区别:
- GRPO 需要 group sampling(4 个 completion 算 relative advantage)+ 攒经验池
- TTT 单样本更新:每轮拿 (prompt, completion, reward) 直接更新权重
- TTT 用 reward-weighted loss,不需要 trl/trainer,直接 torch

核心 loss:
  loss = reward * sft_loss
  sft_loss = -log_prob(completion | prompt)  (transformers 标准 SFT loss)
  reward > 0: loss 正 -> 梯度减小 loss -> 增加 completion 概率(好动作强化)
  reward < 0: loss 负 -> 梯度反向 -> 降低 completion 概率(坏动作抑制)

用 LoRA 更新(参数高效,不破坏 base,跟 lora/train.py 的 freeze guard 一致)。
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import torch
import torch.nn.functional as F


@dataclass(frozen=True)
class TTTConfig:
    """TTT 更新配置。"""

    lr: float = 1e-4              # TTT 学习率(比 GRPO 大,单样本更新需要更快)
    steps: int = 3                # 每次经验的更新步数(防过拟合)
    max_len: int = 512            # prompt + completion 最大长度
    lora_r: int = 8               # LoRA rank(比 lora/train.py 小,轻量)


def _encode_pair(
    tokenizer: Any, prompt: str, completion: str, max_len: int
) -> dict[str, torch.Tensor]:
    """编码 prompt+completion,labels mask prompt 部分。

    跟 lora/train.py 的 encode_pair 一致:prompt 部分标签 -100,只学 completion。
    """
    p_ids = tokenizer(prompt, return_tensors="pt")["input_ids"][0].tolist()
    c_ids = tokenizer(completion, return_tensors="pt")["input_ids"][0].tolist()
    # 截断(保留尾部 = completion 优先)
    ids = (p_ids + c_ids)[-max_len:]
    labels = ([-100] * len(p_ids) + c_ids)[-max_len:]
    return {
        "input_ids": torch.tensor([ids], dtype=torch.long),
        "labels": torch.tensor([labels], dtype=torch.long),
    }


def ttt_step(
    model: torch.nn.Module,
    tokenizer: Any,
    optimizer: torch.optim.Optimizer,
    prompt: str,
    completion: str,
    reward: float,
    config: TTTConfig,
) -> dict[str, float]:
    """一步 TTT 更新:reward-weighted SFT。

    返回 metrics(sft_loss, weighted_loss, reward)用于监控。
    """
    model.train()
    batch = _encode_pair(tokenizer, prompt, completion, config.max_len)
    batch = {k: v.to(next(model.parameters()).device) for k, v in batch.items()}

    metrics: dict[str, float] = {"reward": reward}
    for step in range(config.steps):
        outputs = model(**batch)
        sft_loss = outputs.loss  # -mean(log_prob(completion))

        # reward-weighted:reward > 0 强化,reward < 0 抑制
        # loss = reward * sft_loss
        # reward > 0: loss 正,梯度减小 sft_loss -> 增加 prob
        # reward < 0: loss 负,梯度增大 sft_loss -> 降低 prob
        weighted_loss = reward * sft_loss

        optimizer.zero_grad()
        weighted_loss.backward()
        optimizer.step()

        if step == config.steps - 1:  # 记录最后一步
            metrics["sft_loss"] = float(sft_loss.item())
            metrics["weighted_loss"] = float(weighted_loss.item())

    model.eval()
    return metrics


def setup_ttt_lora(
    model: torch.nn.Module,
    config: TTTConfig,
) -> torch.optim.Optimizer:
    """给模型挂 LoRA + 返 optimizer(只优化 LoRA 参数)。

    跟 lora/train.py 的 BaseSnapshot guard 不同 —— TTT 不做全量 base 快照
    (太慢),而是用 LoRA 保证 base 不动(LoRA 只更新低秩矩阵)。
    """
    from peft import LoraConfig, get_peft_model

    # 检查是否已经挂了 LoRA(避免重复挂)
    if hasattr(model, "peft_config") and model.peft_config:
        trainable = [p for p in model.parameters() if p.requires_grad]
    else:
        peft_cfg = LoraConfig(
            r=config.lora_r,
            lora_alpha=config.lora_r * 2,
            lora_dropout=0.0,
            target_modules=["q_proj", "v_proj"],  # 轻量:只 attention 的 q/v
            task_type="CAUSAL_LM",
        )
        model = get_peft_model(model, peft_cfg)
        trainable = [p for p in model.parameters() if p.requires_grad]

    optimizer = torch.optim.AdamW(trainable, lr=config.lr)
    return optimizer
