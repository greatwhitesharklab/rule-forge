"""Champion-challenger promotion gate for LoRA generations (design §1.4 晋升,
§5 P2 验收 NT<5%).

Weekly (or per-generation) review: the challenger (newly trained adapter) and
the champion (currently mounted adapter, None = raw base) are both scored on
a replay set — mean per-token NLL of the completion, teacher-forced. Three
doors, all must pass to promote:

1. replay improvement: (champion - challenger) / champion > min_rel_improvement
   (default 1%) — no significant win, no swap;
2. old-regime retention (NT): the old-knowledge probe subset (fixed "old
   regime" samples carved out of the replay set) must not regress by more
   than max_old_regression (default 5%) — new knowledge may not evict old;
3. (implicit) the champion baseline is whatever is mounted, so promotion is
   always relative to production, never to a stale reference.

Rejected challengers keep their directory (audit trail) with a
gate_decision.json marker; every verdict is appended to promotion_log.jsonl.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Sequence

import torch
import torch.nn.functional as F

from .distill import DistillPair
from .mount import mount_adapter, unmount_adapter
from .train import encode_pair

NllFn = Callable[[str | None, Sequence[DistillPair], str], float]


@dataclass(frozen=True)
class GateThresholds:
    min_rel_improvement: float = 0.01  # door 1: replay NLL relative drop
    max_old_regression: float = 0.05   # door 2 (NT): old-subset NLL relative rise


@dataclass(frozen=True)
class GateVerdict:
    promoted: bool
    reason: str
    champion: str | None
    challenger: str
    replay_champion_nll: float
    replay_challenger_nll: float
    old_champion_nll: float
    old_challenger_nll: float
    ts: str


def old_regime_probe(
    pairs: Sequence[DistillPair],
    old_regimes: set[str] | None = None,
    fraction: float = 0.2,
) -> list[DistillPair]:
    """Carve the fixed old-knowledge probe subset out of the replay set.

    Pairs tagged with an old regime label are preferred; when nothing is
    tagged (or no regime set is given) a deterministic every-k-th stride
    keeps the probe stable across nights.
    """
    if old_regimes:
        tagged = [p for p in pairs if p.regime in old_regimes]
        if tagged:
            return tagged
    k = max(1, round(1 / max(fraction, 1e-9)))
    return [p for i, p in enumerate(pairs) if i % k == 0]


@torch.no_grad()
def mean_completion_nll(
    model: Any,
    tokenizer: Any,
    pairs: Sequence[DistillPair],
    max_len: int = 512,
    batch_size: int = 4,
) -> float:
    """Mean per-token NLL over completion tokens only (prompt masked out)."""
    model.eval()
    total_nll, total_tok = 0.0, 0
    examples = [encode_pair(tokenizer, p.prompt, p.completion, max_len) for p in pairs]
    for i in range(0, len(examples), batch_size):
        chunk = examples[i:i + batch_size]
        width = max(len(e["input_ids"]) for e in chunk)
        ids = torch.tensor([e["input_ids"] + [0] * (width - len(e["input_ids"]))
                            for e in chunk], dtype=torch.long)
        labels = torch.tensor([e["labels"] + [-100] * (width - len(e["labels"]))
                               for e in chunk], dtype=torch.long)
        attn = torch.tensor([[1] * len(e["input_ids"]) + [0] * (width - len(e["input_ids"]))
                             for e in chunk], dtype=torch.long)
        logits = model(input_ids=ids, attention_mask=attn).logits
        # teacher forcing: position t predicts token t+1
        shift_logits = logits[:, :-1]
        shift_labels = labels[:, 1:]
        mask = shift_labels != -100
        safe = shift_labels.clamp_min(0)
        logp = F.log_softmax(shift_logits.float(), dim=-1)
        nll = -logp.gather(-1, safe.unsqueeze(-1)).squeeze(-1)
        total_nll += float((nll * mask).sum())
        total_tok += int(mask.sum())
    return total_nll / max(total_tok, 1)


class PromotionGate:
    """Scores champion vs challenger on the replay set and rules promotion."""

    def __init__(
        self,
        model: Any,
        tokenizer: Any,
        log_path: Path | str,
        thresholds: GateThresholds = GateThresholds(),
        nll_fn: NllFn | None = None,
        old_regimes: set[str] | None = None,
        max_len: int = 512,
    ):
        self.model = model
        self.tokenizer = tokenizer
        self.log_path = Path(log_path)
        self.thresholds = thresholds
        self.nll_fn = nll_fn  # injectable evaluator (deterministic gate tests)
        self.old_regimes = old_regimes
        self.max_len = max_len

    def _eval(self, adapter_dir: str | None, pairs: Sequence[DistillPair], tag: str) -> float:
        if self.nll_fn is not None:
            return self.nll_fn(adapter_dir, pairs, tag)
        if adapter_dir is None:
            return mean_completion_nll(self.model, self.tokenizer, pairs, self.max_len)
        mounted = mount_adapter(self.model, adapter_dir)
        try:
            return mean_completion_nll(mounted, self.tokenizer, pairs, self.max_len)
        finally:
            unmount_adapter(mounted)

    def judge(
        self,
        champion_dir: str | None,
        challenger_dir: str,
        replay_pairs: Sequence[DistillPair],
        old_pairs: Sequence[DistillPair] | None = None,
    ) -> GateVerdict:
        if old_pairs is None:
            old_pairs = old_regime_probe(replay_pairs, self.old_regimes)

        champ_replay = self._eval(champion_dir, replay_pairs, "replay")
        chall_replay = self._eval(challenger_dir, replay_pairs, "replay")
        champ_old = self._eval(champion_dir, old_pairs, "old")
        chall_old = self._eval(challenger_dir, old_pairs, "old")

        rel_imp = (champ_replay - chall_replay) / max(champ_replay, 1e-9)
        old_regr = (chall_old - champ_old) / max(champ_old, 1e-9)
        if rel_imp <= self.thresholds.min_rel_improvement:
            promoted = False
            reason = (f"insufficient replay improvement: {rel_imp:+.2%} "
                      f"<= {self.thresholds.min_rel_improvement:+.2%}")
        elif old_regr > self.thresholds.max_old_regression:
            promoted = False
            reason = (f"old-regime regression: {old_regr:+.2%} "
                      f"> {self.thresholds.max_old_regression:+.2%}")
        else:
            promoted = True
            reason = (f"promoted: replay {rel_imp:+.2%}, old {old_regr:+.2%}")

        verdict = GateVerdict(
            promoted=promoted, reason=reason,
            champion=champion_dir, challenger=challenger_dir,
            replay_champion_nll=champ_replay, replay_challenger_nll=chall_replay,
            old_champion_nll=champ_old, old_challenger_nll=chall_old,
            ts=datetime.now(timezone.utc).isoformat(),
        )
        self._record(verdict)
        return verdict

    def _record(self, v: GateVerdict) -> None:
        rec = {**asdict(v), "verdict": "promoted" if v.promoted else "rejected"}
        self.log_path.parent.mkdir(parents=True, exist_ok=True)
        with self.log_path.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
        # the challenger directory stays either way (audit); mark the ruling
        decision = {"verdict": rec["verdict"], "reason": v.reason, "ts": v.ts}
        (Path(v.challenger) / "gate_decision.json").write_text(
            json.dumps(decision, indent=2, ensure_ascii=False)
        )
