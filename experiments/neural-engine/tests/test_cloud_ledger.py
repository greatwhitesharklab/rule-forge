"""Tests for cloud.ledger (cost / provenance ledger, design doc §2.1)."""

from __future__ import annotations

from pathlib import Path

from cloud.ledger import CostLedger


def add_row(
    ledger: CostLedger,
    *,
    ts: str,
    provider: str = "deepseek",
    task_type: str = "feature_proposal",
    cost: int = 100,
    status: str = "ok",
) -> None:
    ledger.record(
        ts=ts,
        task_id=f"t-{ts}",
        task_type=task_type,
        provider=provider,
        model="m",
        model_version="v1",
        prompt_hash="h" * 64,
        cost_tokens=cost,
        status=status,
        error=None if status == "ok" else "boom",
    )


class TestRecordAndAggregate:
    def test_aggregate_groups_by_provider_and_task_type(self) -> None:
        ledger = CostLedger()
        add_row(ledger, ts="2026-07-01T00:00:00+00:00", cost=100)
        add_row(ledger, ts="2026-07-02T00:00:00+00:00", cost=50)
        add_row(ledger, ts="2026-07-02T01:00:00+00:00", task_type="explanation", cost=30)
        add_row(ledger, ts="2026-07-03T00:00:00+00:00", provider="gpt", cost=200)

        rows = ledger.aggregate()
        assert rows == [
            {"provider": "deepseek", "task_type": "explanation", "calls": 1, "cost_tokens": 30, "failures": 0},
            {"provider": "deepseek", "task_type": "feature_proposal", "calls": 2, "cost_tokens": 150, "failures": 0},
            {"provider": "gpt", "task_type": "feature_proposal", "calls": 1, "cost_tokens": 200, "failures": 0},
        ]

    def test_filter_by_provider(self) -> None:
        ledger = CostLedger()
        add_row(ledger, ts="2026-07-01T00:00:00+00:00")
        add_row(ledger, ts="2026-07-01T01:00:00+00:00", provider="gpt")
        rows = ledger.aggregate(provider="gpt")
        assert len(rows) == 1 and rows[0]["provider"] == "gpt"

    def test_filter_by_task_type(self) -> None:
        ledger = CostLedger()
        add_row(ledger, ts="2026-07-01T00:00:00+00:00", task_type="explanation")
        add_row(ledger, ts="2026-07-01T01:00:00+00:00", task_type="case_analysis")
        rows = ledger.aggregate(task_type="case_analysis")
        assert len(rows) == 1 and rows[0]["task_type"] == "case_analysis"

    def test_filter_by_time_range(self) -> None:
        ledger = CostLedger()
        add_row(ledger, ts="2026-06-30T23:59:59+00:00", cost=1)
        add_row(ledger, ts="2026-07-01T00:00:00+00:00", cost=10)
        add_row(ledger, ts="2026-07-31T23:59:59+00:00", cost=100)
        add_row(ledger, ts="2026-08-01T00:00:00+00:00", cost=1000)
        rows = ledger.aggregate(since="2026-07-01T00:00:00+00:00", until="2026-08-01T00:00:00+00:00")
        assert rows[0]["calls"] == 2
        assert rows[0]["cost_tokens"] == 110

    def test_failures_counted(self) -> None:
        ledger = CostLedger()
        add_row(ledger, ts="2026-07-01T00:00:00+00:00", status="error", cost=0)
        add_row(ledger, ts="2026-07-01T01:00:00+00:00", status="blocked", cost=0)
        add_row(ledger, ts="2026-07-01T02:00:00+00:00", cost=10)
        rows = ledger.aggregate()
        assert rows[0]["failures"] == 2

    def test_empty_ledger_returns_empty(self) -> None:
        assert CostLedger().aggregate() == []


class TestPersistence:
    def test_sqlite_file_roundtrip(self, tmp_path: Path) -> None:
        path = tmp_path / "ledger.db"
        ledger = CostLedger(path)
        add_row(ledger, ts="2026-07-01T00:00:00+00:00", cost=42)
        ledger.close()

        reopened = CostLedger(path)
        rows = reopened.aggregate()
        assert rows[0]["cost_tokens"] == 42
        reopened.close()

    def test_fetch_returns_raw_rows(self) -> None:
        ledger = CostLedger()
        add_row(ledger, ts="2026-07-01T00:00:00+00:00")
        rows = ledger.fetch(provider="deepseek")
        assert len(rows) == 1
        assert rows[0]["prompt_hash"] == "h" * 64
        assert rows[0]["status"] == "ok"
