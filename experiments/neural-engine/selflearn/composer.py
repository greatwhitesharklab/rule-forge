"""RPC v2.0:ParameterComposer(三层 PM + 层级门控)。

v1.0:Domain PM + Session PM,全局标量门控
v2.0:Domain PM + User PM + Session PM,层级门控(per-layer)

组合公式:
  W_runtime = W_base
    + g_domain^(l) × ΔW_domain
    + g_user^(l) × ΔW_user
    + g_session^(l) × ΔW_session

其中 g^(l) 是每一层独立的门控(L=28 层 × 3 PM = 84 个 gate)。

Patch(PM)不一定是 LoRA。v1.0 用 LoRA 实现,v2.0 保持抽象:
  ΔP 可以是 LoRA / KV delta / Router delta / Hidden delta。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import torch
import torch.nn as nn


@dataclass(frozen=True)
class CompositionConfig:
    """Φ 组合配置(v2.0:层级门控)。"""

    n_layers: int = 28             # Transformer 层数(用于层级门控)
    # 门控初始化:σ(0)=0.5(平衡)
    gate_init: float = 0.0


class ParameterComposer(nn.Module):
    """RPC Φ(Level 1 层级门控):组合 Domain + User + Session PM。

    v2.0 改进:
    1. 加 User PM(研究员个性化偏好)
    2. 门控从全局标量 → 层级门控(每层每 PM 独立 gate)
    3. Patch 抽象(不固定为 LoRA)

    工作方式:
    - Base model 冻结
    - Domain PM(冻结):行业领域知识
    - User PM(冻结):研究员偏好(可选,没给就跳过)
    - Session PM(可训,TTT):本次会话经验
    - 层级门控:每层 3 个 gate(decide 该层的组合比例)
    """

    def __init__(
        self,
        base_model: nn.Module,
        config: CompositionConfig = CompositionConfig(),
    ) -> None:
        super().__init__()
        self.base = base_model
        self.config = config
        self.n_layers = config.n_layers

        # 冻结 base
        for p in self.base.parameters():
            p.requires_grad = False

        # Session PM:可训练的 LoRA 参数(ParameterDict)
        self._session_lora = nn.ParameterDict()
        self._initialized = False

        # 层级门控:每层每 PM 一个 gate
        # shape: [n_layers × 3] (domain, user, session)
        # 用小随机初始化(避免冷启动零梯度)
        self.gates = nn.Parameter(
            torch.randn(config.n_layers, 3) * 0.01 + config.gate_init
        )

    def init_session_lora(self, r: int = 8) -> None:
        """初始化 Session PM 的 LoRA 参数。"""
        layer_idx = 0
        for name, module in self.base.named_modules():
            if isinstance(module, nn.Linear) and any(
                t in name for t in ["q_proj", "v_proj"]
            ):
                in_features = module.in_features
                out_features = module.out_features
                lora_A = nn.Parameter(torch.randn(r, in_features) * 0.01)
                lora_B = nn.Parameter(torch.zeros(out_features, r))
                safe_name = name.replace(".", "_")
                self._session_lora[safe_name + "_lora_A"] = lora_A
                self._session_lora[safe_name + "_lora_B"] = lora_B

        self._initialized = True

    def get_gate_values(self) -> torch.Tensor:
        """返当前门控值 [n_layers × 3],sigmoid 后的。"""
        return torch.sigmoid(self.gates).detach()

    def get_trainable_parameters(self) -> list[nn.Parameter]:
        """返所有可训练参数(Session LoRA + 门控)。"""
        params = [self.gates]
        params.extend(self._session_lora.values())
        return [p for p in params if p.requires_grad]

    def get_composition_info(self) -> dict:
        """返当前组合状态(审计用)。"""
        gates = torch.sigmoid(self.gates).detach()
        return {
            "n_layers": self.n_layers,
            "n_pm_types": 3,  # domain, user, session
            "gates_shape": list(gates.shape),
            # 每层门控的均值(概览)
            "domain_gate_mean": round(gates[:, 0].mean().item(), 4),
            "user_gate_mean": round(gates[:, 1].mean().item(), 4),
            "session_gate_mean": round(gates[:, 2].mean().item(), 4),
            # 最后 4 层(高层语义)的门控
            "domain_gate_last4": [round(g, 4) for g in gates[-4:, 0].tolist()],
            "session_gate_last4": [round(g, 4) for g in gates[-4:, 2].tolist()],
            "n_session_lora": len(self._session_lora),
        }
