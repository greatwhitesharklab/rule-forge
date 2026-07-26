"""LendingMemory batch-path tests.

LendingMemory 是 SlotService 的批量化运行壳:检索用批量 FAISS + 槽状态
缓存(读路径),写入/声誉回流仍走 SlotService 原语(写路径)。这些测试把
批量路径与 SlotService 逐条路径钉死等价,并覆盖无命中回退。
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from embed.fake import hash_encode
from eval.lending_canon import value_text
from eval.lending_memory import LendingMemory
from slots import SlotConfig, SlotService


def _svc(tmp_path) -> SlotService:
    return SlotService(tmp_path / "slots.db", SlotConfig())


def _night_rows(pairs: list[tuple[str, str]]) -> dict:
    """(canon, outcome) pairs -> nightly_write kwargs."""
    return {
        "canon": [p[0] for p in pairs],
        "outcomes": [p[1] for p in pairs],
        "emp": ["some employer"] * len(pairs),
        "case_ids": [f"c{i}" for i in range(len(pairs))],
        "episode": "2008-01",
    }


class TestEnrichFallback:
    def test_empty_memory_returns_prior(self, tmp_path):
        mem = LendingMemory(_svc(tmp_path))
        mem.set_prior(0.17)
        feats = mem.enrich(["等级:A;期限:36月"], ["q1"])
        assert feats.shape == (1, 3)
        assert feats[0, 0] == pytest.approx(0.17)  # memory_bad_rate = 全局先验
        assert feats[0, 1] == 0.0  # memory_hit_count
        assert feats[0, 2] == 0.0  # memory_max_w

    def test_unknown_profile_returns_prior(self, tmp_path):
        mem = LendingMemory(_svc(tmp_path))
        mem.set_prior(0.2)
        mem.nightly_write(**_night_rows([("等级:A", "good")]))
        feats = mem.enrich(["等级:G"], ["q1"])  # hash 编码下近似正交,不命中
        assert feats[0, 0] == pytest.approx(0.2)
        assert feats[0, 1] == 0.0


class TestEnrichParityWithService:
    def test_batch_matches_per_case_retrieve(self, tmp_path):
        svc = _svc(tmp_path)
        mem = LendingMemory(svc)
        pairs = [("等级:A", "good"), ("等级:B", "bad"), ("等级:B", "good")]
        mem.nightly_write(**_night_rows(pairs))
        texts = ["等级:A", "等级:B", "等级:C"]
        feats = mem.enrich(texts, ["q0", "q1", "q2"])

        for i, text in enumerate(texts):
            key = hash_encode([text], 256)[0]
            # Embedder 归一化与 store.search 一致;直接走 service 逐条路径
            key = key / np.linalg.norm(key)
            hits = svc.retrieve(key, case_id=f"ref{i}")
            if not hits:
                assert feats[i, 1] == 0.0
                continue
            w = np.array([hw for _s, hw in hits])
            rep_bad = np.array(
                [s.beta_b / (s.beta_a + s.beta_b) for s, _w in hits]
            )
            assert feats[i, 1] == pytest.approx(float(len(hits)))
            assert feats[i, 2] == pytest.approx(float(w.max()), abs=1e-6)
            assert feats[i, 0] == pytest.approx(
                float((w * rep_bad).sum() / w.sum()), abs=1e-6
            )

    def test_known_beta_weighted_mean(self, tmp_path):
        """两条同 profile 的槽(一好一坏 compete)按 w 加权。"""
        svc = _svc(tmp_path)
        mem = LendingMemory(svc)
        mem.nightly_write(**_night_rows([("等级:D", "good")]))
        # 手动把槽的 beta 调成已知值,再 enrich
        slot = svc.store.all_slots()[0]
        svc.store.update_betas(slot.slot_id, 3.0, 9.0)  # rep_bad = 0.75
        mem.resync_slot(slot.slot_id)
        feats = mem.enrich(["等级:D"], ["q1"])
        assert feats[0, 1] == pytest.approx(1.0)
        assert feats[0, 0] == pytest.approx(0.75, abs=1e-6)


class TestCreditParity:
    def test_cache_matches_store_after_credit(self, tmp_path):
        svc = _svc(tmp_path)
        mem = LendingMemory(svc)
        mem.nightly_write(**_night_rows([("等级:E", "good"), ("等级:F", "bad")]))
        mem.enrich(["等级:E", "等级:F", "等级:E"], ["q0", "q1", "q2"])
        mem.nightly_credit(["q0", "q1", "q2"], ["good", "bad", "bad"])
        for slot in svc.store.all_slots():
            ba, bb = mem.beta_of(slot.slot_id)
            assert ba == pytest.approx(slot.beta_a)
            assert bb == pytest.approx(slot.beta_b)

    def test_credit_moves_reputation_in_outcome_direction(self, tmp_path):
        svc = _svc(tmp_path)
        mem = LendingMemory(svc)
        mem.nightly_write(**_night_rows([("等级:G", "good")]))
        before = mem.enrich(["等级:G"], ["q0"])[0, 0]
        mem.nightly_credit(["q0"], ["bad"])
        after = mem.enrich(["等级:G"], ["q1"])[0, 0]
        assert after > before


class TestWritePath:
    def test_allocate_then_reinforce_counts(self, tmp_path):
        svc = _svc(tmp_path)
        mem = LendingMemory(svc)
        ops = mem.nightly_write(
            **_night_rows([("等级:A", "good"), ("等级:A", "good"), ("等级:B", "bad")])
        )
        assert ops["allocate"] == 2  # 两个唯一 (text, outcome) 组
        assert ops["reinforce"] == 0  # 组内已去重,同晚同组只写一次
        assert mem.n_slots == 2
        # 第二晚同 profile -> reinforce
        ops2 = mem.nightly_write(**_night_rows([("等级:A", "good")]))
        assert ops2["reinforce"] == 1

    def test_slot_episode_tracking(self, tmp_path):
        mem = LendingMemory(_svc(tmp_path))
        mem.nightly_write(**_night_rows([("等级:A", "good")]))
        eps = mem.slot_episodes()
        assert set(eps.values()) == {"2008-01"}

    def test_value_text_has_outcome_and_emp(self, tmp_path):
        svc = _svc(tmp_path)
        mem = LendingMemory(svc)
        mem.nightly_write(**_night_rows([("等级:C", "bad")]))
        slot = svc.store.all_slots()[0]
        assert "结局:违约" in slot.value_text
        assert "雇主:some employer" in slot.value_text
