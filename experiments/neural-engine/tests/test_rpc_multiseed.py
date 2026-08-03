"""Tests for eval/rpc_multiseed.py (statistics + plotting only).

The heavy experiment loop (_run_group) is not exercised here.
"""

from __future__ import annotations

import json

import pytest

from eval.rpc_multiseed import ci95, plot_multiseed, summarize_runs
from eval.rpc_paper1 import ExperimentResult


def _res(group: str, seed: int, strong_rate: float,
         b_quality: float = 0.25, b_strong: int = 5) -> ExperimentResult:
    return ExperimentResult(
        group=group, seed=seed, b_strong=b_strong, strong_rate=strong_rate,
        b_quality=b_quality, b_feat=10, total_proposals=20,
        avg_iv_first_half=0.2, avg_iv_second_half=0.3,
    )


class TestCi95:
    def test_known_values(self) -> None:
        stat = ci95([0.2, 0.4, 0.6])
        assert stat["mean"] == pytest.approx(0.4)
        assert stat["n"] == 3
        assert stat["ci_lo"] < stat["mean"] < stat["ci_hi"]

    def test_single_value_degenerates(self) -> None:
        stat = ci95([0.42])
        assert stat["ci_lo"] == stat["ci_hi"] == pytest.approx(0.42)

    def test_identical_values_zero_width(self) -> None:
        stat = ci95([0.3, 0.3, 0.3])
        assert stat["ci_lo"] == pytest.approx(0.3)
        assert stat["ci_hi"] == pytest.approx(0.3)

    def test_wider_spread_wider_interval(self) -> None:
        narrow = ci95([0.39, 0.40, 0.41])
        wide = ci95([0.1, 0.4, 0.7])
        assert (wide["ci_hi"] - wide["ci_lo"]) > (narrow["ci_hi"] - narrow["ci_lo"])


class TestSummarizeRuns:
    def _results(self) -> list[ExperimentResult]:
        return [
            _res("A", 1, 0.20), _res("A", 2, 0.22), _res("A", 3, 0.24),
            _res("B", 1, 0.25), _res("B", 2, 0.25), _res("B", 3, 0.25),
            _res("C", 1, 0.21), _res("C", 2, 0.23), _res("C", 3, 0.25),
            _res("D", 1, 0.40), _res("D", 2, 0.44), _res("D", 3, 0.48),
        ]

    def test_group_stats(self) -> None:
        agg = summarize_runs(self._results())
        assert set(agg["summary"]) == {"A", "B", "C", "D"}
        assert agg["summary"]["A"]["strong_rate"]["mean"] == pytest.approx(0.22)
        assert agg["summary"]["B"]["strong_rate"]["ci_lo"] == pytest.approx(0.25)

    def test_d_vs_a_ratio(self) -> None:
        agg = summarize_runs(self._results())
        ratio = agg["d_vs_a_ratio"]["strong_rate"]
        # Per-seed ratios: 0.40/0.20 = 2.0, 0.44/0.22 = 2.0, 0.48/0.24 = 2.0.
        assert ratio["mean"] == pytest.approx(2.0)
        assert ratio["seeds"] == [1, 2, 3]
        assert agg["d_advantage_robust"]

    def test_not_robust_when_ci_crosses_one(self) -> None:
        results = [
            _res("A", 1, 0.20), _res("A", 2, 0.30),
            _res("D", 1, 0.40), _res("D", 2, 0.22),  # ratios 2.0 and 0.733
        ]
        agg = summarize_runs(results)
        assert not agg["d_advantage_robust"]

    def test_missing_seed_pair_skipped(self) -> None:
        results = [_res("A", 1, 0.2), _res("D", 2, 0.4)]
        agg = summarize_runs(results)
        assert agg["d_vs_a_ratio"]["strong_rate"]["seeds"] == []

    def test_json_serializable(self) -> None:
        json.dumps(summarize_runs(self._results()))


class TestPlot:
    def test_plot_writes_png(self, tmp_path) -> None:
        results = [
            _res("A", 1, 0.20), _res("A", 2, 0.22),
            _res("D", 1, 0.40), _res("D", 2, 0.44),
        ]
        agg = summarize_runs(results)
        out = tmp_path / "multiseed.png"
        plot_multiseed(agg, out)
        assert out.exists()
        assert out.stat().st_size > 1000
