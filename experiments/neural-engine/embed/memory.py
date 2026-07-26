"""SemanticMemory facade: case rows -> SlotService (design doc §1.1/§1.2).

Pure composition of the slots public API — no slot internals touched:

  retrieve_for_case      = canonicalize -> embed_key -> SlotService.retrieve
  write_case_experience  = canonicalize -> embed key/value -> write_slot dispatch
                           (allocate / reinforce / compete decided by SlotService)

value_text is the canonical case text plus an outcome statement, so every
slot stays human-auditable.
"""

from __future__ import annotations

from typing import Mapping

from slots import Slot, SlotService
from slots.service import Outcome

from .canonicalize import canonicalize, experience_text
from .encoder import Embedder


class SemanticMemory:
    """Embedding-backed facade over a SlotService instance."""

    def __init__(self, service: SlotService, embedder: Embedder | None = None) -> None:
        self.service = service
        self.embedder = embedder or Embedder.default()

    def retrieve_for_case(
        self,
        case_row: Mapping[str, float],
        case_id: str,
        k: int | None = None,
    ) -> list[tuple[Slot, float]]:
        """Retrieve experience slots relevant to one case (read path)."""
        key = self.embedder.embed_keys([canonicalize(case_row)])[0]
        return self.service.retrieve(key, case_id=case_id, k=k)

    def write_case_experience(
        self,
        case_row: Mapping[str, float],
        outcome: Outcome | None,
        case_id: str,
        regime_tag: str = "",
    ) -> tuple[str, Slot]:
        """Write one case's experience (nightly write path).

        Returns SlotService's dispatch result: ("allocate" | "reinforce" |
        "compete", slot).
        """
        text = experience_text(case_row, outcome)
        key = self.embedder.embed_keys([canonicalize(case_row)])[0]
        value = self.embedder.embed_values([text])[0]
        return self.service.write_slot(
            key, value, text, outcome, case_id, regime_tag=regime_tag
        )
