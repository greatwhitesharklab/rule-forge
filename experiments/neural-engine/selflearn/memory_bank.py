"""阶段 2 方向 C:原生记忆模块(MemoryBank)。

给 0.6B 加一个 key-value 外部记忆,推理时用 cross-attention 检索经验,
TTT 时更新记忆内容。这是 Titans 长期记忆模块的简化版。

跟 LoRA(方向 A)的区别:
- LoRA:记忆是抽象的权重增量(不可读,不可审计)
- MemoryBank:记忆是 key-value pairs(每条对应一个具体经验,可审计)

跟 P1 Engram 的区别:
- P1:哈希寻址(不可学)+ EWMA 写(不进梯度)-> 证伪
- 方向 C:attention 寻址(可学)+ TTT 更新(进梯度)-> 正确版

设计:
  MemoryBank 存储 N 条记忆,每条是一个 (key_vec, value_vec) pair。
  - key_vec: 经验的语义键(如"探索 platform_loans 方向"的 embedding)
  - value_vec: 经验的内容(如"IV=0.4, 好方向"的 embedding)
  推理时: query(当前 prompt embedding)跟所有 key 做 attention, 加权求和 value,
  注入到模型的 hidden state(类似 cross-attention)。
  TTT 时: 用 reward-weighted loss 更新 key_vec/value_vec(进梯度)。
"""

from __future__ import annotations

import math

import torch
import torch.nn as nn


class MemoryBank(nn.Module):
    """Key-value 外部记忆模块(Titans 长期记忆简化版)。

    存 N 条记忆,每条 (key_vec[d_k], value_vec[d_v])。
    推理时 query 跟 key 做 attention,加权求和 value,注入 hidden state。

    跟标准 cross-attention 的区别:
    - 标准 cross-attention: K/V 来自另一个序列(如 encoder output)
    - MemoryBank: K/V 是持久化的参数(跨样本保持,TTT 更新)

    跟 LoRA 的区别:
    - LoRA: 改权重(抽象增量)
    - MemoryBank: 改记忆内容(具体经验,可审计 key/value)
    """

    def __init__(
        self,
        n_slots: int = 16,        # 记忆槽数(经验容量)
        key_dim: int = 64,        # key 向量维度
        value_dim: int = 64,      # value 向量维度
        query_dim: int = 576,     # query 维度(0.6B hidden_size,用于投影)
    ) -> None:
        super().__init__()
        self.n_slots = n_slots
        self.key_dim = key_dim
        self.value_dim = value_dim

        # 记忆槽(key + value),作为可训练参数(持久化,TTT 更新)
        # 小随机初始化(不是零):零初始化导致冷启动问题(memory 不参与前向 -> 无梯度
        # -> 永远零)。小随机让梯度开始流动,TTT 后 memory 逐渐学到有意义的内容。
        self.memory_keys = nn.Parameter(torch.randn(n_slots, key_dim) * 0.02)
        self.memory_values = nn.Parameter(torch.randn(n_slots, value_dim) * 0.02)

        # query 投影:把 hidden state 投影到 key_dim(做 attention)
        self.query_proj = nn.Linear(query_dim, key_dim, bias=False)
        # value 投影:把 memory value 投影回 hidden_dim(注入)
        self.value_proj = nn.Linear(value_dim, query_dim, bias=False)
        # 注入门控(跟 P1 的 read gate 类似,但更简单:sigmoid gate)
        self.gate = nn.Parameter(torch.zeros(1))  # 零初始化 = 初始不注入

    def forward(self, hidden: torch.Tensor) -> torch.Tensor:
        """从记忆检索 + 注入到 hidden state。

        Args:
            hidden: [batch, seq_len, hidden_dim] 模型的 hidden state
        Returns:
            [batch, seq_len, hidden_dim] 注入记忆后的 hidden state
        """
        # query: [batch, seq_len, key_dim]
        q = self.query_proj(hidden)

        # attention: q @ k^T -> [batch, seq_len, n_slots]
        # 用最后一个 token 的 query 做全局检索(简化:不用 per-token attention)
        q_last = q[:, -1:, :]  # [batch, 1, key_dim]
        scores = torch.matmul(q_last, self.memory_keys.T)  # [batch, 1, n_slots]
        scores = scores / math.sqrt(self.key_dim)
        weights = torch.softmax(scores, dim=-1)  # [batch, 1, n_slots]

        # 加权求和 value: [batch, 1, value_dim]
        retrieved = torch.matmul(weights, self.memory_values)
        # 投影回 hidden_dim
        injected = self.value_proj(retrieved)  # [batch, 1, hidden_dim]

        # 门控注入:gate 初始为 0(不影响 base),TTT 后 gate 增大(开始用记忆)
        gate = torch.sigmoid(self.gate)
        # 注入到最后一个 token(当前决策位)
        output = hidden.clone()
        output[:, -1:, :] = hidden[:, -1:, :] + gate * injected
        return output

    def get_memory_snapshot(self) -> dict[str, torch.Tensor]:
        """返当前记忆快照(审计用,可看每条记忆的 key/value)。

        用 clone() 避免后续原地修改影响快照(detach 共享底层 tensor)。
        """
        return {
            "keys": self.memory_keys.detach().cpu().clone(),
            "values": self.memory_values.detach().cpu().clone(),
            "gate": torch.sigmoid(self.gate).detach().cpu().item(),
        }
