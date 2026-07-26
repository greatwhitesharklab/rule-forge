"""SyntheticWorld: the CLAB-lite generative world (design doc §5 P0).

Generates a reproducible, drifting synthetic credit world:

  * per episode, a batch of applications is sampled from 8 latent factors,
    mapped to 6 concepts, scored with base risk + active rule weights, and
    assigned a good/bad outcome;
  * each episode switches regime with probability `switch_prob`
    (gap ~ Geometric(p), expected 10 episodes); a switch mutates a subset of
    rule weights and is recorded in the regime history;
  * outcomes mature 1~3 episodes later (post-loan seasoning).

Time red-line is structural: decision-time information (CaseBook) and
outcome labels (OutcomeLedger) are separate data structures; the only join
helper, `matured_view`, filters by visibility episode, so a decision at
episode t can never see an unmatured label.

Reproducibility: every draw comes from ONE seeded numpy Generator consumed
in a fixed order (per episode: regime switch, drift, factors, observables,
noise, outcome, delay). Same seed -> bit-identical output.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from synth.config import OBSERVABLE_TRANSFORMS, WorldConfig, default_config
from synth.rules import EXPERIENCE, Rule, build_rule_pool

# Regime drift modes applied to a mutated rule's weight (design doc §5:
# "部分概念-结局映射强度/方向变化").
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
    """One regime switch, recorded for the slot regime_tag history (§1.2)."""

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
    concepts: np.ndarray  # [N, 6] concept values
    concept_names: tuple[str, ...]
    rule_fired: np.ndarray  # [N, R] bool, under the ACTIVE weights' conditions
    rule_ids: tuple[str, ...]


@dataclass
class WorldData:
    casebook: CaseBook
    ledger: OutcomeLedger
    truth: GroundTruth
    regimes: tuple[RegimeEvent, ...]  # switch history (empty = never switched)
    config: WorldConfig

    def matured_view(self, episode: int) -> tuple[np.ndarray, np.ndarray]:
        """Time-safe join: (observables, labels) for outcomes visible by
        `episode` only. This is the ONLY supported way to pair features with
        labels, which enforces the time red-line."""
        m = self.ledger.visible_mask(episode)
        return self.casebook.observables[m], self.ledger.outcome[m]


class SyntheticWorld:
    """Generative world driven by a single seeded RNG (fixed draw order)."""

    def __init__(self, config: WorldConfig | None = None) -> None:
        self.config = config or default_config()
        self.rng = np.random.Generator(np.random.PCG64(self.config.seed))
        self.rules: tuple[Rule, ...] = build_rule_pool()
        self.rule_ids = tuple(r.rule_id for r in self.rules)
        self._weights = np.array([r.weight for r in self.rules])
        self._factor_index = {f.name: i for i, f in enumerate(self.config.factors)}
        self._concept_index = {c.name: i for i, c in enumerate(self.config.concepts)}

    # -- public views ------------------------------------------------------

    @property
    def experience_rules(self) -> tuple[Rule, ...]:
        """The disclosed pool. Held-out rules are unreachable from here."""
        return tuple(r for r in self.rules if r.pool == EXPERIENCE)

    @property
    def current_weights(self) -> np.ndarray:
        """Active rule weights of the current regime (after any drift)."""
        return self._weights.copy()

    # -- generation ---------------------------------------------------------

    def run(self, episodes: int, per_episode: int) -> WorldData:
        cols: dict[str, list[np.ndarray]] = {
            k: [] for k in ("episode", "regime_id", "factors", "observables",
                            "concepts", "fired", "outcome", "delay")
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
        )
        ledger = OutcomeLedger(
            case_ids=case_ids, outcome=cat["outcome"], delay=cat["delay"],
            visible_episode=cat["episode"] + cat["delay"].astype(np.int32),
        )
        truth = GroundTruth(
            factors=cat["factors"], factor_names=tuple(f.name for f in cfg.factors),
            concepts=cat["concepts"], concept_names=tuple(c.name for c in cfg.concepts),
            rule_fired=cat["fired"], rule_ids=self.rule_ids,
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
        factors = np.stack([self._sample_factor(f, n) for f in cfg.factors], axis=1)
        z = (factors - np.array([f.loc for f in cfg.factors])) / np.array(
            [f.scale for f in cfg.factors])
        concepts = self._concepts(z)
        fired = np.stack([r.fires(concepts, self._concept_index) for r in self.rules],
                         axis=1)
        logit = cfg.base_logit + fired @ self._weights
        for name, coef in cfg.concept_logit_coef:
            logit = logit + coef * (concepts[:, self._concept_index[name]] - 0.5)
        logit = logit + self.rng.normal(0.0, cfg.noise_std, n)
        p_bad = 1.0 / (1.0 + np.exp(-logit))
        outcome = (self.rng.random(n) < p_bad).astype(np.int8)
        delay = self.rng.integers(cfg.delay_min, cfg.delay_max + 1, n).astype(np.int8)
        observables = np.stack(
            [self._observe(f, factors[:, i], n) for i, f in enumerate(cfg.factors)],
            axis=1)
        return {"factors": factors, "observables": observables, "concepts": concepts,
                "fired": fired, "outcome": outcome, "delay": delay}

    def _sample_factor(self, spec: object, n: int) -> np.ndarray:
        p = spec.params  # type: ignore[attr-defined]
        dist = spec.dist  # type: ignore[attr-defined]
        if dist == "beta":
            return self.rng.beta(p[0], p[1], n)
        if dist == "gamma":
            return self.rng.gamma(p[0], p[1], n)
        if dist == "poisson":
            return self.rng.poisson(p[0], n).astype(np.float64)
        if dist == "lognormal":
            return self.rng.lognormal(p[0], p[1], n)
        raise ValueError(f"unknown dist: {dist}")

    def _concepts(self, z: np.ndarray) -> np.ndarray:
        out = np.empty((z.shape[0], len(self.config.concepts)))
        for j, spec in enumerate(self.config.concepts):
            s = np.full(z.shape[0], spec.intercept)
            for fname, coef in spec.terms:
                s = s + coef * z[:, self._factor_index[fname]]
            out[:, j] = 1.0 / (1.0 + np.exp(-s))
        return out

    def _observe(self, spec: object, x: np.ndarray, n: int) -> np.ndarray:
        name = spec.observable  # type: ignore[attr-defined]
        if name in OBSERVABLE_TRANSFORMS:
            kind, mult = OBSERVABLE_TRANSFORMS[name]
            x = (1.0 - x) if kind == "reverse" else x * mult
        model, param = spec.obs_model, spec.obs_param  # type: ignore[attr-defined]
        if model == "add":
            y = x + self.rng.normal(0.0, param, n)
        elif model == "mul":
            y = x * np.exp(self.rng.normal(0.0, param, n))
        elif model == "thin":
            y = self.rng.binomial(x.astype(np.int64), param).astype(np.float64)
        else:
            raise ValueError(f"unknown obs_model: {model}")
        clip = spec.obs_clip  # type: ignore[attr-defined]
        return np.clip(y, *clip) if clip else y
