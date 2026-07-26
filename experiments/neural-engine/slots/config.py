"""Tunable constants for the Engram slot-table service (design doc §1.2).

All retrieval/write-path hyper-parameters live here so experiments can
override them via a single frozen dataclass instead of editing module code.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class SlotConfig:
    # Retrieval scoring: alpha = a_sem * a_rep**beta_exp * a_tmp**gamma_exp.
    beta_exp: float = 1.0  # BETA: reputation exponent
    gamma_exp: float = 1.0  # GAMMA: temporal-consistency exponent
    theta: float = 0.2  # THETA: acceptance threshold for a retrieved slot
    retrieve_k: int = 8  # FAISS candidate count per query

    # Write path.
    sim_threshold: float = 0.85  # cosine gate between reinforce/compete and allocate
    ema_alpha: float = 0.1  # value_vec EMA rate for reinforce
    rep_gap: float = 0.2  # reputation lean margin for the compete conflict rule

    # Temporal consistency: size of the recent-outcome sliding window.
    a_tmp_window: int = 20

    # Vector dimensions (design doc §1.2 schema).
    key_dim: int = 256
    value_dim: int = 1024
