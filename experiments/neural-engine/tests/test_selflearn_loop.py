"""闭环端到端测试(tiny 数据,mock 云端)+ 时间红线断言。

红线:迭代只准碰 dev 窗 —— 构造含 eval 窗 episode 的帧必须直接拒绝;
验证器只见剥离 outcome/episode 的 dev 帧。e2e:2 轮迭代,mock 云端第 1 轮
返回一个真有区分度的构造特征(入库 + 重训 AUC 上升路径)和一个常数垃圾
特征(进死路档案),第 2 轮返回空(无变化)。
"""

from __future__ import annotations

import json
import re

import numpy as np
import pandas as pd
import pytest

from cloud.contracts import Provenance, TaskResult
from selflearn.config import LoopConfig
from selflearn.loop import SelfLearnLoop
from selflearn.memory import StrategyMemory
from slots import SlotConfig, SlotService

DEV_EPISODES = [f"2013-{m:02d}" for m in range(1, 7)]  # 2013-01..2013-06


def _tiny_dev_df(per_episode: int = 120, seed: int = 9) -> pd.DataFrame:
    """dev 窗 tiny 帧:hidden_risk 驱动结局,但不在基础特征里(待云端发现)。"""
    rng = np.random.default_rng(seed)
    rows = []
    for ep in DEV_EPISODES:
        for _ in range(per_episode):
            hidden = rng.normal()
            dti = rng.uniform(0, 40)
            p_bad = 1.0 / (1.0 + np.exp(-(-0.5 + 1.8 * hidden + 0.01 * dti)))
            rows.append({
                "episode": ep,
                "outcome": int(rng.random() < p_bad),
                "loan_amnt": rng.uniform(1000, 35000),
                "int_rate": rng.uniform(5, 25),
                "annual_inc": rng.uniform(20000, 150000),
                "dti": dti,
                "hidden_risk": hidden,
                "grade": "C",
                "purpose": "debt_consolidation",
            })
    return pd.DataFrame(rows)


def _tiny_base_features(df: pd.DataFrame):
    """tiny 版基础特征(验收脚本用 eval.lending_acceptance.build_base_features)。"""
    cols = ["loan_amnt", "int_rate", "annual_inc", "dti"]
    return df[cols].to_numpy(dtype=np.float32), cols


class _StubCloud:
    """按轮次返回预定 feature_proposal 结果的 mock 云端。"""

    provider_name = "stub"
    model_name = "stub-1"

    def __init__(self, by_round: dict[int, list[dict]]) -> None:
        self._by_round = by_round
        self.seen_task_ids: list[str] = []
        self.seen_contexts: list[dict] = []

    def execute(self, task) -> TaskResult:
        self.seen_task_ids.append(task.task_id)
        self.seen_contexts.append(task.context)
        m = re.fullmatch(r"selflearn-r(\d+)", task.task_id)
        assert m, f"unexpected task_id {task.task_id!r}"
        feats = self._by_round.get(int(m.group(1)), [])
        return TaskResult(
            task_id=task.task_id, task_type=task.task_type,
            content={"features": feats},
            provenance=Provenance(
                provider="stub", model="stub-1", model_version="v0",
                timestamp="2026-07-27T00:00:00+00:00", prompt_hash="h",
                cost_tokens=0,
            ),
        )


def _cfg(**kw) -> LoopConfig:
    base = dict(
        dev_start="2013-01", dev_end="2013-06",
        eval_start="2014-01", eval_end="2014-12",
        top_k=10, max_features_per_round=3, max_train_rows=100_000,
        lgbm_params={
            "n_estimators": 25, "num_leaves": 7, "min_child_samples": 10,
            "learning_rate": 0.1, "verbose": -1, "n_jobs": 1,
        },
    )
    base.update(kw)
    return LoopConfig(**base)


def _loop(tmp_path, df, cloud, **kw) -> SelfLearnLoop:
    mem = StrategyMemory(SlotService(tmp_path / "slots.db", SlotConfig()))
    return SelfLearnLoop(
        df, config=_cfg(**kw), base_features=_tiny_base_features,
        cloud=cloud, memory=mem,
    )


class TestTimeRedLine:
    def test_eval_episode_in_loop_frame_rejected(self, tmp_path) -> None:
        df = _tiny_dev_df(per_episode=10)
        df.loc[df.index[0], "episode"] = "2014-03"  # eval 窗 episode 混入
        with pytest.raises(ValueError, match="red.?line|eval|dev"):
            _loop(tmp_path, df, _StubCloud({}))

    def test_verify_frame_strips_label_and_episode(self, tmp_path) -> None:
        loop = _loop(tmp_path, _tiny_dev_df(per_episode=10), _StubCloud({}))
        assert "outcome" not in loop.verify_df.columns
        assert "episode" not in loop.verify_df.columns
        # dev 标签与帧逐行对齐
        np.testing.assert_array_equal(
            loop.labels, _tiny_dev_df(per_episode=10)["outcome"].to_numpy()
        )


class TestEndToEnd:
    def test_two_rounds_accept_and_dead_end(self, tmp_path) -> None:
        cloud = _StubCloud({
            1: [
                {"name": "hidden_risk_probe", "expression": "df.hidden_risk",
                 "rationale": "存在一个未入模的信用风险维度"},
                {"name": "const_junk", "expression": "df.dti * 0.0 + 1.0",
                 "rationale": "常数应当被拒"},
            ],
            2: [],
        })
        loop = _loop(tmp_path, _tiny_dev_df(), cloud)
        records = loop.run(2)

        assert len(records) == 2
        r1, r2 = records
        # 第 1 轮:好特征入库,垃圾特征进死路
        assert r1.accepted == ["hidden_risk_probe"]
        by_name = {p.name: p for p in r1.proposals}
        assert by_name["hidden_risk_probe"].verdict == "pass"
        assert by_name["const_junk"].verdict == "fail"
        # 注册表运行时吃到新特征(L2,带云端 provenance 与假设陈述)
        assert "hidden_risk_probe" in loop.registry.names
        spec = loop.registry.spec("hidden_risk_probe")
        assert spec.level == "L2" and "stub" in spec.author
        assert "未入模" in spec.assumption
        # 重训后特征矩阵多一列
        assert loop.current_matrix()[0].shape[1] == 4 + 1
        # 第 2 轮空提案:AUC 记录存在且与第 1 轮后一致
        assert r2.accepted == [] and r2.auc_after == pytest.approx(r2.auc_before)
        assert r1.auc_after is not None and r1.auc_before is not None
        # 记忆:字段槽之外多了 1 个 shadow 特征槽 + 1 个 retired 死路槽
        statuses = [s.status for s in loop.memory.service.store.all_slots()]
        assert statuses.count("shadow") == 1
        assert statuses.count("retired") == 1
        dead = loop.memory.dead_end_list()
        assert any("const_junk" in d for d in dead)
        # 出题走了 G1 合约(task_id 轮次编码)
        assert cloud.seen_task_ids == ["selflearn-r01", "selflearn-r02"]

    def test_context_carries_residual_signals_no_row_level(self, tmp_path) -> None:
        cloud = _StubCloud({1: []})
        loop = _loop(tmp_path, _tiny_dev_df(), cloud)
        loop.run(1)
        ctx = cloud.seen_contexts[0]
        # 残余信号字段 + 阅读指引进 context
        assert "residual_signals" in ctx
        assert "residual_signals_guide" in ctx
        assert "SAME proba bin" in ctx["residual_signals_guide"]
        rs = ctx["residual_signals"]
        assert rs["n_missed"] == 10  # top_k=10(holdout 局部计数)
        assert rs["n_controls"] > 0
        assert rs["numeric"] and rs["numeric"][0]["feature"] == "hidden_risk"
        # hidden_risk 是 GBDT 看不见的字段:残余效应必须显著
        assert abs(rs["numeric"][0]["cohens_d"]) > 0.5
        # 画像保留,两者并存
        assert ctx["case_profiles"][0]["n"] == 10
        # 全 context JSON 可序列化且无逐行数据(只有聚合标量/小列表)
        blob = json.dumps(ctx, ensure_ascii=False)
        assert len(blob) < 20000
        for entry in rs["numeric"]:
            for v in entry["missed"].values():
                assert isinstance(v, float)

    def test_duplicate_proposal_name_rejected_and_recorded(self, tmp_path) -> None:
        cloud = _StubCloud({
            1: [
                {"name": "dti", "expression": "df.dti + 0.0",
                 "rationale": "与基础特征同名,必须拒"},
            ],
        })
        loop = _loop(tmp_path, _tiny_dev_df(per_episode=60), cloud)
        (r1,) = loop.run(1)
        assert r1.accepted == []
        assert r1.proposals[0].verdict == "fail"
        assert "重名" in r1.proposals[0].reasons[0] or "duplicate" in r1.proposals[0].reasons[0].lower()
