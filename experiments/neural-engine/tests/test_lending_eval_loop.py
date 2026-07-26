"""滚动实验主循环测试:时间红线(LAG)与两臂训练窗一致性。

用小规模合成 episode 数据跑 run_experiment,断言:
- 记忆库在 episode t 决策时只含 <= t-LAG 的成熟案例(写槽来源 episode);
- 两臂每个 episode 的训练案例集完全一致(同 mature 案例、同窗口);
- 训练 episode 全部落在 [t-LAG-window+1, t-LAG] 内;
- 臂 B 训练用的记忆特征是决策时刻(as-of)冻结值,不是事后重算。
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from eval.lending_acceptance import ExperimentConfig, run_experiment

EPISODES = [f"2008-{m:02d}" for m in range(1, 9)]  # 8 个 episode


def _synth_df(per_episode: int = 30, seed: int = 7) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    rows = []
    grades = ["A", "B", "C", "D", "E", "F", "G"]
    for ei, ep in enumerate(EPISODES):
        grade_i = rng.integers(0, 7, per_episode)
        # 可学信号:grade 越差 bad 概率越高;晚期 episode 整体漂移
        p_bad = 0.05 + 0.06 * grade_i + 0.03 * (ei >= 5)
        bad = rng.random(per_episode) < p_bad
        for j in range(per_episode):
            rows.append({
                "episode": ep,
                "outcome": int(bad[j]),
                "grade": grades[grade_i[j]],
                "sub_grade": f"{grades[grade_i[j]]}3",
                "term": "36 months",
                "term_months": 36,
                "home_ownership": "RENT" if j % 2 else "OWN",
                "verification_status": "Verified",
                "purpose": "debt_consolidation" if j % 3 else "credit_card",
                "application_type": "Individual",
                "addr_state": "CA",
                "loan_amnt": 5000.0 + 100 * j,
                "int_rate": 8.0 + grade_i[j],
                "installment": 150.0 + j,
                "annual_inc": 40000.0 + 500 * j,
                "dti": 5.0 + grade_i[j] * 3.0,
                "delinq_2yrs": float(j % 2),
                "inq_last_6mths": float(j % 3),
                "open_acc": 5.0,
                "pub_rec": 0.0,
                "revol_bal": 3000.0,
                "revol_util": 20.0 + grade_i[j] * 8.0,
                "total_acc": 12.0,
                "credit_history_months": 120,
                "emp_length_years": 3,
                "emp_title_norm": f"employer {j % 5}",
            })
    return pd.DataFrame(rows)


def _cfg(tmp_path, **kw) -> ExperimentConfig:
    base = dict(
        eval_start=4, lag=2, window=3, min_train=20, max_train_rows=100000,
        approve_rate=0.5, seed=11, trace=True,
    )
    base.update(kw)
    return ExperimentConfig(**base)


class TestTimeRedLine:
    def test_memory_contains_only_matured_episodes(self, tmp_path):
        df = _synth_df()
        ep_index = {e: i for i, e in enumerate(EPISODES)}
        violations = []

        def day_hook(t, ep, mem):
            for slot_ep in mem.slot_episodes().values():
                if ep_index[slot_ep] > t - 2:  # lag=2
                    violations.append((t, slot_ep))

        run_experiment(_cfg(tmp_path), df, work_dir=tmp_path / "w",
                       day_hook=day_hook)
        assert violations == []

    def test_lag1_stricter_than_lag3(self, tmp_path):
        """LAG 可配:lag=1 时,t 决策只允许 <= t-1 的写入。"""
        df = _synth_df()
        ep_index = {e: i for i, e in enumerate(EPISODES)}
        seen = []

        def day_hook(t, ep, mem):
            seen.append(
                max((ep_index[e] for e in mem.slot_episodes().values()),
                    default=-1)
            )

        run_experiment(_cfg(tmp_path, lag=1), df, work_dir=tmp_path / "w",
                       day_hook=day_hook)
        for t, max_written in enumerate(seen):
            assert max_written <= t - 1


class TestTrainWindowConsistency:
    def test_arms_train_on_identical_cases(self, tmp_path):
        res = run_experiment(_cfg(tmp_path), _synth_df(),
                             work_dir=tmp_path / "w")
        trained = [r for r in res.records if not r["cold_start"]]
        assert trained, "no trained episode in fixture"
        for r in trained:
            assert r["train_case_ids_A"] == r["train_case_ids_B"]

    def test_train_episodes_within_mature_window(self, tmp_path):
        res = run_experiment(_cfg(tmp_path), _synth_df(),
                             work_dir=tmp_path / "w")
        ep_index = {e: i for i, e in enumerate(EPISODES)}
        for r in res.records:
            if r["cold_start"]:
                continue
            t = ep_index[r["episode"]]
            for e in r["train_episodes"]:
                assert t - 2 - 3 + 1 <= ep_index[e] <= t - 2  # lag=2, window=3

    def test_armB_uses_frozen_decision_time_features(self, tmp_path):
        """臂 B 训练矩阵中的记忆特征 == 该案例决策时 enrich 的冻结值。"""
        res = run_experiment(_cfg(tmp_path), _synth_df(),
                             work_dir=tmp_path / "w")
        for r in res.records:
            if r["cold_start"]:
                continue
            for cid, frozen_val in r["train_memory_features"]:
                assert res.enriched_features[cid] == pytest.approx(
                    frozen_val, abs=1e-9
                )
