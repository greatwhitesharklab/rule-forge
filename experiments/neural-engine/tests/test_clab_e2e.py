"""CLAB 发现力实验:小世界端到端跑通 + 保留池信息隔离。

信息隔离是机制一实验可信度的前提:闭环任何环节(出题 context、G1
briefing、候选表达式、记忆槽文本)都接触不到规则池内容 —— 保留池规则的
ID/文本/概念阈值绝不许出现在云端可见的任何字节里。判卷器是唯一的
ground truth 消费者。
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np

from eval.clab_discovery import DiscoveryConfig, run_discovery
from synth.rules import EXPERIENCE_RULES, HELDOUT_RULES

_TINY_LGBM = {
    "n_estimators": 20, "num_leaves": 7, "min_child_samples": 10,
    "learning_rate": 0.1, "verbose": -1, "n_jobs": 1,
}


def _cfg(tmp_path: Path, **kw) -> DiscoveryConfig:
    base = dict(
        episodes=10, per_episode=150, seed=42, dev_episodes=7,
        rounds=2, top_m=4, top_k=10, dev_holdout_episodes=2,
        lgbm_params=dict(_TINY_LGBM), skip_eval=True, n_null=20,
        out_dir=tmp_path,
    )
    base.update(kw)
    return DiscoveryConfig(**base)


class TestSmallWorldEndToEnd:
    def test_runs_and_produces_artifacts(self, tmp_path) -> None:
        res = run_discovery(_cfg(tmp_path))
        m = res.metrics

        # 迭代日志:2 轮,结构完整
        assert len(m["rounds"]) == 2
        for r in m["rounds"]:
            assert "round" in r and "accepted" in r

        # 保留池 10 条的发现清单:每条都有 found_round(int 或 None)
        heldout = m["heldout_discovery"]
        assert len(heldout) == 10
        assert [h["rule_id"] for h in heldout] == [
            f"HLD-{i:02d}" for i in range(1, 11)
        ]
        for h in heldout:
            assert h["found_round"] is None or isinstance(h["found_round"], int)

        # 假阳性计数与假阳性率
        assert m["false_positive_count"] >= 0
        assert 0.0 <= m["false_positive_rate"] <= 1.0
        # 阈值标定结果入档
        assert m["calibration"]["tau"] > 0

        # 产物:metrics json + 发现曲线图 + 迭代 jsonl
        assert (tmp_path / "clab_metrics.json").exists()
        assert (tmp_path / "clab_discovery_curve.png").exists()
        assert (tmp_path / "iteration_log.jsonl").exists()
        loaded = json.loads((tmp_path / "clab_metrics.json").read_text())
        assert loaded["heldout_discovery"] == heldout

    def test_accepted_features_are_graded(self, tmp_path) -> None:
        res = run_discovery(_cfg(tmp_path))
        for r in res.metrics["rounds"]:
            for a in r["accepted"]:
                assert a["category"] in (
                    "rediscovery", "known", "false_positive"
                )
                assert "iv" in a and "corr" in a


class TestHeldoutInformationIsolation:
    def test_cloud_context_never_sees_rule_pool(self, tmp_path) -> None:
        res = run_discovery(_cfg(tmp_path))
        assert res.cloud.seen_contexts, "闭环应当真的出了题"
        for ctx in res.cloud.seen_contexts:
            blob = json.dumps(ctx, ensure_ascii=False)
            for rule in (*EXPERIENCE_RULES, *HELDOUT_RULES):
                assert rule.rule_id not in blob
                assert rule.text not in blob
            for banned in ("heldout", "ground_truth", "保留池"):
                assert banned not in blob

    def test_candidate_expressions_never_encode_rules(self, tmp_path) -> None:
        res = run_discovery(_cfg(tmp_path))
        for c in res.cloud.candidates:
            for rule in (*EXPERIENCE_RULES, *HELDOUT_RULES):
                assert rule.rule_id not in c["expression"]
                assert rule.rule_id not in c["rationale"]

    def test_adapter_module_does_not_touch_ground_truth(self) -> None:
        """selflearn.clab 是闭环侧代码:源码层面就不许引用规则真值。"""
        src = Path(__file__).resolve().parent.parent / "selflearn" / "clab.py"
        text = src.read_text(encoding="utf-8")
        for banned in ("HELDOUT", "heldout", "ground_truth", ".truth",
                       "rule_fired", "build_rule_pool", "EXPERIENCE_RULES"):
            assert banned not in text, f"selflearn/clab.py 引用了 {banned}"
