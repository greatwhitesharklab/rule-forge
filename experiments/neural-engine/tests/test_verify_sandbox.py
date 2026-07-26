"""Sandbox tests (design doc §3.2): AST whitelist + subprocess isolation."""

import numpy as np
import pandas as pd
import pytest

from verify.sandbox import check_expression_ast, run_expression

DF = pd.DataFrame({"a": [1.0, 2.0, 3.0], "b": [4.0, 5.0, 6.0]})


class TestAstWhitelist:
    @pytest.mark.parametrize(
        "expr",
        [
            "df.a + df.b",
            "np.log(df.a + 1.0)",
            "df.a.rolling(2).mean() / df.b",
            "(df.a > 1.0).astype(float)",
            "pd.Series(df.a).sum()",
            "np.where(df.a > 1.0, df.b, 0.0)",
        ],
    )
    def test_benign_expressions_allowed(self, expr: str) -> None:
        assert check_expression_ast(expr) == []

    @pytest.mark.parametrize(
        "expr, fragment",
        [
            ("import os", "syntax"),  # statements cannot even parse in eval mode
            ("open('/etc/passwd')", "open"),
            ("exec('1')", "exec"),
            ("eval('1')", "eval"),
            ("__import__('os')", "__import__"),
            ("df.to_csv('/tmp/x')", "to_csv"),
            ("df.read_csv('x')", "read_csv"),
            ("np.save('/tmp/a', df.a.values)", "save"),
            ("df.a.values.tofile('/tmp/a')", "tofile"),
            ("np.load('/tmp/a.npy')", "load"),
            ("df.a.__class__", "_"),
            ("().__class__.__mro__", "_"),
            ("(lambda: 1)()", "Lambda"),
            ("[x for x in df.a]", "comprehension"),
            ("df.eval('a + b')", "eval"),
        ],
    )
    def test_malicious_expressions_rejected(self, expr: str, fragment: str) -> None:
        violations = check_expression_ast(expr)
        assert violations, f"{expr!r} should be refused"
        assert any(fragment in v for v in violations)

    def test_rejected_expression_never_runs(self) -> None:
        result = run_expression("open('/etc/passwd').read()", DF, timeout_s=2.0)
        assert not result.ok
        assert "ast whitelist" in (result.error or "")


class TestSubprocessExecution:
    def test_column_arithmetic(self) -> None:
        result = run_expression("df.a + df.b", DF)
        assert result.ok
        np.testing.assert_allclose(result.values, [5.0, 7.0, 9.0])

    def test_scalar_result_broadcasts(self) -> None:
        result = run_expression("1.5", DF)
        assert result.ok
        np.testing.assert_allclose(result.values, [1.5, 1.5, 1.5])

    def test_numpy_call(self) -> None:
        result = run_expression("np.log(df.a)", DF)
        assert result.ok
        np.testing.assert_allclose(result.values, np.log([1.0, 2.0, 3.0]))

    def test_missing_column_is_clean_error(self) -> None:
        result = run_expression("df.missing + 1.0", DF)
        assert not result.ok
        assert "AttributeError" in (result.error or "")

    def test_length_mismatch_rejected(self) -> None:
        result = run_expression("df.a.head(2)", DF)
        assert not result.ok
        assert "length" in (result.error or "").lower()

    def test_infinite_loop_times_out(self) -> None:
        # CPU-bound, memory-trivial: only the wall-clock guard can stop it.
        result = run_expression("sum(range(10**12))", DF, timeout_s=0.5)
        assert not result.ok
        assert result.timed_out

    def test_memory_hog_killed(self) -> None:
        # 800MB virtual allocation against a 64MB headroom must fail cleanly.
        result = run_expression("np.zeros(10**8)", DF, timeout_s=10.0, memory_mb=64)
        assert not result.ok
        assert result.memory_exceeded

    def test_small_allocation_survives_small_headroom(self) -> None:
        result = run_expression("df.a * 2.0", DF, timeout_s=5.0, memory_mb=64)
        assert result.ok
        np.testing.assert_allclose(result.values, [2.0, 4.0, 6.0])
