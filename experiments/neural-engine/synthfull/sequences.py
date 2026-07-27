"""Behavior-sequence generation for CLAB-full.

Each case gets one event sequence: event types from EVENT_VOCAB plus a
duration per event. Sequences are generated from a LATENT behavior mode
(normal / hesitant / instant / batch); the mode id is returned for the
ground-truth side only and never enters the feature side.

Everything is vectorized over the batch. Fixed RNG draw order per call:
mode -> length -> event uniforms -> duration normals.

Padded layout: events/durations are [n, MAX_SEQ_LEN]; positions at or beyond
`lengths` carry event id -1 and duration 0. The final valid event of every
sequence is forced to `submit` (an application always ends in a submission).
"""

from __future__ import annotations

import numpy as np

from synthfull.config import (
    EVENT_DUR,
    EVENT_VOCAB,
    MAX_SEQ_LEN,
    MODES,
    SEQ_STAT_NAMES,
    SUBMIT_EVENT,
    ModeSpec,
)

_MU = np.array([d[0] for d in EVENT_DUR])
_SIGMA = np.array([d[1] for d in EVENT_DUR])
_N_EVENTS = len(EVENT_VOCAB)

# Event ids used by the sequence statistics.
_PASTE = EVENT_VOCAB.index("paste")
_BACKSPACE = EVENT_VOCAB.index("backspace")
_IDLE = EVENT_VOCAB.index("idle_long")
_EDIT = EVENT_VOCAB.index("field_edit")
_FOCUS = EVENT_VOCAB.index("field_focus")


def sample_sequences(
    rng: np.random.Generator,
    n: int,
    modes: tuple[ModeSpec, ...] = MODES,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    """Draw n sequences.

    Returns (events int8 [n, MAX_SEQ_LEN], durations float32 [n, MAX_SEQ_LEN],
    lengths int32 [n], mode ids int8 [n]).
    """
    mode_probs = np.array([m.prob for m in modes])
    mode_probs /= mode_probs.sum()
    mode_cum = np.cumsum(mode_probs)
    event_cum = np.cumsum(np.array([m.event_probs for m in modes]), axis=1)
    lows = np.array([m.len_range[0] for m in modes])
    highs = np.array([m.len_range[1] for m in modes])
    speeds = np.array([m.speed for m in modes])

    # 1) latent mode per case; 2) sequence length within the mode's range.
    mode = np.searchsorted(mode_cum, rng.random(n), side="right")
    mode = np.minimum(mode, len(modes) - 1)
    lengths = rng.integers(lows[mode], highs[mode] + 1)

    # 3) event types via inverse-CDF against the mode's event distribution.
    u = rng.random((n, MAX_SEQ_LEN))
    cum = event_cum[mode]  # [n, V]
    events = (u[:, :, None] >= cum[:, None, :]).sum(axis=2)
    events = np.minimum(events, _N_EVENTS - 1).astype(np.int8)

    # 4) durations: lognormal per event type, scaled by the mode's speed.
    z = rng.normal(size=(n, MAX_SEQ_LEN))
    ev = events.astype(np.intp)
    durations = np.exp(_MU[ev] + np.log(speeds[mode])[:, None] + _SIGMA[ev] * z)

    # Padding + terminal submit (lengths are always >= 4 per mode config).
    pos = np.arange(MAX_SEQ_LEN)[None, :]
    valid = pos < lengths[:, None]
    events[~valid] = -1
    durations[~valid] = 0.0
    events[np.arange(n), lengths - 1] = SUBMIT_EVENT
    return events, durations.astype(np.float32), lengths.astype(np.int32), mode.astype(np.int8)


def seq_stats(
    events: np.ndarray, durations: np.ndarray, lengths: np.ndarray
) -> np.ndarray:
    """Sequence-level statistics [n, len(SEQ_STAT_NAMES)] float64.

    Pure function of decision-time features (no draws); this is the exact
    feature set the random rule generator may condition on.
    """
    valid = events >= 0
    counts = [
        (events == _PASTE).sum(axis=1),
        (events == _BACKSPACE).sum(axis=1),
        (events == _IDLE).sum(axis=1),
        (events == _EDIT).sum(axis=1),
        (events == _FOCUS).sum(axis=1),
    ]
    total = durations.sum(axis=1)
    n_len = np.maximum(lengths, 1).astype(np.float64)
    cols = [
        lengths.astype(np.float64),
        *[c.astype(np.float64) for c in counts],
        total,
        total / n_len,
        np.where(valid, durations, 0.0).max(axis=1),
        counts[0] / n_len,
    ]
    return np.stack(cols, axis=1)


SEQ_STAT_INDEX: dict[str, int] = {s: i for i, s in enumerate(SEQ_STAT_NAMES)}
