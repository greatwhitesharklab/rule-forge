"""Demo: send a feature_proposal task package out through the agent bridge.

Usage:
  uv run python experiments/neural-engine/cloud/bridge_demo.py --emit-only
      Write the sanitized package to bridge/outbox/ and exit (offline demo).
  uv run python experiments/neural-engine/cloud/bridge_demo.py
      Write the package, then wait for the agent's inbox reply and print the
      resulting TaskResult (content + provenance).

The demo payload deliberately contains fake PII so the outbound printout shows
the sanitize chain working end to end.
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

# Mirror tests/conftest.py: make the cloud package importable when run as a script.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from cloud.agent_bridge import AgentBridgeProvider  # noqa: E402
from cloud.contracts import TaskPackage  # noqa: E402


def build_task(task_id: str) -> TaskPackage:
    """A feature_proposal package with synthetic profiles and fake PII."""
    return TaskPackage(
        task_id=task_id,
        task_type="feature_proposal",
        context={
            "case_profiles": [
                {
                    "profile_id": "p-001",
                    "note": "姓名：张伟，手机13812345678，申请金额12万元，放款日2024-03-05",
                    "bad_rate": 0.18,
                    "debt_ratio": 0.72,
                    "income_cv_30d": 0.41,
                },
                {
                    "profile_id": "p-002",
                    "note": "身份证号110101199003070077，住址北京市朝阳区建国路88号",
                    "bad_rate": 0.06,
                    "debt_ratio": 0.35,
                    "income_cv_30d": 0.12,
                },
            ],
            "existing_features": ["debt_ratio", "income_mean_30d", "flow_cv_30d"],
            "dead_ends": [
                "income_x_region — 区分度不足 (IV=0.04)",
                "app_open_count_7d — regime 不稳 (PSI=0.31)",
            ],
        },
        constraints={
            "max_features": 5,
            "must_be_executable": "python",
            "no_future_info": True,
        },
        output_schema={"features": [{"name": "str", "expression": "str", "rationale": "str"}]},
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--emit-only",
        action="store_true",
        help="write the outbox package and exit without waiting for a reply",
    )
    parser.add_argument(
        "--task-id",
        default="demo-" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S"),
        help="task id (file name) for the outbox/inbox pair",
    )
    parser.add_argument(
        "--bridge-dir",
        type=Path,
        default=None,
        help="bridge root directory (default: cloud/bridge/)",
    )
    parser.add_argument("--timeout", type=float, default=600.0, help="reply timeout in seconds")
    parser.add_argument("--poll-interval", type=float, default=2.0, help="poll interval in seconds")
    args = parser.parse_args(argv)

    provider = AgentBridgeProvider(
        bridge_dir=args.bridge_dir,
        timeout_s=args.timeout,
        poll_interval_s=args.poll_interval,
    )
    task = build_task(args.task_id)

    if args.emit_only:
        path = provider.emit(task)
        print(f"[demo] sanitized package written: {path}")
        print("[demo] agent: read it, write the result JSON to the inbox dir")
        print("[demo] protocol: experiments/neural-engine/cloud/bridge/README.md")
        return 0

    result = provider.execute(task)
    print(json.dumps(result.content, ensure_ascii=False, indent=2))
    print("provenance:", json.dumps(result.provenance.as_dict(), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
