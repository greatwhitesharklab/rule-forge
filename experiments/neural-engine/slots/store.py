"""SQLite metadata + FAISS vector index persistence for the slot table.

Layout on disk (all paths derived from the SQLite path):
- ``slots.db``        SQLite metadata: slots / attribution / slot_events tables
- ``slots.db.faiss``  FAISS IndexFlatIP over L2-normalized key_vec, wrapped in
                      IndexIDMap so FAISS ids equal SQLite slot_ids

key_vec is stored L2-normalized, so IndexFlatIP inner products are cosine
similarities. The FAISS index is a pure derivative of the slots table: if the
index file is lost it is rebuilt from SQLite on open.
"""

from __future__ import annotations

import json
import sqlite3
from dataclasses import dataclass
from pathlib import Path

import faiss
import numpy as np

SCHEMA = """
CREATE TABLE IF NOT EXISTS slots (
  slot_id      INTEGER PRIMARY KEY,
  key_vec      BLOB NOT NULL,        -- float32[256] L2-normalized semantic key
  value_text   TEXT NOT NULL,        -- human-readable experience statement
  value_vec    BLOB NOT NULL,        -- float32[1024] content vector
  beta_a       REAL NOT NULL DEFAULT 1.0,
  beta_b       REAL NOT NULL DEFAULT 1.0,
  status       TEXT NOT NULL DEFAULT 'candidate',
  regime_tag   TEXT NOT NULL DEFAULT '',
  created_at   TEXT NOT NULL,
  last_used_at TEXT,
  use_count    INTEGER NOT NULL DEFAULT 0,
  provenance   TEXT NOT NULL DEFAULT '[]'  -- JSON list of source case ids
);
CREATE TABLE IF NOT EXISTS attribution (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  case_id    TEXT NOT NULL,
  slot_id    INTEGER NOT NULL,
  alpha      REAL NOT NULL,
  a_sem      REAL NOT NULL,
  a_rep      REAL NOT NULL,
  a_tmp      REAL NOT NULL,
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS slot_events (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  slot_id    INTEGER NOT NULL,
  hit        INTEGER NOT NULL,       -- 1 = good outcome, 0 = bad outcome
  created_at TEXT NOT NULL
);
"""

VALID_STATUSES = ("candidate", "shadow", "active", "cooling", "retired")


def to_blob(vec: np.ndarray) -> bytes:
    return np.ascontiguousarray(vec, dtype=np.float32).tobytes()


def from_blob(blob: bytes) -> np.ndarray:
    return np.frombuffer(blob, dtype=np.float32).copy()


def normalize(vec: np.ndarray) -> np.ndarray:
    arr = np.asarray(vec, dtype=np.float32)
    return arr / (np.linalg.norm(arr) + 1e-12)


@dataclass
class Slot:
    slot_id: int
    key_vec: np.ndarray
    value_text: str
    value_vec: np.ndarray
    beta_a: float
    beta_b: float
    status: str
    regime_tag: str
    created_at: str
    last_used_at: str | None
    use_count: int
    provenance: list[str]

    @property
    def reputation(self) -> float:
        """Beta posterior mean a_rep = beta_a / (beta_a + beta_b)."""
        return self.beta_a / (self.beta_a + self.beta_b)


def _row_to_slot(row: sqlite3.Row) -> Slot:
    return Slot(
        slot_id=row["slot_id"],
        key_vec=from_blob(row["key_vec"]),
        value_text=row["value_text"],
        value_vec=from_blob(row["value_vec"]),
        beta_a=row["beta_a"],
        beta_b=row["beta_b"],
        status=row["status"],
        regime_tag=row["regime_tag"],
        created_at=row["created_at"],
        last_used_at=row["last_used_at"],
        use_count=row["use_count"],
        provenance=json.loads(row["provenance"]),
    )


class SlotStore:
    def __init__(self, db_path: Path | str, key_dim: int = 256):
        self.db_path = Path(db_path)
        self.faiss_path = self.db_path.with_name(self.db_path.name + ".faiss")
        self.key_dim = key_dim
        self.conn = sqlite3.connect(str(self.db_path))
        self.conn.row_factory = sqlite3.Row
        self.conn.executescript(SCHEMA)
        if self.faiss_path.exists():
            self.index = faiss.read_index(str(self.faiss_path))
        else:
            self.index = self._fresh_index()
            self._rebuild_index()

    def _fresh_index(self) -> faiss.Index:
        return faiss.IndexIDMap(faiss.IndexFlatIP(self.key_dim))

    def _rebuild_index(self) -> None:
        for slot in self.all_slots():
            self._index_add(slot.slot_id, slot.key_vec)

    def _index_add(self, slot_id: int, key_vec: np.ndarray) -> None:
        self.index.add_with_ids(
            key_vec.reshape(1, -1).astype(np.float32),
            np.array([slot_id], dtype=np.int64),
        )

    # ------------------------------------------------------------------ slots

    def insert_slot(
        self,
        key_vec: np.ndarray,
        value_text: str,
        value_vec: np.ndarray,
        status: str,
        regime_tag: str,
        created_at: str,
        provenance: list[str],
        beta_a: float = 1.0,
        beta_b: float = 1.0,
    ) -> int:
        cur = self.conn.execute(
            "INSERT INTO slots (key_vec, value_text, value_vec, beta_a, beta_b,"
            " status, regime_tag, created_at, provenance)"
            " VALUES (?,?,?,?,?,?,?,?,?)",
            (
                to_blob(key_vec),
                value_text,
                to_blob(value_vec),
                beta_a,
                beta_b,
                status,
                regime_tag,
                created_at,
                json.dumps(provenance),
            ),
        )
        self.conn.commit()
        slot_id = int(cur.lastrowid)
        self._index_add(slot_id, key_vec)
        return slot_id

    def get_slot(self, slot_id: int) -> Slot | None:
        row = self.conn.execute(
            "SELECT * FROM slots WHERE slot_id = ?", (slot_id,)
        ).fetchone()
        return _row_to_slot(row) if row else None

    def all_slots(self) -> list[Slot]:
        rows = self.conn.execute("SELECT * FROM slots ORDER BY slot_id").fetchall()
        return [_row_to_slot(r) for r in rows]

    def update_value_vec(self, slot_id: int, value_vec: np.ndarray) -> None:
        self.conn.execute(
            "UPDATE slots SET value_vec = ? WHERE slot_id = ?",
            (to_blob(value_vec), slot_id),
        )
        self.conn.commit()

    def update_betas(self, slot_id: int, beta_a: float, beta_b: float) -> None:
        self.conn.execute(
            "UPDATE slots SET beta_a = ?, beta_b = ? WHERE slot_id = ?",
            (beta_a, beta_b, slot_id),
        )
        self.conn.commit()

    def append_provenance(self, slot_id: int, case_id: str) -> None:
        slot = self.get_slot(slot_id)
        self.conn.execute(
            "UPDATE slots SET provenance = ? WHERE slot_id = ?",
            (json.dumps(slot.provenance + [case_id]), slot_id),
        )
        self.conn.commit()

    def set_status(self, slot_id: int, status: str) -> None:
        self.conn.execute(
            "UPDATE slots SET status = ? WHERE slot_id = ?", (status, slot_id)
        )
        self.conn.commit()

    def touch(self, slot_id: int, ts: str) -> None:
        self.conn.execute(
            "UPDATE slots SET use_count = use_count + 1, last_used_at = ?"
            " WHERE slot_id = ?",
            (ts, slot_id),
        )
        self.conn.commit()

    # ------------------------------------------------------------ attribution

    def add_attribution(
        self,
        case_id: str,
        slot_id: int,
        alpha: float,
        a_sem: float,
        a_rep: float,
        a_tmp: float,
        ts: str,
    ) -> None:
        self.conn.execute(
            "INSERT INTO attribution"
            " (case_id, slot_id, alpha, a_sem, a_rep, a_tmp, created_at)"
            " VALUES (?,?,?,?,?,?,?)",
            (case_id, slot_id, alpha, a_sem, a_rep, a_tmp, ts),
        )
        self.conn.commit()

    def attributions_for(self, case_id: str) -> list[sqlite3.Row]:
        return self.conn.execute(
            "SELECT * FROM attribution WHERE case_id = ? ORDER BY id", (case_id,)
        ).fetchall()

    def all_attributions(self) -> list[sqlite3.Row]:
        return self.conn.execute("SELECT * FROM attribution ORDER BY id").fetchall()

    # ----------------------------------------------------------------- events

    def add_event(self, slot_id: int, hit: int, ts: str) -> None:
        self.conn.execute(
            "INSERT INTO slot_events (slot_id, hit, created_at) VALUES (?,?,?)",
            (slot_id, hit, ts),
        )
        self.conn.commit()

    def recent_events(self, slot_id: int, limit: int) -> list[int]:
        rows = self.conn.execute(
            "SELECT hit FROM slot_events WHERE slot_id = ? ORDER BY id DESC LIMIT ?",
            (slot_id, limit),
        ).fetchall()
        return [r["hit"] for r in rows]

    # ------------------------------------------------------------------ index

    def search(self, vec: np.ndarray, k: int) -> list[tuple[int, float]]:
        """Cosine similarity search; returns [(slot_id, score)] descending."""
        if self.index.ntotal == 0:
            return []
        query = normalize(vec).reshape(1, -1)
        scores, ids = self.index.search(query, min(k, self.index.ntotal))
        return [
            (int(i), float(s))
            for i, s in zip(ids[0], scores[0])
            if i != -1
        ]

    # ------------------------------------------------------------ persistence

    def persist(self) -> None:
        self.conn.commit()
        faiss.write_index(self.index, str(self.faiss_path))

    def close(self) -> None:
        self.conn.close()
