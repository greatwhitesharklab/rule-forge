"""RPC v2.0:ParameterComposer 测试(层级门控 + 四种 Φ)。"""

from __future__ import annotations

import pytest
import torch
import torch.nn as nn

from selflearn.composer import CompositionConfig, ParameterComposer, PhiType


class TinyBase(nn.Module):
    """测试用极小 base 模型(有 embedding + lm_head 供 forward 用)。"""

    def __init__(self, dim=16, vocab=50, n_layers=4):
        super().__init__()
        self.embed = nn.Embedding(vocab, dim)
        self.layers = nn.ModuleList([
            nn.ModuleDict({
                "q_proj": nn.Linear(dim, dim),
                "v_proj": nn.Linear(dim, dim),
            }) for _ in range(n_layers)
        ])
        self.lm_head = nn.Linear(dim, vocab)
        self.config = type("C", (), {"hidden_size": dim})()

    def get_input_embeddings(self):
        return self.embed

    def get_output_embeddings(self):
        return self.lm_head

    def forward(self, input_ids, attention_mask=None):
        h = self.embed(input_ids)
        for layer in self.layers:
            h = layer["q_proj"](h)
            h = layer["v_proj"](h)
        return type("O", (), {"logits": self.lm_head(h)})()


class TestParameterComposerInit:
    """初始化:base 冻结,门控 shape,Session LoRA。"""

    def test_base_冻结(self):
        base = TinyBase(n_layers=4)
        c = ParameterComposer(base, CompositionConfig(n_layers=4))
        for p in base.parameters():
            assert not p.requires_grad

    def test_层级门控_shape(self):
        base = TinyBase(n_layers=4)
        c = ParameterComposer(base, CompositionConfig(n_layers=4))
        assert c.gates.shape == (4, 3)

    def test_init_session_lora(self):
        base = TinyBase(n_layers=4, dim=8)
        c = ParameterComposer(base, CompositionConfig(n_layers=4))
        c.init_session_lora(r=4)
        # 4 层 × 2 模块 × 2 参数 = 16
        assert len(c._session_lora) == 16

    def test_session_lora_B_零初始化(self):
        base = TinyBase(n_layers=4, dim=8)
        c = ParameterComposer(base, CompositionConfig(n_layers=4))
        c.init_session_lora(r=4)
        for name, p in c._session_lora.items():
            if "lora_B" in name:
                assert p.data.abs().max() == 0.0


class TestPhiForward:
    """四种 Φ 的 forward 实现。"""

    def test_ADD_forward_不崩(self):
        """Level 0:Add(无门控)。"""
        base = TinyBase(n_layers=4, dim=8, vocab=20)
        c = ParameterComposer(base, CompositionConfig(
            n_layers=4, phi_type=PhiType.ADD))
        c.init_session_lora(r=4)
        x = torch.randint(0, 20, (2, 5))
        out = c(x)
        assert out.logits.shape == (2, 5, 20)

    def test_GATE_ADD_forward_不崩(self):
        """Level 1:Gate Add(层级门控)。"""
        base = TinyBase(n_layers=4, dim=8, vocab=20)
        c = ParameterComposer(base, CompositionConfig(
            n_layers=4, phi_type=PhiType.GATE_ADD))
        c.init_session_lora(r=4)
        x = torch.randint(0, 20, (2, 5))
        out = c(x)
        assert out.logits.shape == (2, 5, 20)

    def test_ATTENTION_forward_不崩(self):
        """Level 2:Attention。"""
        base = TinyBase(n_layers=4, dim=8, vocab=20)
        c = ParameterComposer(base, CompositionConfig(
            n_layers=4, phi_type=PhiType.ATTENTION, attn_dim=4))
        c.init_session_lora(r=4)
        x = torch.randint(0, 20, (2, 5))
        out = c(x)
        assert out.logits.shape == (2, 5, 20)

    def test_MLP_forward_不崩(self):
        """Level 3:Tiny MLP。"""
        base = TinyBase(n_layers=4, dim=8, vocab=20)
        c = ParameterComposer(base, CompositionConfig(
            n_layers=4, phi_type=PhiType.MLP))
        c.init_session_lora(r=4)
        x = torch.randint(0, 20, (2, 5))
        out = c(x)
        assert out.logits.shape == (2, 5, 20)

    def test_forward_with_labels_算loss(self):
        """有 labels 时算 loss。"""
        base = TinyBase(n_layers=4, dim=8, vocab=20)
        c = ParameterComposer(base, CompositionConfig(
            n_layers=4, phi_type=PhiType.GATE_ADD))
        c.init_session_lora(r=4)
        x = torch.randint(0, 20, (2, 5))
        labels = torch.randint(0, 20, (2, 5))
        labels[:, :2] = -100  # mask 前两个 token
        out = c(x, labels=labels)
        assert out.loss is not None
        assert out.loss.item() > 0

    def test_四种Φ_输出形状一致(self):
        """四种 Φ 都产出相同 shape 的 logits。"""
        base = TinyBase(n_layers=4, dim=8, vocab=20)
        x = torch.randint(0, 20, (2, 5))
        for phi in PhiType:
            c = ParameterComposer(base, CompositionConfig(
                n_layers=4, phi_type=phi, attn_dim=4))
            c.init_session_lora(r=4)
            out = c(x)
            assert out.logits.shape == (2, 5, 20), f"{phi} 输出 shape 错"


class TestGateInfo:
    """门控信息 + 可训参数。"""

    def test_get_composition_info(self):
        base = TinyBase(n_layers=4)
        c = ParameterComposer(base, CompositionConfig(n_layers=4))
        c.init_session_lora(r=4)
        info = c.get_composition_info()
        assert info["n_layers"] == 4
        assert "domain_gate_mean" in info
        assert "session_gate_last4" in info
        assert info["phi_type"] == "gate_add"  # 默认

    def test_可训参数含门控和LoRA(self):
        base = TinyBase(n_layers=4, dim=8)
        c = ParameterComposer(base, CompositionConfig(n_layers=4))
        c.init_session_lora(r=4)
        params = c.get_trainable_parameters()
        assert len(params) >= 17  # 门控 + LoRA

    def test_28层_真实配置(self):
        base = TinyBase(n_layers=28, dim=8)
        c = ParameterComposer(base, CompositionConfig(n_layers=28))
        assert c.gates.shape == (28, 3)
