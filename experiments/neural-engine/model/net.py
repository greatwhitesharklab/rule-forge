"""NeuralCreditNet: feature encoder + read gate + small MLP backbone (D3/D5).

The frozen memory table is materialized into dense per-head buffers
(proto / confidence / n / hit) so lookup is pure indexing: frozen by
construction, moves with ``.to(device)``, and snapshots into state_dict.

Read gate per head (D3):
    gate_k = σ(w · [confidence, log1p(n)/log1p(N_REF), freshness, cos(q, proto_k)]) · hit_k
Missing slots have all-zero stats and freshness, and the explicit hit mask
forces gate → 0, so an unaddressed pattern cannot leak into the decision.
Phase 1 simplification: freshness ≡ 1 for existing slots (offline build,
no time decay yet; decay belongs to the Phase 3 write gate).

memory_miss is flagged when every head's gate falls below MISS_THRESHOLD —
the miss is explicitly observable, never a silent guess (D3/D7).
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

from memory.table import MemoryTable

MISS_THRESHOLD = 0.1
N_REF = 200.0  # normalizer for the log-n gate feature
GATE_FEATURE_DIM = 4  # [confidence, log_n, freshness, query·proto similarity]


@dataclass
class FeatureSpec:
    cat_features: list[str]
    cat_vocab_sizes: list[int]
    num_features: list[str]
    embed_dim: int = 8

    @property
    def encoded_dim(self) -> int:
        return len(self.cat_features) * self.embed_dim + len(self.num_features)


@dataclass
class MemoryTrace:
    """Per-decision audit payload (D7). Tensors are detached CPU copies."""

    slot_ids: torch.Tensor  # [B, K]
    gates: torch.Tensor  # [B, K]
    confidences: torch.Tensor  # [B, K]
    ns: torch.Tensor  # [B, K]
    hits: torch.Tensor  # [B, K] bool
    memory_miss: torch.Tensor  # [B] bool
    prob: torch.Tensor  # [B] calibrated-or-raw bad probability


class NeuralCreditNet(nn.Module):
    def __init__(
        self,
        spec: FeatureSpec,
        table: MemoryTable,
        hidden_dim: int = 128,
        backbone_dim: int = 64,
    ) -> None:
        super().__init__()
        self.spec = spec
        self.head_names = list(table.head_names)
        self.proto_dim = table.proto_dim
        k = len(self.head_names)

        # Feature encoder: categorical embeddings + standardized numerics.
        self.cat_embeddings = nn.ModuleList(
            nn.Embedding(v, spec.embed_dim) for v in spec.cat_vocab_sizes
        )
        self.feature_proj = nn.Linear(spec.encoded_dim, backbone_dim)

        # Read gate: shared weight vector across heads, per-head scale on protos.
        self.query_proj = nn.Linear(backbone_dim, table.proto_dim)
        self.gate_weight = nn.Linear(GATE_FEATURE_DIM, 1)
        self.mem_proj = nn.Linear(k * table.proto_dim, backbone_dim)

        # Backbone: deliberately small MLP (D5), hidden ≤ 256.
        self.backbone = nn.Sequential(
            nn.Linear(backbone_dim * 2, hidden_dim),
            nn.ReLU(),
            nn.Dropout(0.1),
            nn.Linear(hidden_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, 1),
        )

        # Frozen memory as dense buffers (indexing = addressing).
        for ki, head in enumerate(self.head_names):
            size = table.num_slots[ki]
            proto = np.zeros((size, table.proto_dim), dtype=np.float32)
            conf = np.zeros(size, dtype=np.float32)
            cnt = np.zeros(size, dtype=np.float32)
            hit = np.zeros(size, dtype=np.float32)
            for sid, slot in table.slots[ki].items():
                proto[sid] = slot.proto
                conf[sid] = slot.confidence
                cnt[sid] = slot.n
                hit[sid] = 1.0
            prefix = f"mem_{head}_"
            self.register_buffer(prefix + "proto", torch.from_numpy(proto))
            self.register_buffer(prefix + "conf", torch.from_numpy(conf))
            self.register_buffer(prefix + "n", torch.from_numpy(cnt))
            self.register_buffer(prefix + "hit", torch.from_numpy(hit))

    def _head_buffer(self, head: str, kind: str) -> torch.Tensor:
        return getattr(self, f"mem_{head}_{kind}")

    def encode_features(
        self, cat_codes: torch.Tensor, num_vals: torch.Tensor
    ) -> torch.Tensor:
        parts = [emb(cat_codes[:, i]) for i, emb in enumerate(self.cat_embeddings)]
        parts.append(num_vals)
        return F.relu(self.feature_proj(torch.cat(parts, dim=-1)))

    def forward(
        self,
        cat_codes: torch.Tensor,  # [B, C] long
        num_vals: torch.Tensor,  # [B, N] float (standardized)
        slot_ids: torch.Tensor,  # [B, K] long
    ) -> tuple[torch.Tensor, MemoryTrace]:
        f = self.encode_features(cat_codes, num_vals)  # [B, backbone_dim]
        q = self.query_proj(f)  # [B, proto_dim]

        gates, confs, ns_list, hits = [], [], [], []
        gated_protos = []
        for ki, head in enumerate(self.head_names):
            sid = slot_ids[:, ki]
            proto = self._head_buffer(head, "proto")[sid]  # [B, D]
            conf = self._head_buffer(head, "conf")[sid]
            cnt = self._head_buffer(head, "n")[sid]
            hit = self._head_buffer(head, "hit")[sid]

            sim = F.cosine_similarity(q, proto, dim=-1) * hit
            freshness = hit  # ≡ 1.0 for existing slots, 0 for misses (Phase 1)
            log_n = torch.log1p(cnt) / np.log1p(N_REF)
            gate_in = torch.stack([conf, log_n, freshness, sim], dim=-1)
            gate = torch.sigmoid(self.gate_weight(gate_in)).squeeze(-1) * hit

            gates.append(gate)
            confs.append(conf)
            ns_list.append(cnt)
            hits.append(hit)
            gated_protos.append(gate.unsqueeze(-1) * proto)

        gates_t = torch.stack(gates, dim=-1)  # [B, K]
        mem = self.mem_proj(torch.cat(gated_protos, dim=-1))  # [B, backbone_dim]
        logit = self.backbone(torch.cat([f, mem], dim=-1)).squeeze(-1)  # [B]

        memory_miss = (gates_t.max(dim=-1).values < MISS_THRESHOLD) | (
            torch.stack(hits, dim=-1).sum(dim=-1) == 0
        )
        trace = MemoryTrace(
            slot_ids=slot_ids.detach().cpu(),
            gates=gates_t.detach().cpu(),
            confidences=torch.stack(confs, dim=-1).detach().cpu(),
            ns=torch.stack(ns_list, dim=-1).detach().cpu(),
            hits=torch.stack(hits, dim=-1).detach().cpu().bool(),
            memory_miss=memory_miss.detach().cpu(),
            prob=torch.sigmoid(logit).detach().cpu(),
        )
        return logit, trace
