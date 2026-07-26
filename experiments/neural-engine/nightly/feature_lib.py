"""Shadow feature library (design doc §8.4, P1).

Trade-off note: the library is deliberately self-managed (a plain record
list) instead of reusing ``scoring.features.FeatureRegistry``. The registry
is the *active* feature computation path feeding the GBDT — registering a
shadow candidate there would make it computable in the decision path before
any promotion gate exists (promotion arrives post-P1). Shadow records here
carry the sandbox verdict, the quality score Q and full provenance; only a
future promotion gate may turn a record into a registry entry.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from verify import FAIL, PASS, QUARANTINE, Verdict

# verdict -> library status (§8.4: admitted features enter as shadow only)
_STATUS_BY_VERDICT = {PASS: "shadow", FAIL: "rejected", QUARANTINE: "quarantine"}


@dataclass(frozen=True)
class FeatureRecord:
    """One cloud-proposed feature candidate with its local verdict."""

    name: str
    expression: str
    rationale: str
    episode: int  # night on which it was proposed
    verdict: str  # PASS / FAIL / QUARANTINE
    quality: float  # verifier Q in [0,1]
    status: str  # shadow | rejected | quarantine
    provenance: dict[str, Any]  # cloud provenance (provider, prompt_hash, ...)


@dataclass
class FeatureLibrary:
    """Append-only candidate ledger; nothing here influences scoring."""

    _records: list[FeatureRecord] = field(default_factory=list)

    def add(
        self,
        *,
        name: str,
        expression: str,
        rationale: str,
        episode: int,
        verdict: Verdict,
        provenance: dict[str, Any],
    ) -> FeatureRecord:
        record = FeatureRecord(
            name=name,
            expression=expression,
            rationale=rationale,
            episode=episode,
            verdict=verdict.status,
            quality=verdict.quality,
            status=_STATUS_BY_VERDICT[verdict.status],
            provenance=dict(provenance),
        )
        self._records.append(record)
        return record

    @property
    def records(self) -> tuple[FeatureRecord, ...]:
        return tuple(self._records)

    def by_status(self, status: str) -> list[FeatureRecord]:
        return [r for r in self._records if r.status == status]
