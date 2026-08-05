"""RPC v2.0:ParameterComposer(三层 PM + 层级门控 + 多种 Φ)。

v1.0:Domain PM + Session PM,全局标量门控
v2.0:Domain PM + User PM + Session PM,层级门控(per-layer)

Φ 的多种实现(论文消融对比):
  Level 0 (Add):       output = base(x) + Σ delta_i(x)
  Level 1 (Gate Add):  output = base(x) + Σ g_i * delta_i(x)
  Level 2 (Attention): output = base(x) + Attn(Q, K_i, V_i)
  Level 3 (Tiny MLP):  output = base(x) + MLP([delta_1, delta_2, ...])

Patch(PM)不一定是 LoRA。v1.0 用 LoRA 实现,v2.0 保持抽象:
  ΔP 可以是 LoRA / KV delta / Router delta / Hidden delta。
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

import torch
import torch.nn as nn
import torch.nn.functional as F


def compute_weight_deltas(
    base_safetensors: str,
    merged_safetensors: str,
    targets: tuple[str, ...] = ("q_proj", "v_proj"),
) -> dict[str, torch.Tensor]:
    """Dense per-module weight deltas (merged - base) for target linears.

    Used to reconstruct a merged PM (e.g. GRPO domain model) as an explicit
    additive delta over the base, so per-layer gates can modulate it.
    Keys are module names (e.g. "model.layers.3.self_attn.q_proj").
    """
    from safetensors import safe_open

    deltas: dict[str, torch.Tensor] = {}
    with safe_open(base_safetensors, framework="pt") as fb, \
            safe_open(merged_safetensors, framework="pt") as fm:
        for key in fb.keys():
            if key.endswith(".weight") and any(t in key for t in targets):
                name = key[: -len(".weight")]
                deltas[name] = (fm.get_tensor(key) - fb.get_tensor(key)).float()
    return deltas


class PhiType(str, Enum):
    """Φ 组合函数类型(论文消融对比)。"""

    ADD = "add"               # Level 0:简单加法(无门控)
    GATE_ADD = "gate_add"     # Level 1:门控加法(当前主实验)
    ATTENTION = "attention"   # Level 2:attention 组合(Paper 2)
    MLP = "mlp"               # Level 3:Tiny MLP 组合(Paper 2)


@dataclass(frozen=True)
class CompositionConfig:
    """Φ 组合配置(v2.0:层级门控 + 多种 Φ)。"""

    n_layers: int = 28             # Transformer 层数
    gate_init: float = 0.0         # 门控初始化(σ(0)=0.5)
    phi_type: PhiType = PhiType.GATE_ADD  # Φ 类型(Level 1 为默认)
    # Attention Φ 参数
    attn_dim: int = 64             # Attention 内部维度(Level 2)


class ParameterComposer(nn.Module):
    """RPC Φ:组合 Domain + User + Session PM。

    支持多种 Φ 实现(通过 phi_type 配置):
    - ADD:base(x) + session_delta(x) —— 无门控 baseline
    - GATE_ADD:base(x) + g * session_delta(x) —— 层级门控(主实验)
    - ATTENTION:base(x) + Attn(query, keys, values) —— 跨 PM 交互
    - MLP:base(x) + MLP([deltas]) —— 非线性组合

    工作方式(forward):
    1. base model 前向传播(冻结,no_grad)
    2. Session PM 的 LoRA delta 前向传播
    3. 按 Φ 类型组合 base 输出 + Session delta
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
        self.phi_type = config.phi_type

        # 冻结 base
        for p in self.base.parameters():
            p.requires_grad = False

        # Session PM:可训练的 LoRA 参数
        self._session_lora = nn.ParameterDict()
        self._session_layers: list[tuple[str, nn.Linear]] = []  # 记录哪层有 LoRA
        self._initialized = False
        self._wire_handles: list[Any] = []  # forward hooks from wire()

        # 层级门控(Level 1):每层每 PM 一个 gate
        # 列 0=domain, 1=user, 2=session
        self.gates = nn.Parameter(
            torch.randn(config.n_layers, 3) * 0.01 + config.gate_init
        )

        # Attention Φ(Level 2)参数:延迟初始化
        self._attn_proj_q: nn.Linear | None = None
        self._attn_proj_k: nn.Linear | None = None
        self._attn_proj_v: nn.Linear | None = None

        # MLP Φ(Level 3)参数:延迟初始化
        self._mlp: nn.Sequential | None = None

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
                self._session_layers.append((name, module))
                layer_idx += 1

        self._initialized = True

        # 延迟初始化 Attention/MLP(需要知道 hidden_dim)
        hidden_dim = self._session_layers[0][1].out_features if self._session_layers else 1024
        if self.phi_type == PhiType.ATTENTION:
            self._init_attention(hidden_dim)
        elif self.phi_type == PhiType.MLP:
            self._init_mlp(hidden_dim)

    def _init_attention(self, hidden_dim: int) -> None:
        """初始化 Attention Φ(Level 2)。"""
        d = self.config.attn_dim
        self._attn_proj_q = nn.Linear(hidden_dim, d, bias=False)
        self._attn_proj_k = nn.Linear(hidden_dim, d, bias=False)
        self._attn_proj_v = nn.Linear(d, hidden_dim, bias=False)

    def _init_mlp(self, hidden_dim: int) -> None:
        """初始化 Tiny MLP Φ(Level 3)。"""
        self._mlp = nn.Sequential(
            nn.Linear(hidden_dim * 2, hidden_dim),
            nn.GELU(),
            nn.Linear(hidden_dim, hidden_dim),
        )

    def _compute_session_delta(self, layer_name: str, hidden: torch.Tensor) -> torch.Tensor:
        """计算某一层 Session PM 的 LoRA delta。

        delta = B @ A @ hidden(LoRA 低秩近似)
        """
        safe = layer_name.replace(".", "_")
        A = self._session_lora.get(safe + "_lora_A")
        B = self._session_lora.get(safe + "_lora_B")
        if A is None or B is None:
            return torch.zeros_like(hidden)
        # hidden: [batch, seq, dim] -> A: [r, dim] -> B: [dim, r]
        # delta = hidden @ A^T @ B^T = (B @ A @ hidden^T)^T
        return torch.matmul(torch.matmul(hidden, A.t()), B.t())

    def forward(self, input_ids, labels=None, attention_mask=None, **kwargs) -> Any:
        """RPC 前向传播:base + Session delta,按 Φ 类型组合。

        当前实现:不拦截 base 内部层(复杂度太高),而是:
        1. base 正常前向(no_grad)
        2. 对 logits 做 Session delta 的加性偏置
        (跟 continuous_memory.py 的 MemoryAugmentedLM 策略一致)
        """
        with torch.no_grad():
            outputs = self.base(input_ids=input_ids, attention_mask=attention_mask)
            base_logits = outputs.logits  # [batch, seq, vocab]

        # Session delta:用最后一层的 LoRA 算 hidden-level delta
        # 简化:用 base 的最后 hidden state 算 Session delta,投影到 vocab
        if not self._initialized or not self._session_layers:
            return outputs

        # 取 base 的 embedding 作为 Session LoRA 的输入(可训练 embedding 层)
        # 这样梯度能从 loss → logits → session_delta → LoRA 参数
        embed = self.base.get_input_embeddings()(input_ids)  # [batch, seq, hidden]

        # Session delta(用最后一层的 q_proj LoRA)
        last_layer_name = self._session_layers[-1][0]
        session_delta = self._compute_session_delta(last_layer_name, embed)

        # 按 Φ 类型组合
        if self.phi_type == PhiType.ADD:
            # Level 0:简单加法(无门控)
            composed = session_delta  # delta 直接加
        elif self.phi_type == PhiType.GATE_ADD:
            # Level 1:门控加法(用最后一层的 session gate)
            gate = torch.sigmoid(self.gates[-1, 2])  # session gate of last layer
            composed = gate * session_delta
        elif self.phi_type == PhiType.ATTENTION:
            # Level 2:attention(Q=base_hidden, K=session_delta, V=session_delta)
            if self._attn_proj_q is not None:
                q = self._attn_proj_q(embed)        # [batch, seq, d]
                k = self._attn_proj_k(session_delta) # [batch, seq, d]
                v = session_delta                     # [batch, seq, hidden]
                scores = torch.matmul(q, k.transpose(-2, -1)) / (q.shape[-1] ** 0.5)
                weights = F.softmax(scores, dim=-1)
                composed = torch.matmul(weights, v)
            else:
                composed = session_delta
        elif self.phi_type == PhiType.MLP:
            # Level 3:Tiny MLP(把 base hidden 和 session delta 拼接过 MLP)
            if self._mlp is not None:
                combined = torch.cat([embed, session_delta], dim=-1)
                composed = self._mlp(combined)
            else:
                composed = session_delta
        else:
            composed = session_delta

        # 把 composed(delta)投影到 vocab 维度,加到 base logits
        # 用 base 的 lm_head 做投影(冻结)
        with torch.no_grad():
            lm_head = self.base.get_output_embeddings() if hasattr(
                self.base, "get_output_embeddings") else None

        if lm_head is not None:
            logit_bias = lm_head(composed)  # [batch, seq, vocab]
            final_logits = base_logits + logit_bias
        else:
            final_logits = base_logits

        # 算 loss(如果有 labels)
        loss = None
        if labels is not None:
            shift_logits = final_logits[:, :-1, :].contiguous()
            shift_labels = labels[:, 1:].contiguous()
            mask = shift_labels != -100
            if mask.any():
                loss = F.cross_entropy(
                    shift_logits[mask], shift_labels[mask]
                )

        return type("Out", (), {"loss": loss, "logits": final_logits})()

    def get_gate_values(self) -> torch.Tensor:
        """返当前门控值 [n_layers × 3],sigmoid 后的。"""
        return torch.sigmoid(self.gates).detach()

    # ------------------------------------------------------------------
    # Wired per-layer composition (true gradient path for all gates)
    # ------------------------------------------------------------------

    def wire(
        self,
        domain_deltas: dict[str, torch.Tensor] | None = None,
        user_deltas: dict[str, torch.Tensor] | None = None,
    ) -> int:
        """Attach per-layer gated composition to the base model internals.

        For every LoRA target linear in transformer layer ``l`` the forward
        output becomes::

            y = linear(x) + g_D,l * (x @ Wd^T) + g_U,l * (x @ Wu^T)
                          + g_S,l * ((x @ A^T) @ B^T)

        with ``g = sigmoid(self.gates[l])`` (columns: domain / user /
        session). Both q_proj and v_proj of the same layer share that
        layer's gate row. Gates are zero-initialized (sigma(0) = 0.5), so
        the wired model starts at the additive average; with zero-init
        session LoRA (B = 0) and no domain/user deltas the wiring is
        lossless (bit-identical output to the unwired base).

        Unlike the standalone ``forward`` (logit-level bias, only
        ``gates[-1, 2]`` active), the wired path puts every gate into the
        computation graph: dense PM deltas give the domain/user columns
        immediate gradient, and the session column receives gradient as
        soon as the session LoRA B matrix leaves its zero init (after the
        first optimizer step).

        Returns the number of hooked modules.
        """
        if not self._initialized:
            raise RuntimeError("call init_session_lora() before wire()")
        self.unwire()
        self._wired_delta_names: dict[str, str] = {}
        domain_deltas = domain_deltas or {}
        user_deltas = user_deltas or {}

        n_hooked = 0
        for name, module in self._session_layers:
            m = re.search(r"layers\.(\d+)", name)
            if m is None:
                continue
            layer_idx = int(m.group(1))
            if layer_idx >= self.n_layers:
                continue

            domain = self._register_wired_delta("domain", name,
                                                domain_deltas.get(name))
            user = self._register_wired_delta("user", name,
                                              user_deltas.get(name))
            safe = name.replace(".", "_")
            A = self._session_lora[safe + "_lora_A"]
            B = self._session_lora[safe + "_lora_B"]

            def hook(mod, inputs, output, li=layer_idx, d=domain, u=user,
                     a=A, b=B):
                x = inputs[0]
                g = torch.sigmoid(self.gates[li])
                y = output
                if d is not None:
                    y = y + g[0] * (x @ d.t())
                if u is not None:
                    y = y + g[1] * (x @ u.t())
                return y + g[2] * ((x @ a.t()) @ b.t())

            self._wire_handles.append(module.register_forward_hook(hook))
            n_hooked += 1
        return n_hooked

    def _register_wired_delta(
        self, pm: str, module_name: str, delta: torch.Tensor | None
    ) -> torch.Tensor | None:
        """Register a frozen dense PM delta as a buffer; None passthrough."""
        if delta is None:
            return None
        buf_name = f"_wired_{pm}_{module_name.replace('.', '_')}"
        self.register_buffer(buf_name, delta.detach().float())
        return getattr(self, buf_name)

    def unwire(self) -> None:
        """Remove all wiring hooks, restoring the base model's forward."""
        for h in getattr(self, "_wire_handles", []):
            h.remove()
        self._wire_handles: list[Any] = []

    def get_trainable_parameters(self) -> list[nn.Parameter]:
        """返所有可训练参数(Session LoRA + 门控 + Φ 参数)。"""
        params = [self.gates]
        params.extend(self._session_lora.values())
        if self._attn_proj_q is not None:
            params.extend([self._attn_proj_q.weight, self._attn_proj_k.weight,
                          self._attn_proj_v.weight])
        if self._mlp is not None:
            params.extend(list(self._mlp.parameters()))
        return [p for p in params if p.requires_grad]

    def get_composition_info(self) -> dict:
        """返当前组合状态(审计用)。"""
        gates = torch.sigmoid(self.gates).detach()
        info = {
            "phi_type": self.phi_type.value,
            "n_layers": self.n_layers,
            "n_pm_types": 3,
            "gates_shape": list(gates.shape),
            "domain_gate_mean": round(gates[:, 0].mean().item(), 4),
            "user_gate_mean": round(gates[:, 1].mean().item(), 4),
            "session_gate_mean": round(gates[:, 2].mean().item(), 4),
            "domain_gate_last4": [round(g, 4) for g in gates[-4:, 0].tolist()],
            "session_gate_last4": [round(g, 4) for g in gates[-4:, 2].tolist()],
            "n_session_lora": len(self._session_lora),
        }
        return info
