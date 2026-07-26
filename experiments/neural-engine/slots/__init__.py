"""Writable Engram slot-table service (design doc §1.2, P0)."""

from .config import SlotConfig
from .service import SlotService
from .store import VALID_STATUSES, Slot, SlotStore

__all__ = ["SlotConfig", "SlotService", "Slot", "SlotStore", "VALID_STATUSES"]
