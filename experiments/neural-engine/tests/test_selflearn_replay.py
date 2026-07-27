"""replay 云端模式测试:replay_features.json 加载、格式校验、按轮次重放。

replay 文件是既往验证通过的特征的固化重放,供自动 N 轮与回归测试使用;
格式坏必须 fail-fast,绝不静默吞掉。
"""

from __future__ import annotations

import json

import pytest

from selflearn.replay import ReplayFormatError, ReplayProvider, load_replay


def _write(tmp_path, payload) -> str:
    p = tmp_path / "replay.json"
    p.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return str(p)


GOOD = {
    "version": 1,
    "rounds": [
        {"round": 1, "features": [
            {"name": "dti_x_amnt", "expression": "df.dti * df.loan_amnt",
             "rationale": "高杠杆大额借款更危险"},
        ]},
        {"round": 2, "features": []},
    ],
}


class TestLoadReplay:
    def test_valid_file_maps_rounds(self, tmp_path) -> None:
        rounds = load_replay(_write(tmp_path, GOOD))
        assert sorted(rounds) == [1, 2]
        assert rounds[1][0].name == "dti_x_amnt"
        assert rounds[1][0].expression == "df.dti * df.loan_amnt"
        assert rounds[2] == []

    def test_empty_rounds_is_valid_noop(self, tmp_path) -> None:
        assert load_replay(_write(tmp_path, {"version": 1, "rounds": []})) == {}
        assert load_replay(_write(tmp_path, {})) == {}

    def test_missing_file_raises(self, tmp_path) -> None:
        with pytest.raises(FileNotFoundError):
            load_replay(tmp_path / "nope.json")

    def test_rounds_must_be_a_list(self, tmp_path) -> None:
        with pytest.raises(ReplayFormatError):
            load_replay(_write(tmp_path, {"rounds": {"round": 1}}))

    def test_feature_missing_expression_rejected(self, tmp_path) -> None:
        bad = {"rounds": [{"round": 1, "features": [{"name": "x", "rationale": "r"}]}]}
        with pytest.raises(ReplayFormatError, match="expression"):
            load_replay(_write(tmp_path, bad))

    def test_duplicate_name_in_same_round_rejected(self, tmp_path) -> None:
        bad = {"rounds": [{"round": 1, "features": [
            {"name": "x", "expression": "df.dti", "rationale": "a"},
            {"name": "x", "expression": "df.dti + 1", "rationale": "b"},
        ]}]}
        with pytest.raises(ReplayFormatError, match="duplicate"):
            load_replay(_write(tmp_path, bad))

    def test_invalid_feature_name_rejected(self, tmp_path) -> None:
        bad = {"rounds": [{"round": 1, "features": [
            {"name": "1bad name", "expression": "df.dti", "rationale": "a"},
        ]}]}
        with pytest.raises(ReplayFormatError, match="name"):
            load_replay(_write(tmp_path, bad))

    def test_round_number_must_be_positive_int(self, tmp_path) -> None:
        bad = {"rounds": [{"round": 0, "features": []}]}
        with pytest.raises(ReplayFormatError):
            load_replay(_write(tmp_path, bad))


class _FakeTask:
    def __init__(self, task_id: str) -> None:
        self.task_id = task_id
        self.task_type = "feature_proposal"


class TestReplayProvider:
    def test_execute_returns_round_features(self, tmp_path) -> None:
        provider = ReplayProvider(load_replay(_write(tmp_path, GOOD)),
                                  source="replay.json")
        result = provider.execute(_FakeTask("selflearn-r01"))
        assert result.task_type == "feature_proposal"
        assert [f["name"] for f in result.content["features"]] == ["dti_x_amnt"]
        assert result.provenance.provider == "replay"

    def test_missing_round_returns_empty_features(self, tmp_path) -> None:
        provider = ReplayProvider(load_replay(_write(tmp_path, GOOD)),
                                  source="replay.json")
        result = provider.execute(_FakeTask("selflearn-r07"))
        assert result.content == {"features": []}

    def test_unparseable_task_id_raises(self, tmp_path) -> None:
        provider = ReplayProvider({}, source="replay.json")
        with pytest.raises(ReplayFormatError):
            provider.execute(_FakeTask("garbage-id"))
