"""RPC v2.0:ParameterComposer 测试(层级门控 + 三 PM)。"""

from __future__ import annotations

import pytest
import torch
import torch.nn as nn

from selflearn.composer import CompositionConfig, ParameterComposer


class TinyBase(nn.Module):
    """测试用极小 base 模型。"""

    def __init__(self, dim=16, vocab=100, n_layers=4):
        super().__init__()
        self.embed = nn.Embedding(vocab, dim)
        # 模拟 n_layers 层(每层有 q_proj/v_proj)
        self.layers = nn.ModuleList([
            nn.ModuleDict({
                "q_proj": nn.Linear(dim, dim),
                "v_proj": nn.Linear(dim, dim),
            }) for _ in range(n_layers)
        ])
        self.lm_head = nn.Linear(dim, vocab)

    def forward(self, input_ids):
        h = self.embed(input_ids)
        for layer in self.layers:
            h = layer["q_proj"](h)
            h = layer["v_proj"](h)
        return self.lm_head(h)


class TestParameterComposerV2:
    """v2.0:层级门控 + 三 PM(Domain/User/Session)。"""

    def test_base_冻结(self):
        base = TinyBase(n_layers=4)
        composer = ParameterComposer(base, CompositionConfig(n_layers=4))
        for p in base.parameters():
            assert not p.requires_grad

    def test_层级门控_shape(self):
        """门控 shape = [n_layers × 3](domain/user/session)。"""
        base = TinyBase(n_layers=4)
        composer = ParameterComposer(base, CompositionConfig(n_layers=4))
        assert composer.gates.shape == (4, 3)

    def test_门控可训(self):
        base = TinyBase(n_layers=4)
        composer = ParameterComposer(base, CompositionConfig(n_layers=4))
        assert composer.gates.requires_grad

    def test_get_gate_values_sigmoid(self):
        """门控值经过 sigmoid(0-1 之间)。"""
        base = TinyBase(n_layers=4)
        composer = ParameterComposer(base, CompositionConfig(n_layers=4))
        gates = composer.get_gate_values()
        assert gates.shape == (4, 3)
        assert (gates >= 0).all() and (gates <= 1).all()

    def test_init_session_lora(self):
        base = TinyBase(n_layers=4, dim=8)
        composer = ParameterComposer(base, CompositionConfig(n_layers=4))
        composer.init_session_lora(r=4)
        # 4 层 × 2 模块(q/v)× 2 参数(A/B) = 16
        assert len(composer._session_lora) == 16

    def test_session_lora_B_零初始化(self):
        base = TinyBase(n_layers=4, dim=8)
        composer = ParameterComposer(base, CompositionConfig(n_layers=4))
        composer.init_session_lora(r=4)
        for name, param in composer._session_lora.items():
            if "lora_B" in name:
                assert param.data.abs().max() == 0.0

    def test_get_composition_info_含三层统计(self):
        """组合信息含 domain/user/session 三层的门控均值。"""
        base = TinyBase(n_layers=4)
        composer = ParameterComposer(base, CompositionConfig(n_layers=4))
        composer.init_session_lora(r=4)
        info = composer.get_composition_info()
        assert "domain_gate_mean" in info
        assert "user_gate_mean" in info
        assert "session_gate_mean" in info
        assert "domain_gate_last4" in info  # 高层门控
        assert info["n_layers"] == 4

    def test_可训练参数含门控和LoRA(self):
        base = TinyBase(n_layers=4, dim=8)
        composer = ParameterComposer(base, CompositionConfig(n_layers=4))
        composer.init_session_lora(r=4)
        params = composer.get_trainable_parameters()
        # 1 个门控 tensor + 16 个 LoRA 参数 = 17
        assert len(params) == 17
        for p in params:
            assert p.requires_grad

    def test_28层_真实Qwen3配置(self):
        """测试真实 28 层配置(跟 Qwen3-0.6B 一致)。"""
        base = TinyBase(n_layers=28, dim=8)
        composer = ParameterComposer(base, CompositionConfig(n_layers=28))
        assert composer.gates.shape == (28, 3)
        gates = composer.get_gate_values()
        assert gates.shape == (28, 3)
