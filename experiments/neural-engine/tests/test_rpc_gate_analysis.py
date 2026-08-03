"""Tests for eval/rpc_gate_analysis.py (pure functions + plotting only).

The heavy training path (run_gate_experiment) is not exercised here; it is
covered by manual experiment runs, not unit tests.
"""

from __future__ import annotations

import json

import numpy as np

from eval.rpc_gate_analysis import (
    PM_COLUMNS,
    evaluate_hypothesis,
    plot_gate_history,
    segment_means,
    summarize_history,
)


def _uniform_gates(n_layers: int = 28, value: float = 0.5) -> np.ndarray:
    return np.full((n_layers, len(PM_COLUMNS)), value)


def _narrative_gates() -> np.ndarray:
    """Gates matching the Section 6.2 narrative.

    Low segment: all gates low (Base favored). Mid: domain high.
    High: session high.
    """
    g = np.full((28, 3), 0.3)
    g[10:20, 0] = 0.8   # domain gate high in mid layers
    g[20:28, 2] = 0.8   # session gate high in upper layers
    return g


class TestSegmentMeans:
    def test_uniform_gates(self) -> None:
        seg = segment_means(_uniform_gates())
        for s in ("low", "mid", "high"):
            for col in PM_COLUMNS:
                assert seg[s][col] == 0.5

    def test_segment_boundaries(self) -> None:
        g = np.zeros((28, 3))
        g[0:10, :] = 0.1
        g[10:20, :] = 0.5
        g[20:28, :] = 0.9
        seg = segment_means(g)
        assert seg["low"]["domain"] == 0.1
        assert seg["mid"]["session"] == 0.5
        assert seg["high"]["user"] == 0.9

    def test_shorter_model(self) -> None:
        # n_layers < 28 must not crash (segment end is clamped).
        seg = segment_means(np.full((12, 3), 0.4))
        assert seg["high"]["domain"] == 0.4


class TestEvaluateHypothesis:
    def test_narrative_match(self) -> None:
        verdict = evaluate_hypothesis(_narrative_gates())
        assert verdict["specialization_detected"]
        assert verdict["narrative_match"]

    def test_uniform_init_is_not_specialized(self) -> None:
        # Gates stuck at the 0.5 init: no specialization, no narrative.
        verdict = evaluate_hypothesis(_uniform_gates())
        assert not verdict["specialization_detected"]
        assert not verdict["narrative_match"]
        assert verdict["max_abs_deviation_from_init"] == 0.0

    def test_moved_but_wrong_pattern(self) -> None:
        # Session gate high in LOW layers: moved, but contradicts narrative.
        g = _uniform_gates()
        g[0:10, 2] = 0.9
        verdict = evaluate_hypothesis(g)
        assert verdict["specialization_detected"]
        assert not verdict["narrative_match"]


class TestSummarizeHistory:
    def test_counts_moved_gates(self) -> None:
        init = _uniform_gates()
        final = _uniform_gates()
        final[27, 2] = 0.53  # one gate moved by 0.03
        report = summarize_history([init, final])
        assert report["n_snapshots"] == 2
        assert report["n_layers"] == 28
        assert report["n_gates_moved_gt_0.01"] == 1
        assert report["n_gates_total"] == 84
        assert report["max_drift_over_training"] == 0.03

    def test_json_serializable(self) -> None:
        report = summarize_history([_uniform_gates(), _narrative_gates()])
        json.dumps(report)


class TestPlot:
    def test_plot_writes_png(self, tmp_path) -> None:
        history = [_uniform_gates(), _narrative_gates()]
        out = tmp_path / "gates.png"
        plot_gate_history(history, out)
        assert out.exists()
        assert out.stat().st_size > 1000  # real PNG, not empty

    def test_plot_single_snapshot(self, tmp_path) -> None:
        out = tmp_path / "gates1.png"
        plot_gate_history([_uniform_gates()], out)
        assert out.exists()
