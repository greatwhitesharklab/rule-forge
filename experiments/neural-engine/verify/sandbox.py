"""Sandbox for cloud-proposed feature expressions (design doc §3.2).

Two layers, no docker:

1. AST whitelist (static, checked in the parent): only pandas/numpy/df
   arithmetic survives. Imports, file/network builtins, dunder attribute
   access, file-writing methods, lambdas and comprehensions are refused
   before any code runs.
2. Subprocess isolation (fork): the expression is evaluated in a child with
   a wall-clock timeout enforced by the parent (default 30s), a CPU backstop
   via RLIMIT_CPU, a memory cap via RLIMIT_AS and a zero file-size limit.

Memory semantics: RLIMIT_AS counts *virtual* address space, and a Python +
pandas interpreter baseline already exceeds 512MB, so the cap is applied as
headroom over the child's baseline at fork time (baseline + memory_mb).
That keeps the limit meaningful regardless of what the parent has loaded.

Known limits (accepted for P1): fork inherits the parent's open file
descriptors, and the network is blocked only indirectly (no importable
modules + no socket-carrying names reach the eval environment), not by a
namespace. Docker/namespace-grade isolation is the documented upgrade path.
"""

from __future__ import annotations

import ast
import builtins
import math
import multiprocessing as mp
import resource
import signal
import time
from dataclasses import dataclass

import numpy as np
import pandas as pd

# Names an expression may reference: the data frame, the two libraries, and a
# small set of pure builtins. Everything else (open/exec/eval/__import__/...)
# is refused by the whitelist.
SAFE_BUILTINS: tuple[str, ...] = (
    "abs", "min", "max", "round", "sum", "len", "pow", "range", "sorted",
    "enumerate", "zip", "float", "int", "str", "bool", "list", "tuple",
    "dict", "set",
)
_ALLOWED_NAMES = frozenset({"df", "pd", "np"} | set(SAFE_BUILTINS))

# Node types that have no legitimate place in a feature expression.
_DENY_NODES = (
    ast.Import, ast.ImportFrom, ast.Lambda, ast.NamedExpr,
    ast.Yield, ast.YieldFrom, ast.Await, ast.Starred,
    ast.ListComp, ast.SetComp, ast.DictComp, ast.GeneratorExp,
    ast.Global, ast.Nonlocal,
)

# File/network-capable methods reachable from pd/np/df without any import.
_DENY_ATTRS = frozenset({
    "to_csv", "to_pickle", "to_sql", "to_json", "to_excel", "to_parquet",
    "to_feather", "to_stata", "to_hdf", "to_gbq", "to_clipboard", "to_latex",
    "to_markdown", "memmap", "fromfile", "tofile", "save", "savez",
    "savez_compressed", "savetxt", "load", "loads", "dump", "dumps",
    "eval", "query", "pickle",
})


def check_expression_ast(expr: str) -> list[str]:
    """Static whitelist; returns violation descriptions (empty = allowed)."""
    try:
        tree = ast.parse(expr, mode="eval")
    except SyntaxError as exc:
        return [f"syntax error: {exc.msg}"]
    violations: list[str] = []
    for node in ast.walk(tree):
        if isinstance(node, (ast.ListComp, ast.SetComp, ast.DictComp, ast.GeneratorExp)):
            violations.append(f"comprehension node {type(node).__name__} is not allowed")
        elif isinstance(node, _DENY_NODES):
            violations.append(f"node {type(node).__name__} is not allowed")
        elif isinstance(node, ast.Name):
            if node.id not in _ALLOWED_NAMES:
                violations.append(f"name {node.id!r} is not allowed")
        elif isinstance(node, ast.Attribute):
            attr = node.attr
            if attr.startswith("_") or attr.startswith("read_") or attr in _DENY_ATTRS:
                violations.append(f"attribute {attr!r} is not allowed")
    return violations


@dataclass(frozen=True)
class SandboxResult:
    """Outcome of one sandboxed evaluation."""

    ok: bool
    values: np.ndarray | None = None  # 1-D float64, len == len(df)
    error: str | None = None
    timed_out: bool = False
    memory_exceeded: bool = False


def _current_vsz() -> int:
    """This process's virtual memory size in bytes (Linux /proc)."""
    with open("/proc/self/stat", "rb") as fh:
        tail = fh.read().rsplit(b")", 1)[1].split()
    return int(tail[20])  # vsize is field 23; index 20 after the comm field


def _try_send(conn: mp.connection.Connection, msg: dict[str, object]) -> None:
    try:
        conn.send(msg)
    except Exception:
        pass  # child is about to exit anyway; parent reports the bare exit


def _child_entry(
    conn: mp.connection.Connection,
    expr: str,
    df: pd.DataFrame,
    memory_mb: int,
    cpu_s: int,
) -> None:
    """Child side: tighten rlimits, then eval the expression with a reduced
    globals environment and ship the resulting 1-D float array back."""
    try:
        soft = _current_vsz() + memory_mb * 1024 * 1024
        resource.setrlimit(resource.RLIMIT_AS, (soft, soft + 64 * 1024 * 1024))
        resource.setrlimit(resource.RLIMIT_CPU, (cpu_s + 5, cpu_s + 10))
        resource.setrlimit(resource.RLIMIT_FSIZE, (0, 0))
    except (OSError, ValueError):
        pass  # rlimits are a backstop; never mask the expression itself
    env = {
        "__builtins__": {name: getattr(builtins, name) for name in SAFE_BUILTINS},
        "df": df,
        "np": np,
        "pd": pd,
    }
    try:
        result = eval(compile(expr, "<feature-expression>", "eval"), env, {})
        arr = np.asarray(result, dtype=np.float64)
        if arr.ndim == 0:
            arr = np.full(len(df), float(arr))
        if arr.ndim != 1 or arr.shape[0] != len(df):
            raise ValueError(f"result must be 1-D of length {len(df)}, got shape {arr.shape}")
        conn.send({"ok": True, "values": arr})
    except MemoryError:
        _try_send(conn, {"ok": False, "kind": "memory",
                         "error": f"memory limit ({memory_mb}MB headroom) exceeded"})
    except BaseException as exc:
        _try_send(conn, {"ok": False, "kind": "error",
                         "error": f"{type(exc).__name__}: {exc}"})
    finally:
        conn.close()


def run_expression(
    expr: str,
    df: pd.DataFrame,
    *,
    timeout_s: float = 30.0,
    memory_mb: int = 512,
) -> SandboxResult:
    """Evaluate ``expr`` against ``df`` under the two-layer sandbox."""
    violations = check_expression_ast(expr)
    if violations:
        return SandboxResult(ok=False, error="ast whitelist: " + "; ".join(violations))

    ctx = mp.get_context("fork")
    parent_conn, child_conn = ctx.Pipe(duplex=False)
    proc = ctx.Process(
        target=_child_entry,
        args=(child_conn, expr, df, memory_mb, int(math.ceil(timeout_s))),
    )
    proc.start()
    child_conn.close()

    deadline = time.monotonic() + timeout_s
    msg: dict[str, object] | None = None
    while True:
        if parent_conn.poll(0.02):
            msg = parent_conn.recv()
            break
        if not proc.is_alive():
            break
        if time.monotonic() >= deadline:
            proc.terminate()
            proc.join(1.0)
            if proc.is_alive():
                proc.kill()
                proc.join()
            return SandboxResult(ok=False, error=f"timeout after {timeout_s}s", timed_out=True)
    proc.join(2.0)
    if proc.is_alive():  # sent a message but failed to exit — treat as hang
        proc.terminate()
        proc.join()
        return SandboxResult(ok=False, error=f"timeout after {timeout_s}s", timed_out=True)

    if msg is None:
        if proc.exitcode == -signal.SIGXCPU:
            return SandboxResult(ok=False, error="cpu limit exceeded", timed_out=True)
        return SandboxResult(ok=False, error=f"sandbox crashed (exit code {proc.exitcode})")
    if msg.get("ok"):
        return SandboxResult(ok=True, values=msg["values"])  # type: ignore[arg-type]
    return SandboxResult(
        ok=False,
        error=str(msg.get("error", "unknown sandbox error")),
        memory_exceeded=msg.get("kind") == "memory",
    )
