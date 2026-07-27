"""Tunable constants for the Engram slot-table service (design doc §1.2).

All retrieval/write-path hyper-parameters live here so experiments can
override them via a single frozen dataclass instead of editing module code.

P1.1 design changes (driven by the P1 acceptance FAIL autopsy):

1. Reputation is DECOUPLED from the retrieval gate. The old formula
   ``alpha = a_sem * a_rep**BETA * a_tmp**GAMMA, alpha > THETA`` let
   bad-reputation slots fall below THETA forever: memory could vouch for
   good profiles but never warn about bad ones (root cause #1), and
   credit_assignment — weighted by the same alpha — under-blamed exactly
   the slots that were wrong, a divergence conspiracy. The new gate and
   weight are reputation-free; ``beta_exp`` is retired. Reputation now only
   shapes the SIGNAL consumers compute from hits (e.g. weighted rep_bad).
2. New slots initialize their Beta reputation from an injectable global
   prior ``Beta(lambda*(1-p), lambda*p)`` instead of ``Beta(1,1)``: a flat
   0.5 prior was systematically pessimistic in a ~10% bad-rate world and
   freshly written slots passed the old alpha gate immediately, killing
   good approvals (root cause #2). The posterior mean now shrinks to the
   observed bad rate under thin evidence and moves freely as credit lands.
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class SlotConfig:
    # Retrieval gate + hit weight (reputation-free, see module docstring):
    #   hit iff  a_sem * a_tmp**gamma_exp > theta
    #   weight   w = a_sem * a_tmp**gamma_exp   (also the credit weight)
    gamma_exp: float = 1.0  # GAMMA: temporal-consistency exponent
    theta: float = 0.2  # THETA: acceptance threshold for a retrieved slot
    retrieve_k: int = 8  # FAISS candidate count per query

    # Write path.
    sim_threshold: float = 0.85  # cosine gate between reinforce/compete and allocate
    ema_alpha: float = 0.1  # value_vec EMA rate for reinforce
    rep_gap: float = 0.2  # reputation lean margin for the compete conflict rule

    # Temporal consistency: size of the recent-outcome sliding window.
    a_tmp_window: int = 20

    # WAL: store (compressed) vectors in write-op records. False strips them —
    # much smaller WAL, but rebuild() from an empty database then fails on the
    # first stripped record: replay requires a SQLite snapshot taken before it
    # (no external encoder to re-derive vectors in P0/P1 scope). Default True
    # keeps the from-empty replay guarantee.
    wal_store_vectors: bool = True

    # New-slot Beta prior (P1.1): Beta(lambda*(1-p), lambda*p).
    prior_bad_rate: float = 0.1  # p: fallback global bad-rate prior
    prior_strength: float = 4.0  # lambda: pseudo-count mass of the prior

    # Vector dimensions (design doc §1.2 schema).
    key_dim: int = 256
    value_dim: int = 1024
