"""Tests for cloud.agent_bridge — the AI-agent-as-cloud bridge provider."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from cloud.agent_bridge import AgentBridgeProvider, BridgeTimeoutError
from cloud.contracts import TaskPackage
from cloud.providers import CloudLLM


def make_pkg(task_id: str = "t-bridge-1", note: str | None = None) -> TaskPackage:
    return TaskPackage(
        task_id=task_id,
        task_type="feature_proposal",
        context={
            "case_profiles": [
                {
                    "profile_id": "p-001",
                    "note": note or "bad_rate=0.18; lift=1.4",
                }
            ],
            "existing_features": ["debt_ratio"],
            "dead_ends": ["income_x_region"],
        },
        constraints={"max_features": 5, "must_be_executable": "python", "no_future_info": True},
        output_schema={"features": [{"name": "str", "expression": "str", "rationale": "str"}]},
    )


def write_inbox(bridge_dir: Path, task_id: str, payload: dict | None = None) -> Path:
    inbox = bridge_dir / "inbox"
    inbox.mkdir(parents=True, exist_ok=True)
    body = payload or {
        "model": "kimi-k2",
        "model_version": "2026-07-26",
        "content": {"features": [{"name": "f1", "expression": "a / b", "rationale": "why"}]},
    }
    path = inbox / f"{task_id}.json"
    path.write_text(json.dumps(body, ensure_ascii=False), encoding="utf-8")
    return path


def fast_provider(bridge_dir: Path, **kwargs) -> AgentBridgeProvider:
    return AgentBridgeProvider(
        bridge_dir=bridge_dir, timeout_s=2.0, poll_interval_s=0.02, **kwargs
    )


class TestOutboxEmission:
    def test_conforms_to_protocol(self, tmp_path: Path) -> None:
        assert isinstance(fast_provider(tmp_path), CloudLLM)

    def test_outbox_metadata_and_sanitized_prompt(self, tmp_path: Path) -> None:
        pkg = make_pkg(note="姓名：张伟，手机13812345678，身份证110101199003070077，金额12万元")
        write_inbox(tmp_path, pkg.task_id)  # pre-place reply so execute returns at once
        provider = fast_provider(tmp_path)
        provider.execute(pkg)

        outbox_file = tmp_path / "outbox" / f"{pkg.task_id}.json"
        assert outbox_file.exists()
        data = json.loads(outbox_file.read_text(encoding="utf-8"))
        for key in ("task_id", "task_type", "timestamp", "constraints", "output_schema", "prompt"):
            assert key in data
        assert data["task_id"] == pkg.task_id
        assert data["task_type"] == "feature_proposal"

        raw = outbox_file.read_text(encoding="utf-8")
        for pii in ("张伟", "13812345678", "110101199003070077", "12万"):
            assert pii not in raw
        for token in ("[NAME]", "[PHONE]", "[ID_CARD]", "<AMT:10W-50W>"):
            assert token in raw

    def test_result_provenance_from_inbox(self, tmp_path: Path) -> None:
        pkg = make_pkg()
        write_inbox(tmp_path, pkg.task_id)
        result = fast_provider(tmp_path).execute(pkg)
        prov = result.provenance
        assert prov.provider == "kimi-cli"
        assert prov.model == "kimi-k2"
        assert prov.model_version == "2026-07-26"
        assert len(prov.prompt_hash) == 64
        assert result.content["features"][0]["name"] == "f1"

    def test_model_fields_default_to_unknown(self, tmp_path: Path) -> None:
        pkg = make_pkg()
        write_inbox(
            tmp_path,
            pkg.task_id,
            {"content": {"features": [{"name": "f", "expression": "x", "rationale": "y"}]}},
        )
        result = fast_provider(tmp_path).execute(pkg)
        assert result.provenance.model == "unknown"
        assert result.provenance.model_version == "unknown"

    def test_custom_provider_name(self, tmp_path: Path) -> None:
        pkg = make_pkg()
        write_inbox(tmp_path, pkg.task_id)
        provider = fast_provider(tmp_path, provider_name="my-agent")
        assert provider.execute(pkg).provenance.provider == "my-agent"

    def test_usage_tokens_read_from_inbox(self, tmp_path: Path) -> None:
        pkg = make_pkg()
        write_inbox(
            tmp_path,
            pkg.task_id,
            {
                "model": "kimi-k2",
                "model_version": "v",
                "content": {"features": [{"name": "f", "expression": "x", "rationale": "y"}]},
                "usage": {"input_tokens": 100, "output_tokens": 40},
            },
        )
        result = fast_provider(tmp_path).execute(pkg)
        assert result.provenance.cost_tokens == 140


class TestTimeoutAndArchive:
    def test_timeout_raises_explicit_error(self, tmp_path: Path) -> None:
        provider = AgentBridgeProvider(bridge_dir=tmp_path, timeout_s=0.1, poll_interval_s=0.02)
        with pytest.raises(BridgeTimeoutError, match="t-never"):
            provider.execute(make_pkg(task_id="t-never"))

    def test_inbox_archived_after_consumption(self, tmp_path: Path) -> None:
        pkg = make_pkg()
        inbox_file = write_inbox(tmp_path, pkg.task_id)
        fast_provider(tmp_path).execute(pkg)
        assert not inbox_file.exists()
        assert (tmp_path / "archive" / f"{pkg.task_id}.json").exists()

    def test_no_double_consumption(self, tmp_path: Path) -> None:
        pkg = make_pkg()
        write_inbox(tmp_path, pkg.task_id)
        provider = fast_provider(tmp_path)
        provider.execute(pkg)
        # Same task_id again: the archived reply must not be re-consumed.
        with pytest.raises(BridgeTimeoutError):
            provider.execute(pkg)


class TestEmitOnlyDemo:
    def test_emit_only_writes_outbox_without_waiting(self, tmp_path: Path) -> None:
        from cloud import bridge_demo

        rc = bridge_demo.main(
            ["--emit-only", "--task-id", "demo-t1", "--bridge-dir", str(tmp_path)]
        )
        assert rc == 0
        outbox_file = tmp_path / "outbox" / "demo-t1.json"
        assert outbox_file.exists()
        raw = outbox_file.read_text(encoding="utf-8")
        assert "13812345678" not in raw  # demo payload's fake PII was sanitized
        assert "[PHONE]" in raw

    def test_demo_task_package_is_valid(self) -> None:
        from cloud import bridge_demo
        from cloud.contracts import validate_package

        validate_package(bridge_demo.build_task("demo-check"))
