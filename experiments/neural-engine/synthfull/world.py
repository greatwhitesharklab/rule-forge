"""FullWorld: the CLAB-full generative world.

Same skeleton as CLAB-lite (synth/world.py) — per-episode batches, regime
switching with Geometric gaps, outcomes delayed 1~3 episodes, CaseBook /
OutcomeLedger separation with a time-safe `matured_view` join — extended to
three modalities:

  * numeric observables (CLAB-lite factor layer),
  * high-cardinality Zipf categoricals (device_id / phone_prefix / region),
  * behavior event sequences (padded [n, MAX_SEQ_LEN] events + durations).

The causal layer is the RANDOM rule pool from RandomRuleGenerator; outcomes
depend on which rules fire: logit = base_logit + sum(fired weights) + noise.
Regime switches mutate a subset of rule weights (decay / boost / flip), and
categorical/sequence rules drift exactly like numeric ones.

Reproducibility: ONE seeded Generator for the world, consumed in a fixed
order per episode: regime switch, factors, observables, categoricals,
sequences, noise, outcome, delay. (The rule generator has its own stream,
exhausted before the world's first draw.) Same seed -> bit-identical output.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from synthfull.categories import CategoricalSampler
from synthfull.config import FullConfig, default_config
from synthfull.factors import observe_factors, sample_factors
from synthfull.rulegen import EXPERIENCE, FeatureView, FullRule, RandomRuleGenerator
from synthfull.sequences import SEQ_STAT_INDEX, sample_sequences, seq_stats

# Regime drift modes applied to a mutated rule's weight (same as CLAB-lite).
_DRIFT_MODES = ("decay", "boost", "flip")
_DRIFT_PROBS = (0.5, 0.3, 0.2)


@dataclass(frozen=True)
class WeightMutation:
    rule_id: str
    mode: str  # "decay" | "boost" | "flip"
    old_weight: float
    new_weight: float


@dataclass(frozen=True)
class RegimeEvent:
    """One regime switch, recorded for the regime history."""

    episode: int
    regime_id: int
    regime_tag: str
    mutations: tuple[WeightMutation, ...]


@dataclass
class CaseBook:
    """Everything visible at DECISION time. Contains no outcome fields."""

    case_ids: np.ndarray  # [N] int64, global sequential
    episode: np.ndarray  # [N] int32
    regime_id: np.ndarray  # [N] int32
    regime_tag: np.ndarray  # [N] str, e.g. "R03"
    observables: np.ndarray  # [N, 8] float64, noisy factor proxies
    observable_names: tuple[str, ...]
    device_id: np.ndarray  # [N] int32, value index into the Zipf pool
    phone_prefix: np.ndarray  # [N] int32
    region: np.ndarray  # [N] int32
    seq_events: np.ndarray  # [N, MAX_SEQ_LEN] int8, -1 = padding
    seq_durations: np.ndarray  # [N, MAX_SEQ_LEN] float32, 0 = padding
    seq_len: np.ndarray  # [N] int32

    def categorical(self, name: str) -> np.ndarray:
        return getattr(self, name)


@dataclass
class OutcomeLedger:
    """Labels + maturity schedule. Post-loan information, kept separate."""

    case_ids: np.ndarray  # [N] int64, aligned with CaseBook
    outcome: np.ndarray  # [N] int8, 1 = bad (default), 0 = good
    delay: np.ndarray  # [N] int8, in [delay_min, delay_max]
    visible_episode: np.ndarray  # [N] int32 = episode + delay

    def visible_mask(self, episode: int) -> np.ndarray:
        """Cases whose outcome is visible at (the end of) `episode`."""
        return self.visible_episode <= episode


@dataclass
class GroundTruth:
    """Latent truth for evaluation/tests only — never decision-time input."""

    factors: np.ndarray  # [N, 8] latent factor values
    factor_names: tuple[str, ...]
    rule_fired: np.ndarray  # [N, R] bool, under the ACTIVE weights' conditions
    rule_ids: tuple[str, ...]
    seq_mode: np.ndarray  # [N] int8, latent behavior mode (NOT a feature)
    mode_names: tuple[str, ...]


@dataclass
class WorldData:
    casebook: CaseBook
    ledger: OutcomeLedger
    truth: GroundTruth
    regimes: tuple[RegimeEvent, ...]  # switch history (empty = never switched)
    config: FullConfig

    def matured_view(self, episode: int) -> tuple[dict[str, np.ndarray], np.ndarray]:
        """Time-safe join: (feature dict, labels) for outcomes visible by
        `episode` only. Same red-line as CLAB-lite: this is the ONLY
        supported way to pair features with labels."""
        m = self.ledger.visible_mask(episode)
        cb = self.casebook
        feats = {
            "observables": cb.observables[m],
            "device_id": cb.device_id[m],
            "phone_prefix": cb.phone_prefix[m],
            "region": cb.region[m],
            "seq_events": cb.seq_events[m],
            "seq_durations": cb.seq_durations[m],
            "seq_len": cb.seq_len[m],
        }
        return feats, self.ledger.outcome[m]

    def feature_view(self) -> FeatureView:
        """FeatureView over ALL cases (fires rules offline; no labels)."""
        cb = self.casebook
        return FeatureView(
            observables=cb.observables,
            observable_index={n: i for i, n in enumerate(cb.observable_names)},
            categories={s.name: cb.categorical(s.name)
                        for s in self.config.categoricals},
            stats=seq_stats(cb.seq_events, cb.seq_durations, cb.seq_len),
            stat_index=SEQ_STAT_INDEX,
        )


class FullWorld:
    """Generative world driven by a single seeded RNG (fixed draw order)."""

    def __init__(self, config: FullConfig | None = None) -> None:
        self.config = config or default_config()
        self.rules: tuple[FullRule, ...] = RandomRuleGenerator(self.config).generate()
        self.rule_ids = tuple(r.rule_id for r in self.rules)
        self._weights = np.array([r.weight for r in self.rules])
        self.rng = np.random.Generator(np.random.PCG64(self.config.seed))
        self._samplers = {s.name: CategoricalSampler(s)
                          for s in self.config.categoricals}

    # -- public views ------------------------------------------------------

    @property
    def experience_rules(self) -> tuple[FullRule, ...]:
        """The disclosed pool. Held-out rules are unreachable from here."""
        return tuple(r for r in self.rules if r.pool == EXPERIENCE)

    @property
    def current_weights(self) -> np.ndarray:
        """Active rule weights of the current regime (after any drift)."""
        return self._weights.copy()

    # -- generation ---------------------------------------------------------

    def run(self, episodes: int, per_episode: int) -> WorldData:
        cols: dict[str, list[np.ndarray]] = {
            k: [] for k in (
                "episode", "regime_id", "factors", "observables", "device_id",
                "phone_prefix", "region", "seq_events", "seq_durations",
                "seq_len", "seq_mode", "fired", "outcome", "delay")
        }
        events: list[RegimeEvent] = []
        regime_id = 0
        for ep in range(episodes):
            if ep > 0 and self.rng.random() < self.config.switch_prob:
                regime_id += 1
                events.append(self._switch_regime(ep, regime_id))
            batch = self._gen_episode(per_episode)
            cols["episode"].append(np.full(per_episode, ep, dtype=np.int32))
            cols["regime_id"].append(np.full(per_episode, regime_id, dtype=np.int32))
            for k, v in batch.items():
                cols[k].append(v)
        cat = {k: np.concatenate(v) for k, v in cols.items()}
        n = episodes * per_episode
        case_ids = np.arange(n, dtype=np.int64)
        cfg = self.config
        casebook = CaseBook(
            case_ids=case_ids, episode=cat["episode"], regime_id=cat["regime_id"],
            regime_tag=np.array([f"R{r:02d}" for r in cat["regime_id"]]),
            observables=cat["observables"],
            observable_names=tuple(f.observable for f in cfg.factors),
            device_id=cat["device_id"], phone_prefix=cat["phone_prefix"],
            region=cat["region"], seq_events=cat["seq_events"],
            seq_durations=cat["seq_durations"], seq_len=cat["seq_len"],
        )
        ledger = OutcomeLedger(
            case_ids=case_ids, outcome=cat["outcome"], delay=cat["delay"],
            visible_episode=cat["episode"] + cat["delay"].astype(np.int32),
        )
        truth = GroundTruth(
            factors=cat["factors"], factor_names=tuple(f.name for f in cfg.factors),
            rule_fired=cat["fired"], rule_ids=self.rule_ids,
            seq_mode=cat["seq_mode"],
            mode_names=tuple(m.name for m in cfg.modes),
        )
        return WorldData(casebook, ledger, truth, tuple(events), cfg)

    # -- internals (fixed RNG draw order, do not reorder) -------------------

    def _switch_regime(self, episode: int, regime_id: int) -> RegimeEvent:
        mutations: list[WeightMutation] = []
        for i, rule in enumerate(self.rules):
            if self.rng.random() >= self.config.drift_rule_fraction:
                continue
            mode = _DRIFT_MODES[self.rng.choice(3, p=_DRIFT_PROBS)]
            old = float(self._weights[i])
            if mode == "decay":
                new = old * self.rng.uniform(0.3, 0.7)
            elif mode == "boost":
                new = old * self.rng.uniform(1.3, 1.8)
            else:  # flip: direction reversal, shrunk magnitude
                new = -old * self.rng.uniform(0.5, 1.0)
            self._weights[i] = new
            mutations.append(WeightMutation(rule.rule_id, mode, old, new))
        return RegimeEvent(episode, regime_id, f"R{regime_id:02d}", tuple(mutations))

    def _gen_episode(self, n: int) -> dict[str, np.ndarray]:
        cfg = self.config
        factors = sample_factors(self.rng, cfg.factors, n)
        observables = observe_factors(self.rng, cfg.factors, factors)
        cats = {name: s.sample(self.rng, n) for name, s in self._samplers.items()}
        ev, dur, lengths, mode = sample_sequences(self.rng, n, cfg.modes)
        stats = seq_stats(ev, dur, lengths)
        fv = FeatureView(
            observables=observables,
            observable_index={f.observable: i for i, f in enumerate(cfg.factors)},
            categories=cats, stats=stats, stat_index=SEQ_STAT_INDEX,
        )
        fired = np.stack([r.fires(fv) for r in self.rules], axis=1)
        logit = cfg.base_logit + fired @ self._weights
        logit = logit + self.rng.normal(0.0, cfg.noise_std, n)
        p_bad = 1.0 / (1.0 + np.exp(-logit))
        outcome = (self.rng.random(n) < p_bad).astype(np.int8)
        delay = self.rng.integers(cfg.delay_min, cfg.delay_max + 1, n).astype(np.int8)
        return {
            "factors": factors, "observables": observables,
            "device_id": cats["device_id"], "phone_prefix": cats["phone_prefix"],
            "region": cats["region"], "seq_events": ev, "seq_durations": dur,
            "seq_len": lengths, "seq_mode": mode, "fired": fired,
            "outcome": outcome, "delay": delay,
        }
