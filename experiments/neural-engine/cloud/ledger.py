"""Cost / provenance ledger (design doc §2.1).

Every cloud call — success, transport error, or outbound block — lands in a
SQLite table with its full provenance and token cost. Aggregation by provider,
task type and time range feeds the per-task-type reliability ledger (§3.1).
"""

from __future__ import annotations

import sqlite3
from pathlib import Path

_SCHEMA = """
CREATE TABLE IF NOT EXISTS cloud_calls (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  ts            TEXT NOT NULL,          -- ISO-8601 UTC
  task_id       TEXT NOT NULL,
  task_type     TEXT NOT NULL,
  provider      TEXT NOT NULL,
  model         TEXT NOT NULL,
  model_version TEXT,
  prompt_hash   TEXT NOT NULL,
  cost_tokens   INTEGER NOT NULL,
  status        TEXT NOT NULL,          -- ok / error / blocked
  error         TEXT
);
CREATE INDEX IF NOT EXISTS idx_cloud_calls_provider ON cloud_calls(provider);
CREATE INDEX IF NOT EXISTS idx_cloud_calls_task ON cloud_calls(task_type);
CREATE INDEX IF NOT EXISTS idx_cloud_calls_ts ON cloud_calls(ts);
"""

_FILTERS = {
    "provider": "provider = ?",
    "task_type": "task_type = ?",
    "since": "ts >= ?",
    "until": "ts < ?",
}


class CostLedger:
    """SQLite-backed ledger of cloud calls."""

    def __init__(self, path: str | Path = ":memory:") -> None:
        self._conn = sqlite3.connect(str(path))
        self._conn.executescript(_SCHEMA)

    def record(
        self,
        *,
        ts: str,
        task_id: str,
        task_type: str,
        provider: str,
        model: str,
        model_version: str | None,
        prompt_hash: str,
        cost_tokens: int,
        status: str,
        error: str | None = None,
    ) -> int:
        """Append one call record; returns the row id. Append-only (WAL mindset)."""
        cur = self._conn.execute(
            "INSERT INTO cloud_calls"
            " (ts, task_id, task_type, provider, model, model_version,"
            "  prompt_hash, cost_tokens, status, error)"
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (ts, task_id, task_type, provider, model, model_version,
             prompt_hash, cost_tokens, status, error),
        )
        self._conn.commit()
        return int(cur.lastrowid)

    def _where(self, provider: str | None, task_type: str | None,
               since: str | None, until: str | None) -> tuple[str, list[str]]:
        clauses, params = [], []
        for key, value in (("provider", provider), ("task_type", task_type),
                           ("since", since), ("until", until)):
            if value is not None:
                clauses.append(_FILTERS[key])
                params.append(value)
        return (" WHERE " + " AND ".join(clauses) if clauses else "", params)

    def aggregate(
        self,
        *,
        provider: str | None = None,
        task_type: str | None = None,
        since: str | None = None,
        until: str | None = None,
    ) -> list[dict]:
        """Group calls by (provider, task_type): counts, token cost, failures."""
        where, params = self._where(provider, task_type, since, until)
        cur = self._conn.execute(
            "SELECT provider, task_type, COUNT(*) AS calls,"
            "       SUM(cost_tokens) AS cost_tokens,"
            "       SUM(CASE WHEN status != 'ok' THEN 1 ELSE 0 END) AS failures"
            f" FROM cloud_calls{where}"
            " GROUP BY provider, task_type"
            " ORDER BY provider, task_type",
            params,
        )
        cols = [d[0] for d in cur.description]
        return [dict(zip(cols, row)) for row in cur.fetchall()]

    def fetch(
        self,
        *,
        provider: str | None = None,
        task_type: str | None = None,
        since: str | None = None,
        until: str | None = None,
    ) -> list[dict]:
        """Raw rows matching the filters, oldest first (audit / replay)."""
        where, params = self._where(provider, task_type, since, until)
        cur = self._conn.execute(
            f"SELECT * FROM cloud_calls{where} ORDER BY ts, id", params
        )
        cols = [d[0] for d in cur.description]
        return [dict(zip(cols, row)) for row in cur.fetchall()]

    def close(self) -> None:
        self._conn.close()
