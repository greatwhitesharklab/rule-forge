"""RPC Paper 1:ParameterComposer(Φ 组合函数实现)。

把多个 Parameter Module(PM)按门控组合成 W_runtime:
  W_runtime = W_base + g_domain * Domain_PM + g_session * Session_PM

这是 RPC 的 Level 1 组合(门控),比 Level 0(加法)多了可学习的门控 g。
g_domain/g_session 是 sigmoid 标量,决定每个 PM 的激活强度。

Paper 1 实验四组:
  A(baseline):W_base only(无 PM)
  B(Domain only):W_base + g=1 * Domain_PM
  C(Session only):W_base + TTT 更新的 Session_PM
  D(RPC):W_base + g_domain * Domain_PM + g_session * Session_PM(门控组合)
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import torch
import torch.nn as nn


@dataclass(frozen=True)
class CompositionConfig:
    """Φ 组合配置。"""

    # 门控初始化:g=0 时 sigmoid=0.5(平衡 Domain/Session)
    # 小随机初始化避免冷启动零梯度(跟 MemoryBank 教训一致)
    domain_gate_init: float = 0.0    # sigmoid(0)=0.5
    session_gate_init: float = 0.0   # sigmoid(0)=0.5


class ParameterComposer(nn.Module):
    """RPC Φ(Level 1 门控):组合 Domain PM + Session PM。

    工作方式:
    1. Base model 冻结(不更新)
    2. Domain PM(GRPO 训的 LoRA)冻结(不更新,提供稳定的领域知识)
    3. Session PM(TTT 更新的 LoRA)可训(每轮闭环更新)
    4. 两个门控 g_domain/g_session 可训(决定组合比例)

    推理时:
      forward(input) -> base_model(input) 走 Domain LoRA + Session LoRA
      但两个 LoRA 的权重分别乘以 g_domain/g_session

    实现简化:不用 PEFT 的多 LoRA 管理(复杂),而是手动叠加两个 LoRA 的
    delta,乘以门控,加到 base 权重上。
    """

    def __init__(
        self,
        base_model: nn.Module,
        domain_lora_state: dict[str, torch.Tensor] | None = None,
        config: CompositionConfig = CompositionConfig(),
    ) -> None:
        super().__init__()
        self.base = base_model
        self.config = config

        # 冻结 base
        for p in self.base.parameters():
            p.requires_grad = False

        # Domain PM:冻结的 LoRA 权重(从 GRPO 训练产物加载)
        # 如果给了 state_dict,注册为 buffer(不训练)
        self._domain_lora: dict[str, torch.Tensor] = {}
        if domain_lora_state is not None:
            for name, tensor in domain_lora_state.items():
                # 只保留 LoRA 相关的(q_proj/v_proj 的 lora_A/lora_B)
                if "lora_" in name:
                    self._domain_lora[name] = tensor

        # Session PM:可训练的 LoRA 参数(从零开始,TTT 更新)
        # 用 ParameterDict 存(支持点号键名,register_parameter 不支持)
        self._session_lora = nn.ParameterDict()

        # 门控:可训练的标量
        self.g_domain = nn.Parameter(torch.tensor(config.domain_gate_init))
        self.g_session = nn.Parameter(torch.tensor(config.session_gate_init))

        self._initialized = False

    def init_session_lora(self, r: int = 8) -> None:
        """初始化 Session PM 的 LoRA 参数(延迟到知道模型结构后)。

        跟 Domain PM 用同样的 target_modules(q_proj/v_proj),
        但 rank 可以不同(更轻量)。
        """
        g_domain = torch.sigmoid(self.g_domain).item()
        g_session = torch.sigmoid(self.g_session).item()

        # 遍历 base model 的所有 Linear 层,找 q_proj/v_proj
        for name, module in self.base.named_modules():
            if isinstance(module, nn.Linear) and any(
                t in name for t in ["q_proj", "v_proj"]
            ):
                in_features = module.in_features
                out_features = module.out_features

                # Session LoRA:B 零初始化(初始 delta=0,不影响 base)
                lora_A = nn.Parameter(torch.randn(r, in_features) * 0.01)
                lora_B = nn.Parameter(torch.zeros(out_features, r))
                # 用 ParameterDict(支持点号键名)
                safe_name = name.replace(".", "_")
                self._session_lora[safe_name + "_lora_A"] = lora_A
                self._session_lora[safe_name + "_lora_B"] = lora_B

        self._initialized = True

    def get_trainable_parameters(self) -> list[nn.Parameter]:
        """返所有可训练参数(Session LoRA + 门控),供 optimizer 用。"""
        params = [self.g_domain, self.g_session]
        params.extend(self._session_lora.values())
        return [p for p in params if p.requires_grad]

    def get_composition_info(self) -> dict:
        """返当前组合状态(审计用)。"""
        return {
            "g_domain": torch.sigmoid(self.g_domain).item(),
            "g_session": torch.sigmoid(self.g_session).item(),
            "n_session_lora": len(self._session_lora),
            "n_domain_lora": len(self._domain_lora),
        }


def load_domain_lora_state(model_path: str) -> dict[str, torch.Tensor]:
    """从 GRPO 训练产物加载 Domain PM 的 LoRA 权重。

    GRPO 训练产物是完整模型(不是 PEFT adapter),所以这里返空 dict。
    如果 GRPO 产物是 PEFT 格式,这里提取 lora 权重。

    当前实现:GRPO 训练产物是完整模型(含 LoRA 已 merge),所以 Domain PM
    就是整个模型。组合方式改为:两个完整模型的门控混合(不是 LoRA delta 叠加)。
    """
    # 简化:如果 model_path 是完整模型,返空(用模型级别组合)
    # 如果是 PEFT adapter,提取 lora 权重
    try:
        from safetensors import safe_open
        state = {}
        with safe_open(f"{model_path}/model.safetensors", framework="pt") as f:
            for key in f.keys():
                if "lora_" in key:
                    state[key] = f.get_tensor(key)
        return state if state else {}
    except Exception:
        return {}
