"""Engram gated memory injection, welded into a shallow LLM layer (§1.2 P2).

Structure (Engram-style residual injection + our zero-init methodology):

    target layer output h [B,T,hidden]
      └─ pool per sample (last non-pad token, or mean) ── query source
      └─ query_proj: Linear(hidden -> query_dim), RANDOM + FROZEN
           Retrieval is non-differentiable (hard FAISS lookup + soft gate
           weights), so the query path carries no gradient; a straight-through
           estimator is the P3 upgrade path if we ever want to learn queries.
      └─ MemoryReader.read(query, top_k) per batch sample
           -> hits {value_vec[hidden], w_i, key_vec[query_dim]}
           (production: SlotService via SlotReader; w_i = a_sem*a_tmp**GAMMA)
      └─ mem = Σ w_i · value_vec_i          [hidden]
      └─ gate = sigmoid(Linear([Σw, max w, mean cos(query, key)]))  scalar
           Feature rationale: Σw = total memory pressure, max w = strongest
           single opinion, mean cos = query/key-space agreement the weight
           formula cannot see (a_tmp is invisible to geometry). Zero-init ->
           sigmoid(0)=0.5, a neutral gate; real gating is learned in P3.
      └─ mem_out: Linear(hidden -> hidden), weight+bias ZERO-INITIALIZED
      └─ h' = h + mem_out(gate · mem)  — broadcast over all token positions

Broadcast trade-off: one pooled retrieval per *sequence*, added as a uniform
residual bias at every position. Per-token queries would multiply retrieval
cost by T and the slot table stores case-level experience, not token-level
facts — sequence-level injection is the intended granularity.

Zero-init contract (P2 acceptance): at init, mem_out == 0, so the injection
is EXACTLY zero and welded logits are bitwise identical to baseline — even
when the reader returns non-trivial hits. A memory miss also injects exactly
zero (delta row stays zero, gate recorded as 0.0).

Decoupled from LocalLLM: accepts any HF-style causal LM exposing
`.model.layers` (Qwen3/Llama family) plus a layer index. Hook attach/detach
is symmetric; usable as a context manager.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Any, Protocol

import numpy as np
import torch
import torch.nn.functional as F
from torch import nn

if TYPE_CHECKING:
    from slots import SlotService


@dataclass(frozen=True)
class MemoryHit:
    """One retrieved slot, in injection-ready form."""

    value_vec: np.ndarray  # float32 [hidden_dim] slot content vector
    weight: float  # w_i = a_sem * a_tmp**GAMMA (slots two-way gate)
    key_vec: np.ndarray  # float32 [query_dim] slot semantic key (gate feature)


class MemoryReader(Protocol):
    """Retrieval backend seam. Production = SlotReader(SlotService)."""

    def read(self, query: np.ndarray, k: int) -> list[MemoryHit]: ...


class SlotReader:
    """Production adapter: SlotService.retrieve() -> MemoryHit list.

    Attribution logging stays inside SlotService.retrieve (case_id is fixed
    per reader), so credit_assignment keeps blaming the slots the LLM read.
    """

    def __init__(self, service: "SlotService", case_id: str = "llm-injection"):
        self.service = service
        self.case_id = case_id

    def read(self, query: np.ndarray, k: int) -> list[MemoryHit]:
        return [
            MemoryHit(
                value_vec=np.asarray(slot.value_vec, dtype=np.float32),
                weight=float(w),
                key_vec=np.asarray(slot.key_vec, dtype=np.float32),
            )
            for slot, w in self.service.retrieve(query, case_id=self.case_id, k=k)
        ]


def _resolve_layers(model: nn.Module) -> nn.ModuleList:
    inner = getattr(model, "model", model)
    layers = getattr(inner, "layers", None)
    if not isinstance(layers, nn.ModuleList):
        raise ValueError(
            "cannot locate decoder layers: expected model.model.layers "
            "(HF Qwen3/Llama-style); pass a compatible nn.Module"
        )
    return layers


class MemoryInjection(nn.Module):
    """Gated Engram injection welded onto `model`'s decoder layer `layer_idx`.

    Trainable surface: mem_out + gate. query_proj is frozen by design (see
    module docstring). Diagnostics per forward: last_hits / last_gates (one
    entry per batch sample) — the P2 acceptance watches the gate (α)
    distribution through these.
    """

    def __init__(
        self,
        model: nn.Module,
        reader: MemoryReader,
        layer_idx: int = 2,
        top_k: int = 8,
        query_dim: int = 256,
        pooling: str = "last",
    ):
        super().__init__()
        if pooling not in ("last", "mean"):
            raise ValueError(f"pooling must be 'last' or 'mean', got {pooling!r}")
        hidden = int(model.config.hidden_size)
        self.hidden_dim = hidden
        self.query_dim = query_dim
        self.top_k = top_k
        self.pooling = pooling
        self.layer_idx = layer_idx
        self.reader = reader

        self.query_proj = nn.Linear(hidden, query_dim, bias=False)
        self.query_proj.requires_grad_(False)  # frozen random projection
        self.mem_out = nn.Linear(hidden, hidden)
        nn.init.zeros_(self.mem_out.weight)  # zero-init contract (acceptance)
        nn.init.zeros_(self.mem_out.bias)
        self.gate = nn.Linear(3, 1)  # [sum_w, max_w, mean_cos] -> logit
        nn.init.zeros_(self.gate.weight)
        nn.init.zeros_(self.gate.bias)

        self.last_hits: list[int] = []
        self.last_gates: list[float] = []

        self._layers = _resolve_layers(model)
        if not 0 <= layer_idx < len(self._layers):
            raise IndexError(
                f"layer_idx {layer_idx} out of range ({len(self._layers)} layers)"
            )
        self._handle: Any | None = None
        self.attach()

    # ------------------------------------------------------------- lifecycle

    def attach(self) -> "MemoryInjection":
        if self._handle is None:
            self._handle = self._layers[self.layer_idx].register_forward_hook(
                self._hook, with_kwargs=True
            )
        return self

    def detach(self) -> "MemoryInjection":
        if self._handle is not None:
            self._handle.remove()
            self._handle = None
        return self

    def __enter__(self) -> "MemoryInjection":
        return self.attach()

    def __exit__(self, *exc: Any) -> None:
        self.detach()

    # --------------------------------------------------------------- forward

    def _pool(self, h: torch.Tensor, mask: torch.Tensor | None) -> torch.Tensor:
        """h [T, hidden] -> pooled [hidden]; mask [T] with 1 = real token."""
        if self.pooling == "last":
            idx = int(mask.sum().item()) - 1 if mask is not None else h.shape[0] - 1
            return h[idx]
        if mask is not None:  # mean over real tokens only
            m = mask.to(h.dtype)[:, None]
            return (h * m).sum(0) / m.sum().clamp(min=1.0)
        return h.mean(0)

    def _read_memory(
        self, pooled: torch.Tensor, dtype: torch.dtype, device: torch.device
    ) -> tuple[torch.Tensor, float, int] | None:
        """Retrieve + gate one sample. None = memory miss (inject zero)."""
        with torch.no_grad():  # retrieval path is non-differentiable by design
            q = self.query_proj(pooled.detach())
        hits = self.reader.read(q.cpu().float().numpy(), self.top_k)
        if not hits:
            return None
        values = torch.as_tensor(
            np.stack([h.value_vec for h in hits]), dtype=dtype, device=device
        )
        weights = torch.as_tensor(
            [h.weight for h in hits], dtype=dtype, device=device
        )
        keys = torch.as_tensor(
            np.stack([h.key_vec for h in hits]), dtype=q.dtype, device=q.device
        )
        mem = (weights[:, None] * values).sum(0)  # Σ w_i · value_vec_i
        with torch.no_grad():
            mean_cos = F.cosine_similarity(q[None, :], keys, dim=-1).mean()
            feats = torch.stack(
                [weights.sum(), weights.max(), mean_cos.to(dtype)]
            )
        g = torch.sigmoid(self.gate(feats))  # scalar, gate weights trainable
        return self.mem_out(g * mem), float(g.detach()), len(hits)

    def _hook(
        self, module: nn.Module, args: tuple, kwargs: dict, output: Any
    ) -> Any:
        if isinstance(output, tuple):
            hidden, rest = output[0], output[1:]
        else:
            hidden, rest = output, None
        mask = kwargs.get("attention_mask")  # None when model masks internally
        deltas: list[torch.Tensor] = []
        self.last_hits = []
        self.last_gates = []
        for b in range(hidden.shape[0]):
            pooled = self._pool(hidden[b], None if mask is None else mask[b])
            result = self._read_memory(pooled, hidden.dtype, hidden.device)
            if result is None:
                deltas.append(
                    torch.zeros(
                        self.hidden_dim, dtype=hidden.dtype, device=hidden.device
                    )
                )
                self.last_hits.append(0)
                self.last_gates.append(0.0)
            else:
                delta, g, n_hits = result
                deltas.append(delta)
                self.last_hits.append(n_hits)
                self.last_gates.append(g)
        new_hidden = hidden + torch.stack(deltas).unsqueeze(1)  # [B,1,H] broadcast
        return (new_hidden, *rest) if rest is not None else new_hidden
