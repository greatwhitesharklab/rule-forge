"""Session PM 可写参数记忆三测(eval/memory_triad.py)测试。

tiny 模型(tests/_tiny.py)跑全逻辑;真模型冒烟打 slow_model。
"""

from __future__ import annotations

import numpy as np
import pytest
import torch

from _tiny import FakeTokenizer, make_tiny_qwen3

from eval.memory_triad import (
    CONCLUSIONS,
    ExperienceItem,
    attach_session_pm,
    build_experience_bank,
    completion_logprob,
    item_prompt,
    plot_adaptation,
    plot_forgetting,
    randomize_pm,
    recall_accuracy,
    recovery_rounds,
    score_candidates,
    write_items,
    zero_pm,
)
from selflearn.ttt import TTTConfig

VOCAB = 1000


@pytest.fixture()
def tiny_pm():
    """tiny Qwen3 + FakeTokenizer + 已挂 Session PM(LoRA r=4)。"""
    model = make_tiny_qwen3(vocab_size=VOCAB)
    pm, opt = attach_session_pm(model, r=4, lr=1e-2)
    tok = FakeTokenizer(VOCAB)
    return pm, opt, tok


class TestExperienceBank:
    """经验库:4 批 × 8 条,结论组内均衡、映射对基座不可推断。"""

    def test_形状与主题(self):
        bank = build_experience_bank()
        assert len(bank) == 4
        assert all(len(b) == 8 for b in bank)
        for theme, batch in enumerate(bank):
            assert all(it.theme == theme for it in batch)

    def test_结论每批四候选各两次(self):
        bank = build_experience_bank()
        for batch in bank:
            counts = {c: sum(1 for it in batch if it.conclusion == c)
                      for c in CONCLUSIONS}
            assert all(v == 2 for v in counts.values())

    def test_结论指派是打散的_非语义直白(self):
        """同一结论出现在不同语义模式上(随机指派,防基座蒙对)。"""
        bank = build_experience_bank(seed=1)
        batch0 = bank[0]
        # 8 条模式两两不同,且结论不是全部相同
        assert len({it.pattern for it in batch0}) == 8
        assert len({it.conclusion for it in batch0}) == 4

    def test_种子决定论(self):
        a = build_experience_bank(seed=7)
        b = build_experience_bank(seed=7)
        assert a == b


class TestScoring:
    """candidate scoring:mean token logprob,可判定。"""

    def test_分数是有限负数(self, tiny_pm):
        pm, _, tok = tiny_pm
        scores = score_candidates(pm, tok, item_prompt("多头借贷≥3 且查询密集"))
        assert len(scores) == len(CONCLUSIONS)
        assert all(s != s for s in scores) is False  # no NaN
        assert all(s < 0 for s in scores)

    def test_recall_accuracy_返回命中明细(self, tiny_pm):
        pm, _, tok = tiny_pm
        items = build_experience_bank()[0][:4]
        acc, details = recall_accuracy(pm, tok, items)
        assert 0.0 <= acc <= 1.0
        assert len(details) == 4
        assert all(d["pick"] in CONCLUSIONS for d in details)

    def test_上下文携带改变打分(self, tiny_pm):
        pm, _, tok = tiny_pm
        items = build_experience_bank()[0][:2]
        _, d_no = recall_accuracy(pm, tok, items)
        _, d_ctx = recall_accuracy(pm, tok, items, context="信贷经验:x → 违约倾向高")
        assert d_no[0]["scores"] != d_ctx[0]["scores"]


class TestPMArms:
    """PM 臂语义:零初始化 ≡ 基座;随机化 ≠ 基座。"""

    def test_零初始化PM_等价基座(self, tiny_pm):
        pm, _, tok = tiny_pm
        zero_pm(pm)
        prompt = item_prompt("多头借贷≥3 且查询密集")
        on = score_candidates(pm, tok, prompt)
        with pm.disable_adapter():
            off = score_candidates(pm, tok, prompt)
        assert on == pytest.approx(off, abs=1e-5)

    def test_随机化PM_偏离基座(self, tiny_pm):
        pm, _, tok = tiny_pm
        prompt = item_prompt("多头借贷≥3 且查询密集")
        with pm.disable_adapter():
            off = score_candidates(pm, tok, prompt)
        randomize_pm(pm, scale=0.05, seed=3)
        on = score_candidates(pm, tok, prompt)
        assert on != pytest.approx(off, abs=1e-5)


class TestWriteRecall:
    """TTT 写入后,被写结论的 logprob 相对提升(写入-召回通道)。"""

    def test_写入提升金标结论得分(self, tiny_pm):
        pm, opt, tok = tiny_pm
        zero_pm(pm)
        item = ExperienceItem(pattern="多头借贷≥3 且查询密集",
                              conclusion="违约倾向高", theme=0)
        prompt = item_prompt(item.pattern)
        before = completion_logprob(pm, tok, prompt, item.conclusion)
        cfg = TTTConfig(steps=5, lr=1e-2, lora_r=4, max_len=128)
        write_items(pm, tok, opt, [item], reward=1.0, config=cfg, epochs=2)
        after = completion_logprob(pm, tok, prompt, item.conclusion)
        assert after > before

    def test_写入后金标结论平均得分提升(self, tiny_pm):
        """tiny 随机模型上 argmax 召回本身噪声大,断通道级不变量:
        写入后各 item 金标结论的 mean logprob 平均严格上升。"""
        pm, opt, tok = tiny_pm
        zero_pm(pm)
        items = build_experience_bank()[0][:4]
        cfg = TTTConfig(steps=5, lr=1e-2, lora_r=4, max_len=128)
        before = float(np.mean([
            completion_logprob(pm, tok, item_prompt(it.pattern), it.conclusion)
            for it in items
        ]))
        write_items(pm, tok, opt, items, reward=1.0, config=cfg, epochs=2)
        after = float(np.mean([
            completion_logprob(pm, tok, item_prompt(it.pattern), it.conclusion)
            for it in items
        ]))
        assert after > before


class TestRecoveryRounds:
    """测 3 恢复轮数纯函数。"""

    def test_立即恢复(self):
        series = [0.5, 0.5, 0.46, 0.5, 0.5]
        rec = recovery_rounds(series, switch_rounds=[3], rounds=5)
        assert rec[0]["switch_round"] == 3
        # pre = mean(rounds 1..2)=0.5,thr=0.45;round 3 的 0.46 >= 0.45 → 1 轮
        assert rec[0]["recovery_rounds"] == 1
        assert rec[0]["recovered"]

    def test_延迟恢复(self):
        series = [0.5, 0.5, 0.1, 0.2, 0.5]
        rec = recovery_rounds(series, switch_rounds=[3], rounds=5)
        assert rec[0]["recovery_rounds"] == 3  # round 5 才 >= 0.45

    def test_未恢复记剩余轮数(self):
        series = [0.5, 0.5, 0.1, 0.1, 0.1]
        rec = recovery_rounds(series, switch_rounds=[3], rounds=5)
        assert rec[0]["recovery_rounds"] == 3
        assert not rec[0]["recovered"]

    def test_跳过无效切换(self):
        series = [0.5, 0.5, 0.5]
        # r_s=1 无切换前基线;r_s=3 == rounds 无观察窗
        assert recovery_rounds(series, switch_rounds=[1, 3], rounds=3) == []

    def test_pre为零跳过(self):
        series = [0.0, 0.0, 0.3, 0.4]
        assert recovery_rounds(series, switch_rounds=[3], rounds=4) == []


class TestPlots:
    """图产物:tiny 数据能画、文件落盘。"""

    def test_forgetting_图(self, tmp_path):
        report = {
            "k_batches": 2, "batch_size": 2,
            "matrix_batch_x_timestep": [[0.5, 0.25], [None, 0.75]],
            "base_recall_per_batch": [0.25, 0.25],
            "context_carry": {"recall_per_batch": [1.0, 1.0],
                              "context_tokens": 100},
        }
        out = tmp_path / "forgetting.png"
        plot_forgetting(report, out)
        assert out.exists() and out.stat().st_size > 0

    def test_adaptation_图(self, tmp_path):
        report = {
            "rounds": 4, "switch_rounds": [2],
            "arms": {a: {"series": [{"avg_iv": 0.1 * (i + 1)} for i in range(4)]}
                     for a in "ABC"},
        }
        out = tmp_path / "adaptation.png"
        plot_adaptation(report, out)
        assert out.exists() and out.stat().st_size > 0


@pytest.mark.slow_model
class TestRealModelWriteRecall:
    """真模型冒烟:TTT 写入后 written-set 召回 >= 基座(slow_model)。

    跑:uv run pytest experiments/neural-engine/tests/test_memory_triad.py -m slow_model
    """

    def test_真模型写入提升召回(self):
        import os
        os.environ.setdefault("HF_HUB_OFFLINE", "1")
        os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
        os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")
        from llm.local_llm import LocalLLM

        llm = LocalLLM("Qwen/Qwen3-0.6B", device="cpu")
        llm.load()
        cfg = TTTConfig(steps=3, lr=3e-4, lora_r=8, max_len=256)
        pm, opt = attach_session_pm(llm._model, r=cfg.lora_r, lr=cfg.lr)
        zero_pm(pm)
        tok = llm._tokenizer
        items = build_experience_bank()[0][:6]
        with pm.disable_adapter():
            acc_base, _ = recall_accuracy(pm, tok, items)
        write_items(pm, tok, opt, items, reward=1.0, config=cfg, epochs=3)
        acc_pm, _ = recall_accuracy(pm, tok, items)
        assert acc_pm >= acc_base
        assert acc_pm > 0.0
