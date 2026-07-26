"""Append-only JSONL write-ahead log for slot-table mutations.

Every mutating op (allocate / reinforce / compete / credit / status /
attribution) is appended before it is considered done, so the full memory
state can be rebuilt from an empty database by replaying the WAL (design doc
§7 item 4: regulatory reproducibility of memory state at any point in time).
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterator


class WalWriter:
    def __init__(self, path: str | Path):
        self.path = Path(path)
        self._fh = self.path.open("a", encoding="utf-8")

    def append(self, record: dict[str, Any]) -> None:
        self._fh.write(json.dumps(record) + "\n")
        self._fh.flush()

    def close(self) -> None:
        self._fh.close()


class WalReader:
    def __init__(self, path: str | Path):
        self.path = Path(path)

    def __iter__(self) -> Iterator[dict[str, Any]]:
        with self.path.open("r", encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if line:
                    yield json.loads(line)
