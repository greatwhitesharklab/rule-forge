"""Rolling GBDT scorer: the P1 champion baseline (design §8.7).

Per episode t the scorer:
  1. trains a LightGBM binary classifier on ALL matured history visible at t
     (optionally limited to the most recent `window_episodes`), and
  2. scores episode t's fresh CaseBook cases (no labels) into P(bad).

Time red-line (§8.3 rule 2): training labels come exclusively from the
`visible_episode <= t` filter — the exact predicate `matured_view` applies.
A label whose visibility lies in the future physically exists in the ledger
array but can never enter a training batch, because the mask is computed
from `visible_episode` before any label is read.

Cold start: with too few matured samples (or a single-class label set) the
scorer abstains — `proba=None`, `cold_start=True` — instead of guessing.

Metrics are retrospective: episode t's AUC/KS/logloss are recomputed only
from outcomes visible by the evaluation horizon.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable

import lightgbm as lgb
import numpy as np
import pandas as pd
from sklearn.metrics import log_loss, roc_auc_score

from scoring.features import FeatureRegistry
from synth.world import WorldData

TrainHook = Callable[[int, np.ndarray, np.ndarray], None]

_DEFAULT_LGBM: dict[str, object] = {
    "n_estimators": 120,
    "learning_rate": 0.08,
    "num_leaves": 15,
    "min_child_samples": 40,
    "subsample": 0.9,
    "subsample_freq": 1,
    "colsample_bytree": 0.9,
    "random_state": 20260726,
    "deterministic": True,
    "force_col_wise": True,
    "n_jobs": 1,
    "verbose": -1,
}


@dataclass(frozen=True)
class ScorerConfig:
    """Knobs for the rolling scorer."""

    min_train_samples: int = 200  # below this -> cold start (abstain)
    window_episodes: int | None = 30  # cap training to recent N episodes (None = all)
    lgbm_params: dict[str, object] = field(
        default_factory=lambda: dict(_DEFAULT_LGBM))
    # Optional observation hook (episode, X, y) per fit — used by tests to
    # prove the time red-line, and by experiments to trace training sets.
    train_hook: TrainHook | None = None


@dataclass
class ScoreBatch:
    """Scores for one episode's fresh cases."""

    episode: int
    case_ids: np.ndarray  # [N] int64
    proba: np.ndarray | None  # [N] float64 P(bad); None under cold start
    cold_start: bool
    n_train: int  # matured samples used for this episode's model


@dataclass
class RollingResult:
    """Per-episode score batches plus retrospective evaluation helpers."""

    batches: list[ScoreBatch]

    def scores_frame(self) -> pd.DataFrame:
        """case_id / episode / proba (NaN where cold start)."""
        rows = []
        for b in self.batches:
            p = b.proba if b.proba is not None else np.full(len(b.case_ids), np.nan)
            rows.append(pd.DataFrame(
                {"case_id": b.case_ids, "episode": b.episode, "proba": p}))
        return pd.concat(rows, ignore_index=True) if rows else pd.DataFrame(
            columns=["case_id", "episode", "proba"])

    def metrics_frame(self, data: WorldData,
                      upto_episode: int | None = None) -> pd.DataFrame:
        """Per-episode AUC/KS/logloss, recomputed from outcomes visible by
        `upto_episode` (default: last episode of the run)."""
        horizon = (int(data.casebook.episode.max()) if upto_episode is None
                   else upto_episode)
        rows = []
        for b in self.batches:
            if b.proba is None:
                continue
            visible = data.ledger.visible_episode[b.case_ids] <= horizon
            y = data.ledger.outcome[b.case_ids][visible]
            p = b.proba[visible]
            if len(np.unique(y)) < 2:
                continue  # AUC undefined on a single-class episode
            rows.append({
                "episode": b.episode,
                "n": int(len(y)),
                "auc": float(roc_auc_score(y, p)),
                "ks": _ks_stat(y, p),
                "logloss": float(log_loss(y, p, labels=[0, 1])),
            })
        return pd.DataFrame(rows, columns=["episode", "n", "auc", "ks", "logloss"])


def _ks_stat(y: np.ndarray, p: np.ndarray) -> float:
    """Kolmogorov-Smirnov separation between bad/good score distributions."""
    order = np.argsort(p)
    y_sorted = y[order]
    n_bad = max(int(y_sorted.sum()), 1)
    n_good = max(int((1 - y_sorted).sum()), 1)
    cum_bad = np.cumsum(y_sorted) / n_bad
    cum_good = np.cumsum(1 - y_sorted) / n_good
    return float(np.max(np.abs(cum_bad - cum_good)))


class RollingScorer:
    """Train-on-matured-history, score-fresh-cases, per episode."""

    def __init__(self, registry: FeatureRegistry,
                 config: ScorerConfig | None = None) -> None:
        self.registry = registry
        self.config = config or ScorerConfig()
        # The single feature entry point for BOTH phases (§8.3 rule 1).
        self._compute = registry.compute

    def run(self, data: WorldData) -> RollingResult:
        cb = data.casebook
        n_episodes = int(cb.episode.max()) + 1
        raw = pd.DataFrame(cb.observables, columns=cb.observable_names)
        batches: list[ScoreBatch] = []
        for t in range(n_episodes):
            # Exactly the matured_view(t) predicate — the only time-safe join.
            train_mask = data.ledger.visible_mask(t)
            if self.config.window_episodes is not None:
                lo = max(0, t - self.config.window_episodes + 1)
                train_mask = train_mask & (cb.episode >= lo)
            n_train = int(train_mask.sum())
            y = data.ledger.outcome[train_mask]
            new_mask = cb.episode == t
            case_ids = cb.case_ids[new_mask]
            if n_train < self.config.min_train_samples or len(np.unique(y)) < 2:
                batches.append(ScoreBatch(t, case_ids, None, True, n_train))
                continue
            x_train = self._compute(raw[train_mask])
            if self.config.train_hook is not None:
                self.config.train_hook(t, x_train.to_numpy(), y)
            model = lgb.LGBMClassifier(**self.config.lgbm_params)
            model.fit(x_train, y)
            x_new = self._compute(raw[new_mask])
            proba = model.predict_proba(x_new)[:, 1]
            batches.append(ScoreBatch(t, case_ids, proba, False, n_train))
        return RollingResult(batches)
