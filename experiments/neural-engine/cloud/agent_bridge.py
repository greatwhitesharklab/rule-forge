"""Agent bridge provider: a local AI agent (e.g. Kimi CLI) acts as the cloud.

Instead of HTTP, ``_transport`` drops the sanitized task package into
``bridge/outbox/{task_id}.json`` and polls ``bridge/inbox/{task_id}.json``
for the agent's reply. The full sanitize → outbound-gate → validate →
provenance chain of ``SanitizedCloudExecutor`` applies unchanged — the bridge
is just another transport.

Protocol (see bridge/README.md): the agent writes
``{"model": ..., "model_version": ..., "content": <output_schema JSON>}``
into the inbox; consumed replies are moved to ``bridge/archive/`` so they are
never read twice.
"""

from __future__ import annotations

import json
import shutil
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .contracts import TaskPackage, render_prompt, validate_package
from .providers import SanitizedCloudExecutor, TransportResponse
from .sanitize import sanitize_text, verify_outbound


class BridgeTimeoutError(TimeoutError):
    """Raised when no inbox reply arrives within the configured timeout."""


class BridgeProtocolError(RuntimeError):
    """Raised when an inbox file does not follow the bridge protocol."""


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


class AgentBridgeProvider(SanitizedCloudExecutor):
    """File-system bridge to a human/AI agent acting as the cloud executor."""

    def __init__(
        self,
        *,
        bridge_dir: str | Path | None = None,
        provider_name: str = "kimi-cli",
        timeout_s: float = 600.0,
        poll_interval_s: float = 2.0,
        **kwargs: Any,
    ) -> None:
        super().__init__(**kwargs)
        self.provider_name = provider_name
        # Overridden per call from the inbox file; "unknown" when undeclared.
        self.model_name = "unknown"
        root = Path(bridge_dir) if bridge_dir is not None else Path(__file__).parent / "bridge"
        self._outbox = root / "outbox"
        self._inbox = root / "inbox"
        self._archive = root / "archive"
        for d in (self._outbox, self._inbox, self._archive):
            d.mkdir(parents=True, exist_ok=True)
        self._timeout_s = timeout_s
        self._poll_interval_s = poll_interval_s

    # -- outbound ------------------------------------------------------------

    def emit(self, task: TaskPackage) -> Path:
        """Validate → sanitize → gate → write outbox, without waiting for a reply.

        Used by ``bridge_demo.py --emit-only`` for offline demos. Mirrors the
        outbound half of ``execute`` (which stays the canonical path).
        """
        validate_package(task)
        safe_prompt = sanitize_text(
            render_prompt(task),
            reference_date=self._reference_date,
            ner_hook=self._ner_hook,
        )
        verify_outbound(safe_prompt, alert_hook=self._alert_hook)
        return self._write_outbox(task, safe_prompt)

    def _write_outbox(self, task: TaskPackage, safe_prompt: str) -> Path:
        path = self._outbox / f"{task.task_id}.json"
        payload = {
            "task_id": task.task_id,
            "task_type": task.task_type,
            "timestamp": _utc_now(),
            "constraints": task.constraints,
            "output_schema": task.output_schema,
            "prompt": safe_prompt,
        }
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        print(
            f"[agent-bridge] task package pending: {path}\n"
            f"[agent-bridge] agent: write the result to {self._inbox / (task.task_id + '.json')}"
            " (protocol: bridge/README.md)"
        )
        return path

    # -- transport (template-method hook) -------------------------------------

    def _transport(self, safe_prompt: str, task: TaskPackage) -> TransportResponse:
        self._write_outbox(task, safe_prompt)
        inbox_path = self._inbox / f"{task.task_id}.json"
        deadline = time.monotonic() + self._timeout_s
        while True:
            if inbox_path.exists():
                try:
                    return self._consume(inbox_path)
                except json.JSONDecodeError:
                    pass  # partially written file — keep polling
            if time.monotonic() >= deadline:
                raise BridgeTimeoutError(
                    f"agent bridge timed out after {self._timeout_s:.0f}s "
                    f"waiting for {inbox_path}"
                )
            time.sleep(self._poll_interval_s)

    def _consume(self, inbox_path: Path) -> TransportResponse:
        data = json.loads(inbox_path.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or "content" not in data:
            raise BridgeProtocolError(
                f"inbox file {inbox_path} must be a JSON object with a 'content' key"
            )
        content = data["content"]
        if not isinstance(content, dict):
            raise BridgeProtocolError(f"inbox file {inbox_path}: 'content' must be a JSON object")
        # Per-call model identity declared by the agent; read by execute().
        self.model_name = str(data.get("model") or "unknown")
        model_version = str(data.get("model_version") or "unknown")
        usage = data.get("usage") or {}
        self._archive_file(inbox_path)
        return TransportResponse(
            content=json.dumps(content, ensure_ascii=False),
            model_version=model_version,
            input_tokens=int(usage.get("input_tokens", 0) or 0),
            output_tokens=int(usage.get("output_tokens", 0) or 0),
        )

    def _archive_file(self, inbox_path: Path) -> None:
        dest = self._archive / inbox_path.name
        if dest.exists():  # same task_id re-run: keep both, suffix the old one
            dest = dest.with_name(f"{inbox_path.stem}.{int(time.time())}.json")
        shutil.move(str(inbox_path), str(dest))
