"""LendingMemory: SlotService 的批量化运行壳(L3 记忆特征的生产路径)。

为什么需要这层壳:逐条走 SlotService.retrieve() 在 114 万案例上需要
~15 分钟(FAISS 单查询开销 + 每 hit 一次 sqlite commit + WAL flush),
超出端到端 20 分钟预算。本类把**读路径**批量化:

- enrich():整 episode 一次 FAISS batch search,双向门公式
  ``w = a_sem * a_tmp**GAMMA > THETA`` 用槽状态缓存向量化计算,
  产出三个 L3 特征(memory_bad_rate / memory_hit_count / memory_max_w);
  attribution 行照常写入 sqlite(credit_assignment 的唯一事实源),
  但省略 attribution 的 WAL 行(见「保真度取舍」)。

**写路径不做任何重写**:allocate / reinforce / compete / credit_assignment
全部调用 SlotService 原语,Beta 声誉、compete 冲突规则、WAL 记录与原
实现完全一致;缓存只是这些原语结果的镜像(单进程内,无并发漂移)。

保真度取舍(显式声明):
1. attribution 的 WAL 行省略 — WAL 重放会丢失 credit 目标;DB 内状态
   (slots 表 beta / attribution 表)是精确的,实验不回放 WAL。
2. retrieve 的 touch(use_count/last_used_at)省略 — 纯审计字段,不影响
   任何门控/声誉/特征。
3. 每晚写入按 (canonical, outcome) 分组,同组一晚写一次(首个案例为
   代表);逐案例写入只改变 value_vec EMA 轨迹与 provenance 长度,不改变
   声誉语义,而三个 L3 特征只依赖声誉与门控权重。

等价性由 tests/test_lending_eval_memory.py 钉死:批量 enrich 与逐条
service.retrieve 的命中槽/权重一致;credit 后缓存 beta 与 store 一致。
"""

from __future__ import annotations

import os
from collections import deque
from datetime import datetime, timezone
from typing import Sequence

import faiss
import numpy as np

from embed.fake import hash_encode
from slots import SlotService
from slots.service import Outcome

from eval.lending_canon import value_text

# 读路径批量检索的 FAISS 线程数(单查询路径已由批量取代,不怕 per-call 开销)
faiss.omp_set_num_threads(min(8, os.cpu_count() or 1))


class LendingMemory:
    """Batch read-path facade over a SlotService for LendingClub episodes."""

    def __init__(self, service: SlotService, encode_fn=None) -> None:
        self.service = service
        self.cfg = service.cfg
        self._encode_fn = encode_fn or hash_encode
        # sqlite 热路径调优(只配置连接,不改包代码):关掉 fsync 级保证,
        # 实验 DB 是一次性产物;attribution/events 查询加索引避免全表扫。
        conn = service.store.conn
        conn.execute("PRAGMA journal_mode=MEMORY")
        conn.execute("PRAGMA synchronous=OFF")
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_attr_case ON attribution(case_id)"
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_events_slot ON slot_events(slot_id, id)"
        )
        # 去重嵌入缓存:canonical 组合空间有界(全量 ~23k),每文本编码一次。
        self._key_cache: dict[str, np.ndarray] = {}
        self._val_cache: dict[str, np.ndarray] = {}
        # 槽状态缓存(slot_id 从 1 开始 dense;下标 0 留空)。
        self._beta_a: list[float] = [0.0]
        self._beta_b: list[float] = [0.0]
        self._ev_sum: list[float] = [0.0]  # 近 a_tmp_window 个结局事件和
        self._ev_len: list[int] = [0]
        self._events: list[deque[int]] = [deque()]
        self._slot_episode: dict[int, str] = {}
        # 决策时 attribution(案例 -> [(slot_id, alpha)]),供 credit 后同步缓存;
        # 案例成熟被 credit 后立即删除,驻留规模 ~LAG 个 episode。
        self._pending_attr: dict[str, list[tuple[int, float]]] = {}
        self._prior = self.cfg.prior_bad_rate

    # ------------------------------------------------------------------ config

    def set_prior(self, bad_rate: float) -> None:
        """滚动全局 bad rate:无命中回退值 + 新槽 Beta 先验(P1.1)。"""
        self._prior = float(bad_rate)
        self.service.set_outcome_prior(bad_rate)

    @property
    def prior_bad_rate(self) -> float:
        return self._prior

    @property
    def n_slots(self) -> int:
        return len(self._slot_episode)

    # ------------------------------------------------------------------- embed

    def _keys(self, texts: Sequence[str]) -> np.ndarray:
        missing = [t for t in dict.fromkeys(texts) if t not in self._key_cache]
        if missing:
            vecs = np.asarray(
                self._encode_fn(missing, self.cfg.key_dim), dtype=np.float32
            )
            norms = np.linalg.norm(vecs, axis=1, keepdims=True)
            norms[norms == 0.0] = 1.0
            for t, v in zip(missing, vecs / norms):
                self._key_cache[t] = v
        return np.ascontiguousarray(
            np.stack([self._key_cache[t] for t in texts]), dtype=np.float32
        )

    def _value_vec(self, text: str) -> np.ndarray:
        v = self._val_cache.get(text)
        if v is None:
            vec = np.asarray(
                self._encode_fn([text], self.cfg.value_dim), dtype=np.float32
            )[0]
            n = np.linalg.norm(vec)
            v = vec / (n if n else 1.0)
            self._val_cache[text] = v
        return v

    # ----------------------------------------------------------------- read path

    def _a_tmp_arr(self) -> np.ndarray:
        ev_sum = np.asarray(self._ev_sum)
        ev_len = np.asarray(self._ev_len, dtype=np.float64)
        return (ev_sum + 1.0) / (ev_len + 2.0)

    def enrich(self, texts: Sequence[str], case_ids: Sequence[str]) -> np.ndarray:
        """三个 L3 记忆特征 [N,3]:memory_bad_rate / hit_count / max_w。

        无命中回退:bad_rate=全局先验,hit_count=0,max_w=0。
        语义与 SlotService.retrieve 完全一致(测试钉死),只是批量计算。
        """
        n = len(texts)
        out = np.zeros((n, 3), dtype=np.float64)
        out[:, 0] = self._prior
        index = self.service.store.index
        if n == 0 or index.ntotal == 0:
            return out
        k = min(self.cfg.retrieve_k, index.ntotal)
        keys = self._keys(texts)
        sims, ids = index.search(keys, k)
        ids = ids.astype(np.int64)
        valid = ids >= 0
        safe_ids = np.where(valid, ids, 0)
        beta_a = np.asarray(self._beta_a)
        beta_b = np.asarray(self._beta_b)
        a_tmp = self._a_tmp_arr()[safe_ids]
        w = np.where(valid, sims, 0.0) * a_tmp**self.cfg.gamma_exp
        hit = valid & (w > self.cfg.theta)
        rep_bad = beta_b[safe_ids] / (beta_a[safe_ids] + beta_b[safe_ids])
        wh = np.where(hit, w, 0.0)
        wsum = wh.sum(axis=1)
        has = wsum > 0.0
        out[has, 0] = (wh * rep_bad).sum(axis=1)[has] / wsum[has]
        out[:, 1] = hit.sum(axis=1)
        out[has, 2] = wh.max(axis=1)[has]
        # attribution 落库(credit_assignment 的事实源);alpha 与 P1.1 同为
        # reputation-free 权重 w。省略 WAL/touch(见模块 docstring)。
        store = self.service.store
        ts = datetime.now(timezone.utc).isoformat()
        rows, cols = np.nonzero(hit)
        for r, c in zip(rows, cols):
            slot_id = int(ids[r, c])
            weight = float(w[r, c])
            cid = case_ids[r]
            store.add_attribution(
                cid, slot_id, weight, float(sims[r, c]),
                float(1.0 - rep_bad[r, c]),  # a_rep: 声誉(审计用,不进信用权重)
                float(a_tmp[r, c]), ts,
            )
            self._pending_attr.setdefault(cid, []).append((slot_id, weight))
        return out

    # ---------------------------------------------------------------- write path

    def nightly_write(
        self,
        canon: Sequence[str],
        outcomes: Sequence[Outcome],
        emp: Sequence[str],
        case_ids: Sequence[str],
        episode: str,
    ) -> dict[str, int]:
        """成熟案例写入(夜间):按 (canonical, outcome) 分组,走 service 原语。

        写槽时 emp_title_norm 作为 value_text 附注保留(不进检索 key)。
        """
        groups: dict[tuple[str, str], tuple[str, str]] = {}
        for text, oc, e, cid in zip(canon, outcomes, emp, case_ids):
            groups.setdefault((text, oc), (e or "", cid))
        texts_g = [k[0] for k in groups]
        keys = self._keys(texts_g) if texts_g else np.zeros((0, self.cfg.key_dim))
        # 批量 k=1 近邻,然后逐组走 allocate/reinforce/compete 原语。
        index = self.service.store.index
        nn_id = np.full(len(groups), -1, dtype=np.int64)
        nn_sim = np.zeros(len(groups))
        if len(groups) and index.ntotal:
            sims, ids = index.search(keys, 1)
            nn_id = ids[:, 0].astype(np.int64)
            nn_sim = sims[:, 0]
        ops = {"allocate": 0, "reinforce": 0, "compete": 0}
        for i, ((text, oc), (e, rep_cid)) in enumerate(groups.items()):
            vtext = value_text(text, oc, e)
            vvec = self._value_vec(vtext)
            slot = None
            if nn_id[i] >= 0 and nn_sim[i] > self.cfg.sim_threshold:
                slot = self.service.store.get_slot(int(nn_id[i]))
            if slot is None:
                new = self.service.allocate(
                    keys[i], vvec, vtext, rep_cid, regime_tag=episode
                )
                self._register_slot(new, episode)
                ops["allocate"] += 1
            elif self.service.conflicts(slot, oc):
                new = self.service.compete(
                    slot.slot_id, keys[i], vvec, vtext, rep_cid, regime_tag=episode
                )
                self._register_slot(new, episode)
                ops["compete"] += 1
            else:
                self.service.reinforce(slot.slot_id, vvec, rep_cid)
                ops["reinforce"] += 1
        return ops

    def _register_slot(self, slot, episode: str) -> None:
        sid = slot.slot_id
        while len(self._beta_a) <= sid:
            self._beta_a.append(0.0)
            self._beta_b.append(0.0)
            self._ev_sum.append(0.0)
            self._ev_len.append(0)
            self._events.append(deque())
        self._beta_a[sid] = slot.beta_a
        self._beta_b[sid] = slot.beta_b
        self._slot_episode[sid] = episode

    def nightly_credit(
        self, case_ids: Sequence[str], outcomes: Sequence[Outcome]
    ) -> int:
        """成熟结局回流:走 service.credit_assignment(DB+WAL),再同步缓存。"""
        n = 0
        for cid, oc in zip(case_ids, outcomes):
            self.service.credit_assignment(cid, oc)
            for slot_id, alpha in self._pending_attr.pop(cid, []):
                if oc == "good":
                    self._beta_a[slot_id] += alpha
                else:
                    self._beta_b[slot_id] += alpha
                ev = self._events[slot_id]
                ev.append(1 if oc == "good" else 0)
                while len(ev) > self.cfg.a_tmp_window:
                    ev.popleft()
                self._ev_sum[slot_id] = float(sum(ev))
                self._ev_len[slot_id] = len(ev)
            n += 1
        return n

    # -------------------------------------------------------------------- tests

    def beta_of(self, slot_id: int) -> tuple[float, float]:
        return self._beta_a[slot_id], self._beta_b[slot_id]

    def resync_slot(self, slot_id: int) -> None:
        """缓存绕路更新(测试/外部直接改 store 后)重新镜像一个槽。"""
        slot = self.service.store.get_slot(slot_id)
        self._beta_a[slot_id] = slot.beta_a
        self._beta_b[slot_id] = slot.beta_b

    def slot_episodes(self) -> dict[int, str]:
        return dict(self._slot_episode)
