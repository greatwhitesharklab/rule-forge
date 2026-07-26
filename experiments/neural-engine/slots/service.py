"""Engram slot-table service: online retrieval + nightly write path (§1.2).

Online (read-only w.r.t. experience): retrieve() scores FAISS candidates by
``alpha = a_sem * a_rep**BETA * a_tmp**GAMMA`` and logs an attribution record
per accepted slot — the attribution log is what credit_assignment() reads when
outcomes flow back.

Nightly consolidation: allocate / reinforce / compete write slots (no
overwrite-style updates — reinforce only EMA-blends value_vec and appends
provenance), credit_assignment() folds matured outcomes into Beta reputation.

Every mutation goes through a ``_apply_*`` method and is WAL-appended, so
SlotService.rebuild() can replay the WAL onto an empty database and reproduce
the exact memory state (timestamps included — each WAL record carries its ts).
"""

from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal

import numpy as np

from .config import SlotConfig
from .store import VALID_STATUSES, Slot, SlotStore, normalize
from .wal import WalReader, WalWriter

Outcome = Literal["good", "bad"]


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _as_list(vec: np.ndarray) -> list[float]:
    return [float(x) for x in np.asarray(vec, dtype=np.float32)]


class SlotService:
    def __init__(self, db_path: str | Path, config: SlotConfig | None = None):
        self.cfg = config or SlotConfig()
        self.store = SlotStore(Path(db_path), self.cfg.key_dim)
        self.wal = WalWriter(str(db_path) + ".wal.jsonl")

    @classmethod
    def rebuild(
        cls,
        db_path: str | Path,
        wal_path: str | Path,
        config: SlotConfig | None = None,
    ) -> "SlotService":
        """Rebuild full memory state onto an empty database by replaying a WAL."""
        svc = cls(db_path, config)
        for rec in WalReader(wal_path):
            svc._apply_record(rec)
        svc.persist()
        return svc

    # ------------------------------------------------------------------ read path

    def retrieve(
        self,
        case_embedding: np.ndarray,
        case_id: str,
        k: int | None = None,
    ) -> list[tuple[Slot, float]]:
        """Score candidates by alpha and log attribution for accepted slots."""
        out: list[tuple[Slot, float]] = []
        ts = _now()
        for slot_id, a_sem in self.store.search(case_embedding, k or self.cfg.retrieve_k):
            slot = self.store.get_slot(slot_id)
            if slot is None:
                continue
            a_rep = slot.reputation
            a_tmp = self._a_tmp(slot_id)
            alpha = a_sem * a_rep**self.cfg.beta_exp * a_tmp**self.cfg.gamma_exp
            if alpha > self.cfg.theta:
                out.append((slot, alpha))
                rec = {
                    "op": "attribution",
                    "ts": ts,
                    "case_id": case_id,
                    "slot_id": slot_id,
                    "alpha": alpha,
                    "a_sem": a_sem,
                    "a_rep": a_rep,
                    "a_tmp": a_tmp,
                }
                self._apply_attribution(rec)
                self.wal.append(rec)
        return out

    def _a_tmp(self, slot_id: int) -> float:
        """Temporal consistency: Laplace-smoothed hit-rate over the slot's most
        recent `a_tmp_window` outcome events (1 = good, 0 = bad, appended by
        credit_assignment). A slot with no events yet gets the uninformative 0.5."""
        events = self.store.recent_events(slot_id, self.cfg.a_tmp_window)
        return (sum(events) + 1.0) / (len(events) + 2.0)

    # ----------------------------------------------------------------- write path

    def write_slot(
        self,
        key_vec: np.ndarray,
        value_vec: np.ndarray,
        value_text: str,
        outcome: Outcome | None,
        case_id: str,
        regime_tag: str = "",
    ) -> tuple[str, Slot]:
        """Nightly Scribe dispatch: allocate / reinforce / compete."""
        hits = self.store.search(key_vec, k=1)
        if not hits or hits[0][1] <= self.cfg.sim_threshold:
            return "allocate", self.allocate(
                key_vec, value_vec, value_text, case_id, regime_tag
            )
        slot = self.store.get_slot(hits[0][0])
        if outcome is not None and self._conflicts(slot, outcome):
            return "compete", self.compete(
                slot.slot_id, key_vec, value_vec, value_text, case_id, regime_tag
            )
        return "reinforce", self.reinforce(slot.slot_id, value_vec, case_id)

    def _conflicts(self, slot: Slot, outcome: Outcome) -> bool:
        """Outcome-conflict rule (compete trigger):
        rep = beta_a/(beta_a+beta_b); a slot 'leans good' when
        rep >= 0.5 + rep_gap and 'leans bad' when rep <= 0.5 - rep_gap.
        Conflict iff the slot leans one way and the new case's outcome is the
        opposite. Neutral slots (|rep - 0.5| < rep_gap) never conflict and
        absorb the case via reinforce."""
        rep = slot.reputation
        gap = self.cfg.rep_gap
        return (rep >= 0.5 + gap and outcome == "bad") or (
            rep <= 0.5 - gap and outcome == "good"
        )

    def allocate(
        self,
        key_vec: np.ndarray,
        value_vec: np.ndarray,
        value_text: str,
        case_id: str,
        regime_tag: str = "",
    ) -> Slot:
        rec = {
            "op": "allocate",
            "ts": _now(),
            "key_vec": _as_list(key_vec),
            "value_vec": _as_list(value_vec),
            "value_text": value_text,
            "case_id": case_id,
            "regime_tag": regime_tag,
        }
        slot_id = self._apply_allocate(rec)
        self.wal.append(rec)
        return self.store.get_slot(slot_id)

    def reinforce(self, slot_id: int, value_vec: np.ndarray, case_id: str) -> Slot:
        """EMA-blend the new case's value_vec in (rate ema_alpha) and append
        provenance. Reputation and status are untouched — reputation only moves
        via credit_assignment."""
        rec = {
            "op": "reinforce",
            "ts": _now(),
            "slot_id": slot_id,
            "value_vec": _as_list(value_vec),
            "case_id": case_id,
        }
        self._apply_reinforce(rec)
        self.wal.append(rec)
        return self.store.get_slot(slot_id)

    def compete(
        self,
        rival_slot_id: int,
        key_vec: np.ndarray,
        value_vec: np.ndarray,
        value_text: str,
        case_id: str,
        regime_tag: str = "",
    ) -> Slot:
        """Create a competing slot coexisting with the similar rival; the Beta
        reputation system arbitrates between them over time."""
        rec = {
            "op": "compete",
            "ts": _now(),
            "rival_slot_id": rival_slot_id,
            "key_vec": _as_list(key_vec),
            "value_vec": _as_list(value_vec),
            "value_text": value_text,
            "case_id": case_id,
            "regime_tag": regime_tag,
        }
        slot_id = self._apply_compete(rec)
        self.wal.append(rec)
        return self.store.get_slot(slot_id)

    def credit_assignment(
        self,
        case_id: str,
        outcome: Outcome,
        amount_weight: float = 1.0,
    ) -> None:
        """Fold a matured outcome into the Beta reputation of every slot that
        was attributed to the case, weighted by exposure * retrieval alpha."""
        rec = {
            "op": "credit",
            "ts": _now(),
            "case_id": case_id,
            "outcome": outcome,
            "amount_weight": float(amount_weight),
        }
        self._apply_credit(rec)
        self.wal.append(rec)

    def set_status(self, slot_id: int, status: str) -> None:
        """P0 status machine: transition API only, promotion gates come in P1."""
        if status not in VALID_STATUSES:
            raise ValueError(f"invalid slot status: {status!r}")
        rec = {"op": "status", "ts": _now(), "slot_id": slot_id, "status": status}
        self._apply_status(rec)
        self.wal.append(rec)

    # ------------------------------------------------------- WAL record apply

    def _apply_record(self, rec: dict[str, Any]) -> None:
        handler = {
            "allocate": self._apply_allocate,
            "reinforce": self._apply_reinforce,
            "compete": self._apply_compete,
            "credit": self._apply_credit,
            "status": self._apply_status,
            "attribution": self._apply_attribution,
        }.get(rec["op"])
        if handler is None:
            raise ValueError(f"unknown WAL op: {rec['op']!r}")
        handler(rec)

    def _new_slot_from_record(self, rec: dict[str, Any]) -> int:
        key = np.asarray(rec["key_vec"], dtype=np.float32)
        value = np.asarray(rec["value_vec"], dtype=np.float32)
        if key.shape != (self.cfg.key_dim,):
            raise ValueError(f"key_vec must be [{self.cfg.key_dim}], got {key.shape}")
        if value.shape != (self.cfg.value_dim,):
            raise ValueError(
                f"value_vec must be [{self.cfg.value_dim}], got {value.shape}"
            )
        return self.store.insert_slot(
            key_vec=normalize(key),
            value_text=rec["value_text"],
            value_vec=value,
            status="candidate",
            regime_tag=rec["regime_tag"],
            created_at=rec["ts"],
            provenance=[rec["case_id"]],
        )

    def _apply_allocate(self, rec: dict[str, Any]) -> int:
        return self._new_slot_from_record(rec)

    def _apply_compete(self, rec: dict[str, Any]) -> int:
        return self._new_slot_from_record(rec)

    def _apply_reinforce(self, rec: dict[str, Any]) -> None:
        slot = self.store.get_slot(rec["slot_id"])
        a = self.cfg.ema_alpha
        blended = (1.0 - a) * slot.value_vec + a * np.asarray(
            rec["value_vec"], dtype=np.float32
        )
        self.store.update_value_vec(rec["slot_id"], blended.astype(np.float32))
        self.store.append_provenance(rec["slot_id"], rec["case_id"])

    def _apply_credit(self, rec: dict[str, Any]) -> None:
        good = rec["outcome"] == "good"
        for row in self.store.attributions_for(rec["case_id"]):
            slot = self.store.get_slot(row["slot_id"])
            w = rec["amount_weight"] * row["alpha"]
            self.store.update_betas(
                slot.slot_id,
                slot.beta_a + (w if good else 0.0),
                slot.beta_b + (0.0 if good else w),
            )
            self.store.add_event(slot.slot_id, 1 if good else 0, rec["ts"])

    def _apply_status(self, rec: dict[str, Any]) -> None:
        self.store.set_status(rec["slot_id"], rec["status"])

    def _apply_attribution(self, rec: dict[str, Any]) -> None:
        self.store.add_attribution(
            rec["case_id"],
            rec["slot_id"],
            rec["alpha"],
            rec["a_sem"],
            rec["a_rep"],
            rec["a_tmp"],
            rec["ts"],
        )
        self.store.touch(rec["slot_id"], rec["ts"])

    # -------------------------------------------------------------- lifecycle

    def persist(self) -> None:
        self.store.persist()

    def close(self) -> None:
        self.wal.close()
        self.store.close()
