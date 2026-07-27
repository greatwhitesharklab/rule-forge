"""策略知识记忆:复用 slots 包(不改包),内容 = 特征工程策略。

三类槽位:
- 字段槽(启动初始化):24 个白名单字段各一条,value_text=字段陈述,
  status=active —— "我们有什么原料";
- 特征槽(验证通过):value_text=特征陈述(name + 假设 + provenance),
  status=shadow(§8.5 ⑤影子服役,晋升门之后转 active 是后续阶段的事);
- 死路槽(验证失败):value_text=方向陈述 + 死因,status=retired ——
  §8.4 死路档案,死因是下一轮出题的约束条件。

检索走 encoder:key_vec(语义键)默认 hash_encode(fake,确定性),
真 encoder(Qwen3-Embedding)可经 encode_fn 注入,签名 (texts, dim) -> vecs。
死路不进经验检索(死路由 dead_end_list 单独进 prompt 的 DEAD-END 段)。
"""

from __future__ import annotations

from typing import Callable, Sequence

import numpy as np

from embed.fake import hash_encode
from slots import Slot, SlotService

EncodeFn = Callable[[Sequence[str], int], np.ndarray]

# §8.4 死路档案死因四类 + 工程类补充(沙箱/泄漏/命名)。
CAUSE_DISCRIMINATION = "区分度不足"
CAUSE_REGIME = "regime 不稳"
CAUSE_COVERAGE = "覆盖率萎缩"
CAUSE_REPLACED = "被更优替代"
CAUSE_SANDBOX = "沙箱失败"
CAUSE_LEAK = "泄漏嫌疑"
CAUSE_NAME = "命名冲突"


class StrategyMemory:
    """slots 之上的特征工程策略知识层;所有写入经 SlotService(WAL 可重放)。"""

    def __init__(self, service: SlotService, encode_fn: EncodeFn | None = None) -> None:
        self.service = service
        self._encode: EncodeFn = encode_fn or hash_encode

    def _vecs(self, text: str) -> tuple[np.ndarray, np.ndarray]:
        key = np.asarray(
            self._encode([text], self.service.cfg.key_dim), dtype=np.float32
        )[0]
        value = np.asarray(
            self._encode([text], self.service.cfg.value_dim), dtype=np.float32
        )[0]
        return key, value

    # ------------------------------------------------------------------ 初始化

    def init_field_slots(self, field_statements: dict[str, str]) -> int:
        """白名单字段槽初始化(幂等):每个字段一条 active 槽。

        ``field_statements``: {字段名: 决策时刻可得性陈述}(生产:lending.prepare
        .FEATURE_COLS,其中文注释即字段陈述)。返回新写入的槽数。
        """
        existing = {
            s.value_text.split(" — ", 1)[0]
            for s in self.service.store.all_slots()
            if s.value_text.startswith("字段:")
        }
        n = 0
        for name, statement in field_statements.items():
            marker = f"字段:{name}"
            if marker in existing:
                continue
            text = f"{marker} — {statement}"
            key, value = self._vecs(text)
            slot = self.service.allocate(key, value, text, case_id=marker,
                                         regime_tag="schema")
            self.service.set_status(slot.slot_id, "active")
            n += 1
        return n

    # ------------------------------------------------------------------ 写入

    def add_feature_slot(
        self, name: str, statement: str, *, provenance: str, regime_tag: str = ""
    ) -> Slot:
        """验证通过的新特征:shadow 槽(§8.5 ⑤影子服役起点)。"""
        text = f"特征:{name} — {statement}(provenance: {provenance})"
        key, value = self._vecs(text)
        slot = self.service.allocate(key, value, text, case_id=f"特征:{name}",
                                     regime_tag=regime_tag)
        self.service.set_status(slot.slot_id, "shadow")
        return self.service.store.get_slot(slot.slot_id)

    def add_dead_end(self, name: str, statement: str, *, cause: str) -> Slot:
        """失败方向:retired 死路槽,value_text 必含死因(§8.4)。"""
        text = f"死路:{name} — {statement};死因:{cause}"
        key, value = self._vecs(text)
        slot = self.service.allocate(key, value, text, case_id=f"死路:{name}")
        self.service.set_status(slot.slot_id, "retired")
        return self.service.store.get_slot(slot.slot_id)

    # ------------------------------------------------------------------ 检索

    def experience_summaries(self, query_text: str, k: int = 5) -> list[str]:
        """语义检索相关经验(active/shadow 槽的 value_text,余弦 top-k)。"""
        if not query_text.strip():
            return []
        query = self._vecs(query_text)[0]
        hits = self.service.store.search(query, k=k * 4)  # 多取候选再按状态过滤
        out: list[str] = []
        for slot_id, _score in hits:
            slot = self.service.store.get_slot(slot_id)
            if slot is not None and slot.status in ("active", "shadow"):
                out.append(slot.value_text)
                if len(out) >= k:
                    break
        return out

    def dead_end_list(self) -> list[str]:
        """死路清单(retired 槽 value_text,写入序)—— 出题的去重约束。"""
        return [
            s.value_text for s in self.service.store.all_slots()
            if s.status == "retired"
        ]

    def close(self) -> None:
        self.service.close()
