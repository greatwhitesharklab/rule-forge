"""阶段 2 方向 A:TTT 在线更新测试。"""

from __future__ import annotations

import pytest
import torch
import torch.nn as nn

from selflearn.ttt import TTTConfig, _encode_pair, ttt_step, setup_ttt_lora


class FakeTokenizer:
    """假 tokenizer:把字符串转成 char IDs(简单但够测)。"""

    def __call__(self, text: str, return_tensors: str = "pt", **kwargs):
        ids = [ord(c) % 1000 for c in text]  # char -> int
        if return_tensors == "pt":
            return {"input_ids": torch.tensor([ids])}
        return {"input_ids": ids}


class TinyLM(nn.Module):
    """极小测试模型(vocab=1000, 1 层)。"""

    def __init__(self, vocab=1000, dim=16):
        super().__init__()
        self.embed = nn.Embedding(vocab, dim)
        self.linear = nn.Linear(dim, vocab)

    def forward(self, input_ids, labels=None, **kwargs):
        h = self.embed(input_ids)
        logits = self.linear(h)
        loss = None
        if labels is not None:
            # shift for causal LM
            shift_logits = logits[:, :-1, :].contiguous()
            shift_labels = labels[:, 1:].contiguous()
            mask = shift_labels != -100
            if mask.any():
                loss = nn.functional.cross_entropy(
                    shift_logits[mask], shift_labels[mask]
                )
        return type("Out", (), {"loss": loss, "logits": logits})()


class TestEncodePair:
    """_encode_pair:prompt + completion 编码 + label masking。"""

    def test_prompt_部分_label_负100(self):
        tok = FakeTokenizer()
        out = _encode_pair(tok, "hello", "world", max_len=100)
        # Then: labels 前半(prompt)是 -100
        prompt_len = len("hello")
        labels = out["labels"][0].tolist()
        assert all(l == -100 for l in labels[:prompt_len])

    def test_completion_部分_label_有值(self):
        tok = FakeTokenizer()
        out = _encode_pair(tok, "ab", "cd", max_len=100)
        labels = out["labels"][0].tolist()
        # completion 部分(后 2 个)应该是 char codes,不是 -100
        assert labels[-1] != -100
        assert labels[-2] != -100

    def test_截断_保留尾部(self):
        tok = FakeTokenizer()
        out = _encode_pair(tok, "a" * 100, "b" * 100, max_len=50)
        # Then: 总长度 = 50
        assert out["input_ids"].shape[1] == 50


class TestTTTStep:
    """ttt_step:reward-weighted SFT 更新。"""

    def test_正_reward_更新_不崩(self):
        # Given
        model = TinyLM()
        tok = FakeTokenizer()
        opt = torch.optim.AdamW(model.parameters(), lr=1e-3)
        config = TTTConfig(steps=1)
        # When
        metrics = ttt_step(model, tok, opt, "hello", "world", reward=1.0, config=config)
        # Then: 返 metrics,不崩
        assert "sft_loss" in metrics
        assert "weighted_loss" in metrics
        assert metrics["reward"] == 1.0

    def test_负_reward_也能更新(self):
        # Given
        model = TinyLM()
        tok = FakeTokenizer()
        opt = torch.optim.AdamW(model.parameters(), lr=1e-3)
        config = TTTConfig(steps=1)
        # When: reward=-0.5
        metrics = ttt_step(model, tok, opt, "hello", "bad", reward=-0.5, config=config)
        # Then: weighted_loss 是负数(reward * sft_loss)
        assert metrics["weighted_loss"] < 0
        assert metrics["sft_loss"] > 0  # sft_loss 本身是正的

    def test_多步更新_loss_变化(self):
        """多步更新,sft_loss 应该变化(权重在动)。"""
        # Given
        model = TinyLM()
        tok = FakeTokenizer()
        opt = torch.optim.AdamW(model.parameters(), lr=1e-2)
        config = TTTConfig(steps=3)
        # When
        metrics = ttt_step(model, tok, opt, "hello", "world", reward=1.0, config=config)
        # Then: 记录的是最后一步的 loss(应该跟初始不同)
        assert metrics["sft_loss"] > 0  # 还是有 loss(没完美拟合)

    def test_零_reward_不更新_梯度零(self):
        """reward=0 时 weighted_loss=0,梯度零,权重不动。"""
        # Given
        model = TinyLM()
        before = dict(model.named_parameters())
        tok = FakeTokenizer()
        opt = torch.optim.AdamW(model.parameters(), lr=1e-3)
        config = TTTConfig(steps=1)
        # When
        ttt_step(model, tok, opt, "hello", "world", reward=0.0, config=config)
        # Then: 权重没变(梯度为零)
        for name, param in model.named_parameters():
            assert torch.equal(param.data, before[name].data), f"{name} 变了"


class TestSetupTTTLoRA:
    """setup_ttt_lora:挂 LoRA + optimizer。"""

    def test_挂_LoRA_后部分参数可训(self):
        """LoRA 挂载测试 -- mock get_peft_model 返真实 Parameter。"""
        from unittest.mock import patch, MagicMock
        model = MagicMock()
        model.peft_config = None
        config = TTTConfig(lora_r=4)

        # mock 返一个带真实 nn.Parameter 的假模型
        fake_param = torch.nn.Parameter(torch.randn(4, 4))
        fake_peft_model = MagicMock()
        fake_peft_model.parameters.return_value = iter([fake_param])
        with patch("peft.get_peft_model", return_value=fake_peft_model):
            opt = setup_ttt_lora(model, config)

        assert len(opt.param_groups) > 0
