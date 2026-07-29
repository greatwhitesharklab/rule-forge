"""RPC Paper 1:ParameterComposer 测试。"""

from __future__ import annotations

import pytest
import torch
import torch.nn as nn

from selflearn.composer import CompositionConfig, ParameterComposer


class TinyBase(nn.Module):
    """测试用极小 base 模型。"""

    def __init__(self, dim=16, vocab=100):
        super().__init__()
        self.embed = nn.Embedding(vocab, dim)
        self.q_proj = nn.Linear(dim, dim)  # 模拟 attention q_proj
        self.v_proj = nn.Linear(dim, dim)  # 模拟 attention v_proj
        self.lm_head = nn.Linear(dim, vocab)

    def forward(self, input_ids):
        h = self.embed(input_ids)
        h = self.q_proj(h)
        h = self.v_proj(h)
        return self.lm_head(h)


class TestParameterComposerInit:
    """初始化:base 冻结,Session LoRA 零 delta。"""

    def test_base_冻结(self):
        base = TinyBase()
        composer = ParameterComposer(base)
        for p in base.parameters():
            assert not p.requires_grad

    def test_门控可训(self):
        base = TinyBase()
        composer = ParameterComposer(base)
        assert composer.g_domain.requires_grad
        assert composer.g_session.requires_grad

    def test_init_session_lora_找到_qv_proj(self):
        base = TinyBase(dim=8)
        composer = ParameterComposer(base)
        composer.init_session_lora(r=4)
        # 应该找到 q_proj 和 v_proj 各 2 个参数(A/B)
        assert len(composer._session_lora) == 4  # 2 层 × (A + B)

    def test_session_lora_零初始化_B(self):
        """Session LoRA 的 B 零初始化 -> 初始 delta=0 -> 不影响 base。"""
        base = TinyBase(dim=8)
        composer = ParameterComposer(base)
        composer.init_session_lora(r=4)
        for name, param in composer._session_lora.items():
            if "lora_B" in name:
                assert param.data.abs().max() == 0.0


class TestParameterComposerGate:
    """门控:sigmoid 控制组合比例。"""

    def test_默认门控_0_5(self):
        """g=0 时 sigmoid=0.5(Domain 和 Session 各半)。"""
        base = TinyBase()
        composer = ParameterComposer(base)
        assert pytest.approx(torch.sigmoid(composer.g_domain).item()) == 0.5
        assert pytest.approx(torch.sigmoid(composer.g_session).item()) == 0.5

    def test_get_composition_info(self):
        base = TinyBase()
        composer = ParameterComposer(base)
        composer.init_session_lora(r=4)
        info = composer.get_composition_info()
        assert "g_domain" in info
        assert "g_session" in info
        assert "n_session_lora" in info
        assert info["n_session_lora"] == 4

    def test_可训练参数列表(self):
        base = TinyBase(dim=8)
        composer = ParameterComposer(base)
        composer.init_session_lora(r=4)
        params = composer.get_trainable_parameters()
        # 2 个门控 + 4 个 LoRA 参数 = 6
        assert len(params) == 6
        for p in params:
            assert p.requires_grad
