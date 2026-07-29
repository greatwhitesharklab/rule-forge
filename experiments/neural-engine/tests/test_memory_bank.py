"""阶段 2 方向 C:MemoryBank 模块测试。

验证原生记忆模块的基本行为:
- 初始零注入(不影响 base 推理)
- TTT 更新后记忆激活(gate 增大,开始注入)
- attention 寻址(query 跟 key 匹配,检索相关 value)
- 快照可审计(能看到 key/value 内容)
"""

from __future__ import annotations

import pytest
import torch

from selflearn.memory_bank import MemoryBank


class TestMemoryBankInit:
    """初始化:记忆为空,不影响 base 推理。"""

    def test_小随机初始化_注入接近零(self):
        """小随机初始化(memory_keys/values * 0.02),初始注入接近零。

        不是精确零(小随机),但量级很小,不影响 base 推理。
        小随机是为了避免冷启动问题(零初始化导致无梯度)。
        """
        bank = MemoryBank(n_slots=4, key_dim=8, value_dim=8, query_dim=16)
        hidden = torch.randn(2, 5, 16)
        out = bank(hidden)
        # 注入应该很小(小随机 * sigmoid(0)=0.5)
        diff = (out - hidden).abs().max()
        assert diff < 0.5, f"初始注入应很小,实际 diff={diff}"

    def test_记忆槽数正确(self):
        bank = MemoryBank(n_slots=8, key_dim=4, value_dim=4, query_dim=16)
        assert bank.memory_keys.shape == (8, 4)
        assert bank.memory_values.shape == (8, 4)

    def test_输出形状不变(self):
        bank = MemoryBank(n_slots=4, key_dim=8, value_dim=8, query_dim=16)
        hidden = torch.randn(2, 5, 16)
        out = bank(hidden)
        assert out.shape == hidden.shape


class TestMemoryBankAttention:
    """attention 寻址:query 跟 key 匹配,检索 value。"""

    def test_非零记忆_注入改变_hidden(self):
        """给 memory_values 赋非零值,注入应该改变 hidden 最后一个 token。"""
        bank = MemoryBank(n_slots=4, key_dim=8, value_dim=8, query_dim=16)
        # 给 memory 赋非零值
        with torch.no_grad():
            bank.memory_values.normal_(0, 1)
            bank.gate.fill_(2.0)  # gate 大 -> sigmoid -> 强注入
        hidden = torch.randn(2, 5, 16)
        out = bank(hidden)
        # 最后一个 token 应该跟输入不同(被注入)
        assert not torch.allclose(out[:, -1:, :], hidden[:, -1:, :], atol=1e-4)
        # 前 4 个 token 不变(只注入最后一个)
        assert torch.allclose(out[:, :-1, :], hidden[:, :-1, :], atol=1e-6)

    def test_gate_控制注入强度(self):
        """gate=大 -> 强注入;gate=小 -> 弱注入。"""
        bank = MemoryBank(n_slots=4, key_dim=8, value_dim=8, query_dim=16)
        with torch.no_grad():
            bank.memory_values.normal_(0, 1)

        hidden = torch.randn(1, 3, 16)

        # gate 小(弱注入)
        with torch.no_grad():
            bank.gate.fill_(-5.0)  # sigmoid(-5)~0.007
        out_weak = bank(hidden)

        # gate 大(强注入)
        with torch.no_grad():
            bank.gate.fill_(5.0)  # sigmoid(5)~0.993
        out_strong = bank(hidden)

        # 强注入的偏离更大
        diff_weak = (out_weak[:, -1:, :] - hidden[:, -1:, :]).abs().sum()
        diff_strong = (out_strong[:, -1:, :] - hidden[:, -1:, :]).abs().sum()
        assert diff_strong > diff_weak


class TestMemoryBankUpdate:
    """TTT 更新:记忆内容进梯度。"""

    def test_记忆参数可训练(self):
        """memory_keys/values/gate 的 requires_grad=True(可 TTT 更新)。"""
        bank = MemoryBank(n_slots=4, key_dim=8, value_dim=8, query_dim=16)
        assert bank.memory_keys.requires_grad
        assert bank.memory_values.requires_grad
        assert bank.gate.requires_grad

    def test_梯度回传到记忆(self):
        """forward -> loss -> backward,梯度应该流到 memory_values。"""
        bank = MemoryBank(n_slots=4, key_dim=8, value_dim=8, query_dim=16)
        with torch.no_grad():
            bank.memory_values.normal_(0, 1)
            bank.gate.fill_(1.0)
        hidden = torch.randn(1, 3, 16)
        out = bank(hidden)
        loss = out.sum()
        loss.backward()
        # memory_values 应该有梯度
        assert bank.memory_values.grad is not None
        assert bank.memory_values.grad.abs().sum() > 0


class TestMemoryBankSnapshot:
    """快照:记忆内容可审计。"""

    def test_快照含_keys_values_gate(self):
        bank = MemoryBank(n_slots=4, key_dim=8, value_dim=8, query_dim=16)
        snap = bank.get_memory_snapshot()
        assert "keys" in snap
        assert "values" in snap
        assert "gate" in snap
        assert snap["keys"].shape == (4, 8)
        assert isinstance(snap["gate"], float)

    def test_快照反映更新后的记忆(self):
        """TTT 更新后,快照应该反映新的 memory_values。"""
        bank = MemoryBank(n_slots=4, key_dim=8, value_dim=8, query_dim=16)
        old_snap = bank.get_memory_snapshot()
        # 手动改 memory_values
        with torch.no_grad():
            bank.memory_values.fill_(1.0)
        new_snap = bank.get_memory_snapshot()
        # 快照应该不同
        assert not torch.equal(old_snap["values"], new_snap["values"])
